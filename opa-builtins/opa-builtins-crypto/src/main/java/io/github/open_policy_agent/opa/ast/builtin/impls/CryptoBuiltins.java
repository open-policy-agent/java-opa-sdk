package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

public class CryptoBuiltins implements BuiltinProvider {

  @Override
  public Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    CryptoBuiltins instance = new CryptoBuiltins();
    // @formatter:off
    return Map.ofEntries(
        Map.entry("crypto.md5", instance::md5),
        Map.entry("crypto.sha1", instance::sha1),
        Map.entry("crypto.sha256", instance::sha256),
        Map.entry("crypto.hmac.md5", instance::hmacMd5),
        Map.entry("crypto.hmac.sha1", instance::hmacSha1),
        Map.entry("crypto.hmac.sha256", instance::hmacSha256),
        Map.entry("crypto.hmac.sha512", instance::hmacSha512),
        Map.entry("crypto.hmac.equal", instance::hmacEqual),
        Map.entry("crypto.x509.parse_certificates", instance::x509ParseCertificates),
        Map.entry("crypto.x509.parse_and_verify_certificates", instance::x509ParseAndVerifyCertificates));
    // @formatter:on
  }

  @OpaBuiltin(
      name = "crypto.md5",
      description = "Returns the MD5 hash of the input string as a hex-encoded string",
      categories = {"crypto"},
      args = {@OpaType(type = "string", name = "x", description = "input string")},
      result = @OpaType(type = "string", name = "y", description = "MD5-hash of `x`"))
  public RegoString md5(EvaluationContext ctx, RegoValue[] args) {
    return hashBuiltin(args, "MD5");
  }

  @OpaBuiltin(
      name = "crypto.sha1",
      description = "Returns the SHA1 hash of the input string as a hex-encoded string",
      categories = {"crypto"},
      args = {@OpaType(type = "string", name = "x", description = "input string")},
      result = @OpaType(type = "string", name = "y", description = "SHA1-hash of `x`"))
  public RegoString sha1(EvaluationContext ctx, RegoValue[] args) {
    return hashBuiltin(args, "SHA-1");
  }

  @OpaBuiltin(
      name = "crypto.sha256",
      description = "Returns the SHA256 hash of the input string as a hex-encoded string",
      categories = {"crypto"},
      args = {@OpaType(type = "string", name = "x", description = "input string")},
      result = @OpaType(type = "string", name = "y", description = "SHA256-hash of `x`"))
  public RegoString sha256(EvaluationContext ctx, RegoValue[] args) {
    return hashBuiltin(args, "SHA-256");
  }

  @OpaBuiltin(
      name = "crypto.hmac.md5",
      description = "Returns the HMAC-MD5 hash of the input string using the specified key",
      categories = {"crypto"},
      args = {
        @OpaType(type = "string", name = "x", description = "input string"),
        @OpaType(type = "string", name = "key", description = "key to use")
      },
      result = @OpaType(type = "string", name = "y", description = "MD5-HMAC of `x`"))
  public RegoString hmacMd5(EvaluationContext ctx, RegoValue[] args) {
    return hmacBuiltin(args, "HmacMD5");
  }

  @OpaBuiltin(
      name = "crypto.hmac.sha1",
      description = "Returns the HMAC-SHA1 hash of the input string using the specified key",
      categories = {"crypto"},
      args = {
        @OpaType(type = "string", name = "x", description = "input string"),
        @OpaType(type = "string", name = "key", description = "key to use")
      },
      result = @OpaType(type = "string", name = "y", description = "SHA1-HMAC of `x`"))
  public RegoString hmacSha1(EvaluationContext ctx, RegoValue[] args) {
    return hmacBuiltin(args, "HmacSHA1");
  }

  @OpaBuiltin(
      name = "crypto.hmac.sha256",
      description = "Returns the HMAC-SHA256 hash of the input string using the specified key",
      categories = {"crypto"},
      args = {
        @OpaType(type = "string", name = "x", description = "input string"),
        @OpaType(type = "string", name = "key", description = "key to use")
      },
      result = @OpaType(type = "string", name = "y", description = "SHA256-HMAC of `x`"))
  public RegoString hmacSha256(EvaluationContext ctx, RegoValue[] args) {
    return hmacBuiltin(args, "HmacSHA256");
  }

  @OpaBuiltin(
      name = "crypto.hmac.sha512",
      description = "Returns the HMAC-SHA512 hash of the input string using the specified key",
      categories = {"crypto"},
      args = {
        @OpaType(type = "string", name = "x", description = "input string"),
        @OpaType(type = "string", name = "key", description = "key to use")
      },
      result = @OpaType(type = "string", name = "y", description = "SHA512-HMAC of `x`"))
  public RegoString hmacSha512(EvaluationContext ctx, RegoValue[] args) {
    return hmacBuiltin(args, "HmacSHA512");
  }

  @OpaBuiltin(
      name = "crypto.hmac.equal",
      description =
          "Constant-time comparison of two MAC values to prevent timing attacks. Returns true if"
              + " equal, false otherwise.",
      categories = {"crypto"},
      args = {
        @OpaType(type = "string", name = "mac1", description = "mac1 to compare"),
        @OpaType(type = "string", name = "mac2", description = "mac2 to compare")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if the MACs are equals, `false` otherwise"))
  public RegoBoolean hmacEqual(EvaluationContext ctx, RegoValue[] args) {
    String mac1 = getArg(args, 0, RegoString.class).getValue();
    String mac2 = getArg(args, 1, RegoString.class).getValue();
    // Use MessageDigest.isEqual for constant-time comparison to prevent timing attacks
    boolean equal =
        MessageDigest.isEqual(
            mac1.getBytes(StandardCharsets.UTF_8), mac2.getBytes(StandardCharsets.UTF_8));
    return RegoBoolean.of(equal);
  }

  @OpaBuiltin(
      name = "crypto.x509.parse_certificates",
      description =
          "Returns zero or more certificates from the given string, which holds one or more concatenated PEM"
              + " blocks, optionally base64 encoded as a whole, or base64 encoded DER certificate data.",
      categories = {"crypto"},
      args = {
        @OpaType(
            type = "string",
            name = "certs",
            description =
                "base64 encoded DER or PEM data containing one or more certificates or a PEM string of one or more certificates")
      },
      result =
          @OpaType(
              type = "array",
              name = "output",
              description = "parsed X.509 certificates represented as objects",
              dynamic = @OpaDynamic(type = "object")))
  public RegoArray x509ParseCertificates(EvaluationContext ctx, RegoValue[] args) {
    String certs = getArg(args, 0, RegoString.class).getValue();
    return x509Values(X509Input.parseCertificates(certs));
  }

  @OpaBuiltin(
      name = "crypto.x509.parse_and_verify_certificates",
      description =
          "Returns one or more certificates from the given string containing PEM or base64 encoded DER"
              + " certificates after verifying the supplied certificates form a complete certificate chain back to a"
              + " trusted root. The first certificate is treated as the root and the last is treated as the leaf, with"
              + " all others being treated as intermediates.",
      categories = {"crypto"},
      args = {
        @OpaType(
            type = "string",
            name = "certs",
            description =
                "base64 encoded DER or PEM data containing two or more certificates where the first is a root CA, the last is a"
                    + " leaf certificate, and all others are intermediate CAs")
      },
      result =
          @OpaType(
              type = "array",
              name = "output",
              description =
                  "array of `[valid, certs]`: if the input certificate chain could be verified then `valid` is `true` and"
                      + " `certs` is an array of X.509 certificates represented as objects; if the input certificate chain"
                      + " could not be verified then `valid` is `false` and `certs` is `[]`"))
  public RegoArray x509ParseAndVerifyCertificates(EvaluationContext ctx, RegoValue[] args) {
    String certs = getArg(args, 0, RegoString.class).getValue();
    return x509ParseAndVerify(certs, Instant.now());
  }

  /**
   * Verifies the chain at the given time. As in OPA, input that cannot be parsed, or a chain that
   * does not verify, yields {@code [false, []]} rather than an error.
   */
  static RegoArray x509ParseAndVerify(String input, Instant now) {
    List<X509Cert> chain;
    try {
      chain = X509Verifier.verify(X509Input.parseCertificates(input), now);
    } catch (BuiltinError e) {
      chain = null;
    }
    if (chain == null) {
      return new RegoArray(List.of(RegoBoolean.FALSE, new RegoArray()));
    }
    return new RegoArray(List.of(RegoBoolean.TRUE, x509Values(chain)));
  }

  private static RegoArray x509Values(List<X509Cert> certs) {
    try {
      return X509Values.certificates(certs);
    } catch (IllegalArgumentException e) {
      // Go's JSON encoding of the certificate failed (e.g. an out-of-range time zone offset).
      throw new BuiltinError(e.getMessage());
    }
  }

  /**
   * Wrapper method for hash functions to reduce code duplication.
   *
   * @param args the arguments containing the input string
   * @param algorithm the hash algorithm (e.g., "MD5", "SHA-1", "SHA-256")
   * @return RegoString containing the lowercase hex-encoded hash
   */
  private RegoString hashBuiltin(RegoValue[] args, String algorithm) {
    String input = getArg(args, 0, RegoString.class).getValue();
    return new RegoString(hash(input, algorithm));
  }

  /**
   * Wrapper method for HMAC functions to reduce code duplication.
   *
   * @param args the arguments containing the message and key
   * @param algorithm the HMAC algorithm (e.g., "HmacMD5", "HmacSHA1", "HmacSHA256", "HmacSHA512")
   * @return RegoString containing the lowercase hex-encoded HMAC
   */
  private RegoString hmacBuiltin(RegoValue[] args, String algorithm) {
    String message = getArg(args, 0, RegoString.class).getValue();
    String key = getArg(args, 1, RegoString.class).getValue();
    return new RegoString(hmac(message, key, algorithm));
  }

  /**
   * Compute a hash using the specified algorithm and return as a lowercase hex-encoded string.
   *
   * @param input the input string to hash
   * @param algorithm the hash algorithm (e.g., "MD5", "SHA-1", "SHA-256")
   * @return lowercase hex-encoded hash string
   */
  private String hash(String input, String algorithm) {
    try {
      MessageDigest digest = MessageDigest.getInstance(algorithm);
      byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new BuiltinError("Hash algorithm not available: " + algorithm);
    }
  }

  /**
   * Compute an HMAC using the specified algorithm and return as a lowercase hex-encoded string.
   *
   * <p>This implementation supports weak keys (less than the recommended key size) to match OPA
   * behavior.
   *
   * @param message the message to hash
   * @param key the secret key
   * @param algorithm the HMAC algorithm (e.g., "HmacMD5", "HmacSHA1", "HmacSHA256", "HmacSHA512")
   * @return lowercase hex-encoded HMAC string
   */
  private String hmac(String message, String key, String algorithm) {
    try {
      Mac mac = Mac.getInstance(algorithm);
      SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm);
      mac.init(secretKey);
      byte[] hmacBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hmacBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new BuiltinError("HMAC algorithm not available: " + algorithm);
    } catch (InvalidKeyException e) {
      throw new BuiltinError("Invalid key for HMAC: " + e.getMessage());
    }
  }

  /**
   * Convert a byte array to a lowercase hex string.
   *
   * @param bytes the byte array to convert
   * @return lowercase hex-encoded string
   */
  private String bytesToHex(byte[] bytes) {
    StringBuilder hex = new StringBuilder();
    for (byte b : bytes) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }
}
