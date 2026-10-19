package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import io.github.open_policy_agent.opa.rego.TypeError;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Tests for crypto.x509.parse_certificates.
 *
 * <p>The fixtures in {@code src/test/resources/x509} were generated with Go 1.27.2 (the toolchain
 * OPA is built with), and each {@code .json} file holds OPA's output for the {@code .pem} file of
 * the same name; see the README there.
 */
class CryptoX509BuiltinsTest {

  private final CryptoBuiltins builtins = new CryptoBuiltins();
  private final EvaluationContext ctx = new EvaluationContext.Builder().build();

  @Test
  void providerRegistersX509Builtin() {
    boolean found =
        ServiceLoader.load(BuiltinProvider.class, CryptoBuiltins.class.getClassLoader()).stream()
            .anyMatch(p -> p.type().equals(CryptoBuiltins.class));
    assertTrue(found, "CryptoBuiltins should be registered as a BuiltinProvider");
    assertTrue(builtins.builtins().containsKey("crypto.x509.parse_certificates"));
  }

  // The full certificate object must match Go's json.Marshal of x509.Certificate, field by field.
  @Test
  void parseCertificatesMatchesOpa() {
    for (String name :
        List.of(
            "parse-rich-rsa",
            "parse-ecdsa-p384",
            "parse-ed25519",
            "parse-name-constraints-ca",
            "parse-names-and-times",
            "parse-unusual-extensions")) {
      assertEquals(resource(name + ".json").trim(), canonicalJson(parse(resource(name + ".pem"))), name);
    }
  }

  // OPA accepts a PEM string, or base64 of either PEM or concatenated DER certificates.
  @Test
  void parseCertificatesAcceptsEveryInputEncoding() {
    String pem = resource("parse-ed25519.pem") + resource("parse-ecdsa-p384.pem") + resource("parse-rich-rsa.pem");
    RegoArray expected = parse(pem);
    assertEquals(List.of("ed25519.example.com", "p384.example.com", "rich.example.com"), commonNames(expected));

    String base64Pem = Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.US_ASCII));
    assertEquals(expected, parse(base64Pem));

    byte[] concatenatedDer = concatenateDer(expected);
    assertEquals(expected, parse(Base64.getEncoder().encodeToString(concatenatedDer)));

    // Go's PEM decoder accepts CRLF line endings, and its base64 decoder skips line breaks.
    assertEquals(expected, parse(pem.replace("\n", "\r\n")));
    assertEquals(expected, parse(base64Pem.substring(0, 40) + "\r\n" + base64Pem.substring(40)));
  }

  // Go's pem.Decode skips text around blocks, headers, and blocks that are not valid base64; the
  // first input that is not a PEM block ends decoding.
  @Test
  void parseCertificatesDecodesPemLikeGo() {
    String a = resource("parse-ed25519.pem");
    String b = resource("parse-ecdsa-p384.pem");
    List<String> both = List.of("ed25519.example.com", "p384.example.com");
    assertEquals(both, commonNames(parse(a + "some text in between\n" + b)));
    assertEquals(both, commonNames(parse(a + "-----BEGIN CERTIFICATE-----\nnot base64!\n-----END CERTIFICATE-----\n" + b)));
    assertEquals(List.of("ed25519.example.com"), commonNames(parse(a + "trailing garbage")));
    String withHeaders = a.replaceFirst("\n", "\nProc-Type: 4,ENCRYPTED\nDEK-Info: none\n\n");
    assertEquals(List.of("ed25519.example.com"), commonNames(parse(withHeaders)));
  }

  // Go's x509.ParseCertificates returns no certificates (not an error) when there is no data.
  @Test
  void parseCertificatesReturnsEmptyArrayWithoutCertificates() {
    assertEquals(new RegoArray(), parse(""));
    assertEquals(new RegoArray(), parse("-----BEGIN junk"));
  }

  @Test
  void parseCertificatesRepresentsFieldsAsGoDoes() {
    RegoValue cert = parse(resource("parse-rich-rsa.pem")).getValues().get(0);

    RegoObject subject = (RegoObject) property(cert, "Subject");
    assertEquals(new RegoString("rich.example.com"), subject.getProperty("CommonName"));
    // Both countries are in one multi-valued RDN, whose DER SET encoding sorts them.
    assertEquals(strings("CA", "US"), subject.getProperty("Country"));
    assertEquals(RegoNull.INSTANCE, subject.getProperty("ExtraNames"));
    assertEquals(strings("rich.example.com", "*.rich.example.com"), property(cert, "DNSNames"));
    assertEquals(strings("192.0.2.1", "2001:db8::1"), property(cert, "IPAddresses"));
    assertEquals(
        strings(
            "spiffe://example.org/ns/default/sa/opa",
            "https://user:p%40ss@example.com:8443/a/b/c%20d?x=1&y=%20#frag%20ment",
            "urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
            "https://[2001:db8::1]:443/path"),
        property(cert, "URIStrings"));
    assertEquals(new RegoString("2020-01-01T00:00:00Z"), property(cert, "NotBefore"));
    // Enum values are Go's: an RSA key (x509.RSA) signed by the ECDSA intermediate
    // (x509.ECDSAWithSHA256).
    assertEquals(number(10), property(cert, "SignatureAlgorithm"));
    assertEquals(number(1), property(cert, "PublicKeyAlgorithm"));
    assertEquals(number(65537), property(property(cert, "PublicKey"), "E"));
    assertEquals(number(3), property(cert, "Version"));
    // Go encodes byte slices as base64 and leaves absent ones null.
    assertEquals(new RegoString("AQIDBAU="), property(cert, "SubjectKeyId"));
    assertEquals(RegoNull.INSTANCE, property(cert, "ExtraExtensions"));
    assertEquals(strings("admin@example.com"), property(cert, "EmailAddresses"));
    // A policy OID with an arc beyond 31 bits is kept in Policies but not in PolicyIdentifiers.
    assertEquals(strings("2.23.140.1.2.1", "1.2.3.18446744073709551616"), property(cert, "Policies"));
    assertEquals(1, ((RegoArray) property(cert, "PolicyIdentifiers")).length());
  }

  @Test
  void parseCertificatesReportsGoErrors() {
    Map<String, String> inputs = new TreeMap<>();
    inputs.put("foobar", "illegal base64 data at input byte 4");
    inputs.put("YmFkc3RyaW5n", "x509: malformed certificate");
    inputs.put("TUlJ\nQ", "illegal base64 data at input byte 5");
    inputs.put("AAAA====", "illegal base64 data at input byte 4");
    inputs.put(
        "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----\n",
        "PEM block type is 'RSA PRIVATE KEY', expected CERTIFICATE");
    for (Map.Entry<String, String> input : inputs.entrySet()) {
      assertBuiltinError(input.getValue(), input.getKey());
    }

    Map<String, String> fixtures = new TreeMap<>();
    fixtures.put("error-negative-serial", "x509: negative serial number");
    fixtures.put("error-duplicate-extension", "x509: certificate contains duplicate extension with OID \"2.5.29.15\"");
    fixtures.put("error-bad-utf8-name", "x509: invalid RDNSequence: invalid attribute value: invalid UTF-8 string");
    fixtures.put(
        "error-san-bad-uri", "x509: cannot parse URI \"http://[::1/x\": parse \"http://[::1/x\": missing ']' in host");
    fixtures.put("error-ec-compressed-point", "ecdsa: invalid uncompressed public key");
    fixtures.put("error-time-feb-30", "x509: malformed UTCTime");
    // Go parses a +24:00 offset but cannot encode it as RFC 3339, so the JSON conversion fails.
    fixtures.put(
        "error-time-offset-24h",
        "json: error calling MarshalJSON for type *time.Time: timezone hour outside of range [0,23]");
    for (Map.Entry<String, String> fixture : fixtures.entrySet()) {
      assertBuiltinError(fixture.getValue(), resource(fixture.getKey() + ".pem"));
    }
  }

  // A DER length larger than the input must be rejected without allocating it, and large
  // inputs must fail fast.
  @Test
  void parseCertificatesRejectsOversizedLengths() {
    byte[] declaredHuge = {0x30, (byte) 0x84, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x30, 0x00};
    assertBuiltinError("x509: malformed certificate", Base64.getEncoder().encodeToString(declaredHuge));
    // Lengths of more than four bytes, and non-minimal long-form lengths, are not DER.
    assertBuiltinError("x509: malformed certificate", Base64.getEncoder().encodeToString(new byte[] {0x30, (byte) 0x85, 0, 0, 0, 0, 1, 0}));
    assertBuiltinError("x509: malformed certificate", Base64.getEncoder().encodeToString(new byte[] {0x30, (byte) 0x81, 0x01, 0x00}));

    // An empty SEQUENCE followed by 1 MiB of zeros: Go fails on the missing TBSCertificate.
    byte[] garbage = new byte[1 << 20];
    garbage[0] = 0x30;
    assertBuiltinError("x509: malformed tbs certificate", Base64.getEncoder().encodeToString(garbage));
  }

  @Test
  void parseCertificatesRejectsNonStringInput() {
    TypeError e =
        assertThrows(TypeError.class, () -> builtins.x509ParseCertificates(ctx, new RegoValue[] {number(1)}));
    assertEquals("eval_type_error: operand 1 must be string but got number", e.getMessage());
  }

  // Like OPA, input that cannot be parsed is reported as an invalid chain rather than an error.

  // Go verifies P-224 signatures, but Java 17 no longer provides the curve, so such chains are
  // reported as invalid rather than accepted without verification.

  private RegoArray parse(String input) {
    return builtins.x509ParseCertificates(ctx, new RegoValue[] {new RegoString(input)});
  }

  private static List<String> commonNames(RegoArray certs) {
    List<String> names = new ArrayList<>();
    for (RegoValue cert : certs.getValues()) {
      names.add(((RegoString) property(property(cert, "Subject"), "CommonName")).getValue());
    }
    return names;
  }

  private void assertBuiltinError(String expected, String input) {
    BuiltinError e = assertThrows(BuiltinError.class, () -> parse(input), input);
    assertEquals("eval_builtin_error: " + expected, e.getMessage(), input);
  }

  private static byte[] concatenateDer(RegoArray certs) {
    List<byte[]> parts = new ArrayList<>();
    int length = 0;
    for (RegoValue cert : certs.getValues()) {
      byte[] der = Base64.getDecoder().decode(stringField(cert, "Raw"));
      parts.add(der);
      length += der.length;
    }
    byte[] out = new byte[length];
    int offset = 0;
    for (byte[] part : parts) {
      System.arraycopy(part, 0, out, offset, part.length);
      offset += part.length;
    }
    return out;
  }

  private static String stringField(RegoValue object, String name) {
    return ((RegoString) property(object, name)).getValue();
  }

  private static RegoValue property(RegoValue object, String name) {
    return ((RegoObject) object).getProperty(name);
  }

  private static RegoArray strings(String... values) {
    List<RegoValue> list = new ArrayList<>();
    for (String v : values) {
      list.add(new RegoString(v));
    }
    return new RegoArray(list);
  }

  private static RegoValue number(long v) {
    return new RegoBigInt(v);
  }

  private static String resource(String name) {
    try (InputStream in = CryptoX509BuiltinsTest.class.getResourceAsStream("/x509/" + name)) {
      if (in == null) {
        throw new IllegalArgumentException("missing test resource " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Serializes a value as JSON with sorted keys and no whitespace, matching the normalization the
   * expected outputs were written with (Python's json.dumps with sort_keys and ensure_ascii off).
   */
  static String canonicalJson(RegoValue v) {
    StringBuilder sb = new StringBuilder();
    writeJson(sb, v);
    return sb.toString();
  }

  private static void writeJson(StringBuilder sb, RegoValue v) {
    if (v instanceof RegoNull) {
      sb.append("null");
    } else if (v instanceof RegoBoolean) {
      sb.append(((RegoBoolean) v).getValue());
    } else if (v instanceof RegoNumber) {
      sb.append(((RegoNumber) v).getBigIntValue());
    } else if (v instanceof RegoString) {
      writeString(sb, ((RegoString) v).getValue());
    } else if (v instanceof RegoArray) {
      sb.append('[');
      List<RegoValue> values = ((RegoArray) v).getValues();
      for (int i = 0; i < values.size(); i++) {
        if (i > 0) {
          sb.append(',');
        }
        writeJson(sb, values.get(i));
      }
      sb.append(']');
    } else {
      Map<String, RegoValue> sorted = new TreeMap<>();
      for (Map.Entry<RegoValue, RegoValue> e : ((RegoObject) v).getProperties().entrySet()) {
        sorted.put(((RegoString) e.getKey()).getValue(), e.getValue());
      }
      sb.append('{');
      boolean first = true;
      for (Map.Entry<String, RegoValue> e : sorted.entrySet()) {
        if (!first) {
          sb.append(',');
        }
        first = false;
        writeString(sb, e.getKey());
        sb.append(':');
        writeJson(sb, e.getValue());
      }
      sb.append('}');
    }
  }

  private static void writeString(StringBuilder sb, String s) {
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\f':
          sb.append("\\f");
          break;
        default:
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    sb.append('"');
  }
}
