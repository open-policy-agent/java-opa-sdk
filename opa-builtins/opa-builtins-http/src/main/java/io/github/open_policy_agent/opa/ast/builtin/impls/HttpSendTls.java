package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * The {@code tls_*} parameters of an {@code http.send} request and the {@link SSLContext} they
 * describe.
 *
 * <p>Every certificate or key can be given inline, as a file path, or as the name of an environment
 * variable, like in OPA. Errors are worded like OPA's where the JDK gives enough to do so.
 */
final class HttpSendTls {

  private static final Pattern PEM_BLOCK =
      Pattern.compile("-----BEGIN ([A-Z0-9 ]+)-----([^-]*)-----END \\1-----");

  boolean useSystemCerts;
  boolean insecureSkipVerify;

  String caCert = "";
  String caCertFile = "";
  String caCertEnv = "";
  String clientCert = "";
  String clientCertFile = "";
  String clientCertEnv = "";
  String clientKey = "";
  String clientKeyFile = "";
  String clientKeyEnv = "";

  /** Whether any parameter asks for something other than the JDK's default TLS behaviour. */
  boolean isCustom() {
    return insecureSkipVerify
        || !caCert.isEmpty()
        || !caCertFile.isEmpty()
        || !caCertEnv.isEmpty()
        || hasInlineClientPair()
        || hasFileClientPair()
        || hasEnvClientPair();
  }

  private boolean hasInlineClientPair() {
    return !clientCert.isEmpty() && !clientKey.isEmpty();
  }

  private boolean hasFileClientPair() {
    return !clientCertFile.isEmpty() && !clientKeyFile.isEmpty();
  }

  private boolean hasEnvClientPair() {
    return !clientCertEnv.isEmpty() && !clientKeyEnv.isEmpty();
  }

  /**
   * Reads every referenced file and environment variable and parses the PEM data. This runs on each
   * request, as in OPA, so rotated files are picked up; what it returns is what a client depends
   * on, which lets equal material share one HTTP client.
   */
  Material resolve() {
    try {
      List<Credential> credentials = new ArrayList<>();
      if (hasInlineClientPair()) {
        credentials.add(keyPair(clientCert.getBytes(StandardCharsets.UTF_8), clientKey.getBytes(StandardCharsets.UTF_8)));
      }
      if (hasFileClientPair()) {
        credentials.add(keyPair(readFile(clientCertFile), readFile(clientKeyFile)));
      }
      if (hasEnvClientPair()) {
        try {
          credentials.add(
              keyPair(
                  env(clientCertEnv).getBytes(StandardCharsets.UTF_8),
                  env(clientKeyEnv).getBytes(StandardCharsets.UTF_8)));
        } catch (HttpSendFailure e) {
          throw HttpSendFailure.internal(
              "cannot extract public/private key pair from envvars \""
                  + clientCertEnv
                  + "\", \""
                  + clientKeyEnv
                  + "\": "
                  + e.getMessage());
        }
      }

      List<X509Certificate> roots = new ArrayList<>();
      if (!caCert.isEmpty()) {
        // OPA accepts the PEM with escaped newlines, which is how it often arrives in a policy.
        roots.addAll(caCerts(caCert.replace("\\n", "\n").getBytes(StandardCharsets.UTF_8), "could not append certificates"));
      }
      if (!caCertFile.isEmpty()) {
        roots.addAll(caCerts(readFile(caCertFile), "could not append CA certificates from \"" + caCertFile + "\""));
      }
      if (!caCertEnv.isEmpty()) {
        try {
          roots.addAll(caCerts(env(caCertEnv).getBytes(StandardCharsets.UTF_8), "could not append certificates"));
        } catch (HttpSendFailure e) {
          throw HttpSendFailure.internal("could not add CA certificates from envvar \"" + caCertEnv + "\": " + e.getMessage());
        }
      }
      return new Material(useSystemCerts, insecureSkipVerify, roots, credentials);
    } catch (GeneralSecurityException e) {
      throw HttpSendFailure.internal(String.valueOf(e.getMessage()));
    }
  }

  /** The parsed result of {@link #resolve()}. */
  static final class Material {
    private final boolean useSystemCerts;
    private final boolean insecureSkipVerify;
    private final List<X509Certificate> roots;
    private final List<Credential> credentials;

    Material(boolean useSystemCerts, boolean insecureSkipVerify, List<X509Certificate> roots, List<Credential> credentials) {
      this.useSystemCerts = useSystemCerts;
      this.insecureSkipVerify = insecureSkipVerify;
      this.roots = roots;
      this.credentials = credentials;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Material that = (Material) o;
      return useSystemCerts == that.useSystemCerts
          && insecureSkipVerify == that.insecureSkipVerify
          && roots.equals(that.roots)
          && credentials.equals(that.credentials);
    }

    @Override
    public int hashCode() {
      return Objects.hash(useSystemCerts, insecureSkipVerify, roots, credentials);
    }
  }

  /** Builds the TLS context described by {@code material}. */
  static SSLContext sslContext(Material material) {
    try {
      TrustManager[] trustManagers;
      if (material.insecureSkipVerify) {
        trustManagers = new TrustManager[] {new TrustAllManager()};
      } else {
        trustManagers = trustManagers(material.roots, material.useSystemCerts);
      }

      KeyManager[] keyManagers = null;
      if (!material.credentials.isEmpty()) {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        for (int i = 0; i < material.credentials.size(); i++) {
          Credential c = material.credentials.get(i);
          keyStore.setKeyEntry("client-" + i, c.key, new char[0], c.chain);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);
        keyManagers = kmf.getKeyManagers();
      }

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(keyManagers, trustManagers, null);
      return context;
    } catch (IOException | GeneralSecurityException e) {
      throw HttpSendFailure.internal(String.valueOf(e.getMessage()));
    }
  }

  /**
   * The roots to trust. Like OPA, CA certificates replace the default roots unless {@code
   * tls_use_system_certs} is also set, in which case they are added to them.
   */
  private static TrustManager[] trustManagers(List<X509Certificate> roots, boolean useSystemCerts) throws IOException, GeneralSecurityException {
    TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    if (roots.isEmpty()) {
      factory.init((KeyStore) null);
      return factory.getTrustManagers();
    }

    KeyStore trustStore = KeyStore.getInstance("PKCS12");
    trustStore.load(null, null);
    int index = 0;
    if (useSystemCerts) {
      TrustManagerFactory system = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      system.init((KeyStore) null);
      for (TrustManager tm : system.getTrustManagers()) {
        if (tm instanceof X509TrustManager) {
          for (X509Certificate issuer : ((X509TrustManager) tm).getAcceptedIssuers()) {
            trustStore.setCertificateEntry("system-" + index++, issuer);
          }
        }
      }
    }
    for (X509Certificate root : roots) {
      trustStore.setCertificateEntry("ca-" + index++, root);
    }
    factory.init(trustStore);
    return factory.getTrustManagers();
  }

  private static String env(String name) {
    String value = System.getenv(name);
    if (value == null || value.isEmpty()) {
      throw HttpSendFailure.internal("environment variable " + name + " is not set");
    }
    return value;
  }

  private static byte[] readFile(String path) {
    try {
      return Files.readAllBytes(Paths.get(path));
    } catch (NoSuchFileException e) {
      throw HttpSendFailure.internal("open " + path + ": no such file or directory");
    } catch (AccessDeniedException e) {
      throw HttpSendFailure.internal("open " + path + ": permission denied");
    } catch (IOException | RuntimeException e) {
      throw HttpSendFailure.internal("open " + path + ": " + e.getMessage());
    }
  }

  // ---------------------------------------------------------------------------------------------
  // PEM handling
  // ---------------------------------------------------------------------------------------------

  private static final class Credential {
    final PrivateKey key;
    final Certificate[] chain;

    Credential(PrivateKey key, Certificate[] chain) {
      this.key = key;
      this.chain = chain;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Credential that = (Credential) o;
      return key.equals(that.key) && Arrays.equals(chain, that.chain);
    }

    @Override
    public int hashCode() {
      return 31 * key.hashCode() + Arrays.hashCode(chain);
    }
  }

  private static final class PemBlock {
    final String type;
    final byte[] der;

    PemBlock(String type, byte[] der) {
      this.type = type;
      this.der = der;
    }
  }

  private static List<PemBlock> pemBlocks(byte[] pem) {
    List<PemBlock> blocks = new ArrayList<>();
    Matcher m = PEM_BLOCK.matcher(new String(pem, StandardCharsets.ISO_8859_1));
    while (m.find()) {
      try {
        blocks.add(new PemBlock(m.group(1), Base64.getMimeDecoder().decode(m.group(2).trim())));
      } catch (IllegalArgumentException e) {
        // A block that is not valid base64 is skipped, as in Go's pem.Decode.
      }
    }
    return blocks;
  }

  private static List<X509Certificate> parseCertificates(List<PemBlock> blocks) throws GeneralSecurityException {
    CertificateFactory factory = CertificateFactory.getInstance("X.509");
    List<X509Certificate> certs = new ArrayList<>();
    for (PemBlock block : blocks) {
      if ("CERTIFICATE".equals(block.type)) {
        try {
          certs.add((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(block.der)));
        } catch (GeneralSecurityException e) {
          // Skipped like x509.CertPool.AppendCertsFromPEM does.
        }
      }
    }
    return certs;
  }

  private static List<X509Certificate> caCerts(byte[] pem, String failureMessage) throws GeneralSecurityException {
    List<X509Certificate> certs = parseCertificates(pemBlocks(pem));
    if (certs.isEmpty()) {
      throw HttpSendFailure.internal(failureMessage);
    }
    return certs;
  }

  private static Credential keyPair(byte[] certPem, byte[] keyPem) throws GeneralSecurityException {
    List<PemBlock> certBlocks = pemBlocks(certPem);
    List<X509Certificate> chain = parseCertificates(certBlocks);
    if (chain.isEmpty()) {
      throw HttpSendFailure.internal(
          certBlocks.isEmpty()
              ? "tls: failed to find any PEM data in certificate input"
              : "tls: failed to find certificate PEM data in certificate input");
    }

    List<PemBlock> keyBlocks = pemBlocks(keyPem);
    if (keyBlocks.isEmpty()) {
      throw HttpSendFailure.internal("tls: failed to find any PEM data in key input");
    }
    for (PemBlock block : keyBlocks) {
      if (block.type.endsWith("PRIVATE KEY")) {
        return new Credential(privateKey(block), chain.toArray(new Certificate[0]));
      }
    }
    throw HttpSendFailure.internal("tls: failed to find PEM block with type ending in \"PRIVATE KEY\" in key input");
  }

  /** Reads a PKCS#8, PKCS#1 (RSA) or SEC1 (EC) private key, the three forms Go's tls package takes. */
  private static PrivateKey privateKey(PemBlock block) throws GeneralSecurityException {
    byte[] pkcs8;
    switch (block.type) {
      case "RSA PRIVATE KEY":
        pkcs8 = Der.pkcs1ToPkcs8(block.der);
        break;
      case "EC PRIVATE KEY":
        pkcs8 = Der.sec1ToPkcs8(block.der);
        break;
      default:
        pkcs8 = block.der;
        break;
    }

    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(pkcs8);
    GeneralSecurityException failure = null;
    for (String algorithm : new String[] {"RSA", "EC", "Ed25519"}) {
      try {
        return KeyFactory.getInstance(algorithm).generatePrivate(spec);
      } catch (GeneralSecurityException e) {
        failure = e;
      }
    }
    throw HttpSendFailure.internal("tls: failed to parse private key: " + failure.getMessage());
  }

  // ---------------------------------------------------------------------------------------------
  // Trust-all manager for tls_insecure_skip_verify
  // ---------------------------------------------------------------------------------------------

  /**
   * Accepts any certificate chain and skips host name checks, which is what {@code
   * tls_insecure_skip_verify: true} asks for. The JDK delegates both checks to the trust manager,
   * so an extended manager that does nothing disables them.
   */
  private static final class TrustAllManager extends X509ExtendedTrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
      // trust everything
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
      // trust everything
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
      // trust everything
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
      // trust everything
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
      // trust everything
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
      // trust everything
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Minimal DER helpers: wrap PKCS#1 and SEC1 keys as PKCS#8, which is what the JDK's KeyFactory reads.
  // ---------------------------------------------------------------------------------------------

  private static final class Der {
    private static final byte[] RSA_ALGORITHM = {
      0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
    };
    private static final byte[] EC_PUBLIC_KEY_OID = {0x06, 0x07, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x02, 0x01};
    private static final byte[] VERSION_ZERO = {0x02, 0x01, 0x00};

    private Der() {}

    static byte[] pkcs1ToPkcs8(byte[] pkcs1) {
      return tlv(0x30, VERSION_ZERO, RSA_ALGORITHM, tlv(0x04, pkcs1));
    }

    /** SEC1 {@code ECPrivateKey ::= SEQUENCE { version, privateKey, [0] parameters, [1] publicKey }}. */
    static byte[] sec1ToPkcs8(byte[] sec1) throws InvalidKeySpecException {
      byte[] curveOid = null;
      try {
        int[] seq = header(sec1, 0);
        int pos = seq[1];
        int end = seq[1] + seq[2];
        while (pos < end) {
          int[] h = header(sec1, pos);
          if (h[0] == 0xa0) {
            int[] oid = header(sec1, h[1]);
            curveOid = Arrays.copyOfRange(sec1, h[1], oid[1] + oid[2]);
          }
          pos = h[1] + h[2];
        }
      } catch (ArrayIndexOutOfBoundsException e) {
        throw new InvalidKeySpecException("malformed EC private key");
      }
      if (curveOid == null) {
        throw new InvalidKeySpecException("EC private key without curve parameters");
      }
      return tlv(0x30, VERSION_ZERO, tlv(0x30, EC_PUBLIC_KEY_OID, curveOid), tlv(0x04, sec1));
    }

    /** Returns {tag, offset of content, length of content} of the element starting at {@code pos}. */
    private static int[] header(byte[] b, int pos) {
      int tag = b[pos] & 0xff;
      int len = b[pos + 1] & 0xff;
      int offset = pos + 2;
      if (len >= 0x80) {
        int n = len & 0x7f;
        len = 0;
        for (int i = 0; i < n; i++) {
          len = (len << 8) | (b[offset++] & 0xff);
        }
      }
      return new int[] {tag, offset, len};
    }

    private static byte[] tlv(int tag, byte[]... parts) {
      int length = 0;
      for (byte[] part : parts) {
        length += part.length;
      }
      byte[] lengthBytes;
      if (length < 0x80) {
        lengthBytes = new byte[] {(byte) length};
      } else if (length < 0x100) {
        lengthBytes = new byte[] {(byte) 0x81, (byte) length};
      } else if (length < 0x10000) {
        lengthBytes = new byte[] {(byte) 0x82, (byte) (length >> 8), (byte) length};
      } else {
        lengthBytes = new byte[] {(byte) 0x83, (byte) (length >> 16), (byte) (length >> 8), (byte) length};
      }
      byte[] out = new byte[1 + lengthBytes.length + length];
      out[0] = (byte) tag;
      System.arraycopy(lengthBytes, 0, out, 1, lengthBytes.length);
      int pos = 1 + lengthBytes.length;
      for (byte[] part : parts) {
        System.arraycopy(part, 0, out, pos, part.length);
        pos += part.length;
      }
      return out;
    }
  }
}
