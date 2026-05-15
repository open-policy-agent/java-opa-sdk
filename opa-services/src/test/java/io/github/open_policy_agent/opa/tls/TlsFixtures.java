package io.github.open_policy_agent.opa.tls;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test helper that generates a tiny self-signed CA plus server and client leaf certificates, using
 * an {@code openssl} subprocess. Keeps crypto material out of the repository.
 *
 * <p>Tests that use this helper assume {@code openssl} (or LibreSSL) is on PATH.
 */
public final class TlsFixtures {

  public final Path ca;
  public final Path caKey;
  public final Path server;
  public final Path serverKey;
  public final Path client;
  public final Path clientKey;
  public final Path clientKeyEncrypted;
  public final String clientKeyPassphrase;

  private TlsFixtures(
      Path ca,
      Path caKey,
      Path server,
      Path serverKey,
      Path client,
      Path clientKey,
      Path clientKeyEncrypted,
      String passphrase) {
    this.ca = ca;
    this.caKey = caKey;
    this.server = server;
    this.serverKey = serverKey;
    this.client = client;
    this.clientKey = clientKey;
    this.clientKeyEncrypted = clientKeyEncrypted;
    this.clientKeyPassphrase = passphrase;
  }

  public static TlsFixtures generate(Path dir) throws IOException, InterruptedException {
    Path ca = dir.resolve("ca.pem");
    Path caKey = dir.resolve("ca-key.pem");
    Path caSrl = dir.resolve("ca.srl");

    // CA key + self-signed cert.
    run(dir, "openssl", "genpkey", "-algorithm", "RSA", "-out", caKey.toString(),
        "-pkeyopt", "rsa_keygen_bits:2048");
    run(dir, "openssl", "req", "-x509", "-new", "-key", caKey.toString(), "-out", ca.toString(),
        "-days", "1", "-subj", "/CN=opa-test-ca");

    Path server = dir.resolve("server.pem");
    Path serverKey = dir.resolve("server-key.pem");
    issueLeaf(dir, ca, caKey, caSrl, server, serverKey, "localhost",
        "subjectAltName=DNS:localhost,IP:127.0.0.1");

    Path client = dir.resolve("client.pem");
    Path clientKey = dir.resolve("client-key.pem");
    issueLeaf(dir, ca, caKey, caSrl, client, clientKey, "opa-test-client", null);

    // Produce a passphrase-protected copy of the client key (PKCS#8 encrypted PEM).
    String passphrase = "correct-horse-battery-staple";
    Path clientKeyEnc = dir.resolve("client-key-encrypted.pem");
    run(
        dir,
        "openssl",
        "pkcs8",
        "-topk8",
        "-in",
        clientKey.toString(),
        "-out",
        clientKeyEnc.toString(),
        "-passout",
        "pass:" + passphrase);

    return new TlsFixtures(ca, caKey, server, serverKey, client, clientKey, clientKeyEnc, passphrase);
  }

  private static void issueLeaf(
      Path dir,
      Path ca,
      Path caKey,
      Path caSrl,
      Path certOut,
      Path keyOut,
      String cn,
      String extensions)
      throws IOException, InterruptedException {
    Path csr = dir.resolve(cn + ".csr");
    run(dir, "openssl", "genpkey", "-algorithm", "RSA", "-out", keyOut.toString(),
        "-pkeyopt", "rsa_keygen_bits:2048");
    run(dir, "openssl", "req", "-new", "-key", keyOut.toString(), "-out", csr.toString(),
        "-subj", "/CN=" + cn);

    java.util.List<String> cmd =
        new java.util.ArrayList<>(
            java.util.Arrays.asList(
                "openssl",
                "x509",
                "-req",
                "-in",
                csr.toString(),
                "-CA",
                ca.toString(),
                "-CAkey",
                caKey.toString(),
                "-CAcreateserial",
                "-CAserial",
                caSrl.toString(),
                "-out",
                certOut.toString(),
                "-days",
                "1"));
    if (extensions != null) {
      // LibreSSL's openssl x509 needs extensions via an extfile.
      Path extFile = dir.resolve(cn + ".ext");
      Files.write(extFile, extensions.getBytes());
      cmd.add("-extfile");
      cmd.add(extFile.toString());
    }
    run(dir, cmd.toArray(new String[0]));
    Files.deleteIfExists(csr);
  }

  private static void run(Path dir, String... command) throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true);
    Process p = pb.start();
    byte[] out = p.getInputStream().readAllBytes();
    int rc = p.waitFor();
    if (rc != 0) {
      throw new IOException(
          "openssl command failed (rc=" + rc + "): " + String.join(" ", command) + "\n" + new String(out));
    }
  }
}
