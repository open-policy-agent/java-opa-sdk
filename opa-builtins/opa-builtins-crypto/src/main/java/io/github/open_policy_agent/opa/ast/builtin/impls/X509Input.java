// This file contains a Java translation of code from the Go project:
//   encoding/pem (pem.go), Copyright 2009 The Go Authors.
//   encoding/base64 (base64.go), Copyright 2009 The Go Authors.
// That code is used under the BSD-style license reproduced in
// src/main/resources/META-INF/licenses/LICENSE-golang.txt (Copyright 2009 The Go Authors).
// The translation and the rest of this file are licensed under the Apache License 2.0.
package io.github.open_policy_agent.opa.ast.builtin.impls;

import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Decodes the string argument of the crypto.x509 builtins the way OPA's {@code
 * getX509CertsFromString} does: a PEM string, or standard base64 of either PEM or concatenated DER
 * certificates. PEM and base64 decoding are ports of Go's {@code encoding/pem} and {@code
 * encoding/base64} so that the accepted inputs and the error messages match.
 */
final class X509Input {

  private static final String PEM_BEGIN = "-----BEGIN";
  private static final String PEM_START = "\n-----BEGIN ";
  private static final String PEM_END = "\n-----END ";
  private static final String PEM_END_OF_LINE = "-----";
  private static final String BLOCK_TYPE_CERTIFICATE = "CERTIFICATE";
  private static final int[] BASE64_DECODE = new int[256];

  static {
    Arrays.fill(BASE64_DECODE, -1);
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    for (int i = 0; i < alphabet.length(); i++) {
      BASE64_DECODE[alphabet.charAt(i)] = i;
    }
  }

  private X509Input() {}

  /**
   * Parses every certificate in the input. Throws a {@link BuiltinError} carrying Go's error
   * message when the input is not valid base64/PEM or a certificate fails to parse.
   */
  static List<X509Cert> parseCertificates(String input) {
    byte[] data = input.getBytes(StandardCharsets.UTF_8);
    if (X509Strings.latin1(data).startsWith(PEM_BEGIN)) {
      return parseDer(certificatesFromPem(X509Strings.latin1(data)));
    }
    int[] errOffset = new int[1];
    byte[] decoded = decodeBase64(data, errOffset);
    if (decoded == null) {
      throw new BuiltinError("illegal base64 data at input byte " + errOffset[0]);
    }
    String text = X509Strings.latin1(decoded);
    return parseDer(text.startsWith(PEM_BEGIN) ? certificatesFromPem(text) : decoded);
  }

  /** Concatenates the contents of the PEM blocks, which must all be CERTIFICATE blocks. */
  private static byte[] certificatesFromPem(String data) {
    ByteArrayOutputStream der = new ByteArrayOutputStream();
    int offset = 0;
    while (offset < data.length()) {
      PemBlock block = decodePem(data, offset);
      if (block == null) {
        break;
      }
      if (!BLOCK_TYPE_CERTIFICATE.equals(block.type())) {
        throw new BuiltinError("PEM block type is '" + block.type() + "', expected " + BLOCK_TYPE_CERTIFICATE);
      }
      offset = block.rest();
      der.writeBytes(block.bytes());
    }
    return der.toByteArray();
  }

  /** Go's x509.ParseCertificates: certificates concatenated with no padding in between. */
  private static List<X509Cert> parseDer(byte[] der) {
    List<X509Cert> certs = new ArrayList<>();
    int offset = 0;
    while (offset < der.length) {
      X509Cert cert = X509Parser.parse(der, offset);
      certs.add(cert);
      offset += cert.raw.length;
    }
    return certs;
  }

  /** A decoded PEM block; {@code rest} is the offset in the input where decoding should resume. */
  private record PemBlock(String type, byte[] bytes, int rest) {}

  /**
   * Port of Go's {@code pem.Decode} (Go 1.27) over a byte string held as ISO-8859-1: finds the first
   * END line, then the last BEGIN line before it, skipping candidates that are not well-formed.
   * Returns null when no block is found.
   */
  private static PemBlock decodePem(String data, int from) {
    int rest = from;
    int endTrailerIndex = 0;
    while (true) {
      if (endTrailerIndex < 0 || endTrailerIndex > data.length() - rest) {
        return null;
      }
      rest += endTrailerIndex;
      int endIndex = data.indexOf(PEM_END, rest);
      if (endIndex < 0) {
        return null;
      }
      endIndex -= rest;
      endTrailerIndex = endIndex + PEM_END.length();
      // The last "-----BEGIN " (PEM_START without its newline) within rest[:endIndex].
      int beginIndex = data.lastIndexOf(PEM_START.substring(1), rest + endIndex - (PEM_START.length() - 1)) - rest;
      if (beginIndex < 0 || beginIndex > 0 && data.charAt(rest + beginIndex - 1) != '\n') {
        continue;
      }
      int skip = beginIndex + PEM_START.length() - 1;
      rest += skip;
      endIndex -= skip;
      endTrailerIndex -= skip;

      int[] line = getLine(data, rest);
      rest += line[2];
      endIndex -= line[2];
      endTrailerIndex -= line[2];
      String typeLine = data.substring(line[0], line[1]);
      if (!typeLine.endsWith(PEM_END_OF_LINE)) {
        continue;
      }
      typeLine = typeLine.substring(0, typeLine.length() - PEM_END_OF_LINE.length());

      boolean hasHeaders = false;
      while (true) {
        if (rest >= data.length()) {
          return null;
        }
        int[] h = getLine(data, rest);
        if (data.substring(h[0], h[1]).indexOf(':') < 0) {
          break;
        }
        hasHeaders = true;
        rest += h[2];
        endIndex -= h[2];
        endTrailerIndex -= h[2];
      }
      if (hasHeaders && endIndex < 0) {
        continue;
      }

      int endTrailer = rest + endTrailerIndex;
      int restOfEndLine = endTrailer + typeLine.length() + PEM_END_OF_LINE.length();
      if (restOfEndLine > data.length()
          || !data.startsWith(typeLine, endTrailer)
          || !data.startsWith(PEM_END_OF_LINE, restOfEndLine - PEM_END_OF_LINE.length())) {
        continue;
      }
      int[] tail = getLine(data, restOfEndLine);
      if (tail[1] != tail[0]) {
        continue;
      }

      byte[] bytes = new byte[0];
      if (endIndex > 0) {
        String base64 = data.substring(rest, rest + endIndex).replace(" ", "").replace("\t", "");
        bytes = decodeBase64(base64.getBytes(StandardCharsets.ISO_8859_1), new int[1]);
        if (bytes == null) {
          continue;
        }
      }
      int afterEnd = rest + endIndex + PEM_END.length() - 1;
      String type = new String(typeLine.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
      return new PemBlock(type, bytes, afterEnd + getLine(data, afterEnd)[2]);
    }
  }

  /**
   * Go's pem getLine: returns {lineStart, lineEnd, consumed}, where the line excludes the newline
   * (and a preceding carriage return) and has trailing spaces and tabs trimmed.
   */
  private static int[] getLine(String data, int start) {
    int i = data.indexOf('\n', start);
    int j = i < 0 ? data.length() : i + 1;
    if (i < 0) {
      i = data.length();
    } else if (i > start && data.charAt(i - 1) == '\r') {
      i--;
    }
    while (i > start && (data.charAt(i - 1) == ' ' || data.charAt(i - 1) == '\t')) {
      i--;
    }
    return new int[] {start, i, j - start};
  }

  /**
   * Port of Go's {@code base64.StdEncoding.Decode}: padding is required, '\r' and '\n' are ignored
   * and the first invalid byte offset is reported through {@code errOffset}. Returns null on error.
   */
  private static byte[] decodeBase64(byte[] src, int[] errOffset) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(src.length / 4 * 3);
    int si = 0;
    while (si < src.length) {
      int[] dbuf = new int[4];
      int dlen = 4;
      int j = 0;
      int trailingGarbage = -1;
      while (j < 4) {
        if (si == src.length) {
          if (j == 0) {
            return out.toByteArray();
          }
          errOffset[0] = si - j;
          return null;
        }
        int in = src[si] & 0xff;
        si++;
        int decoded = BASE64_DECODE[in];
        if (decoded >= 0) {
          dbuf[j] = decoded;
          j++;
          continue;
        }
        if (in == '\n' || in == '\r') {
          continue;
        }
        if (in != '=' || j < 2) {
          errOffset[0] = si - 1;
          return null;
        }
        if (j == 2) {
          // "==" is expected; the first '=' has been consumed.
          si = skipNewlines(src, si);
          if (si == src.length) {
            errOffset[0] = src.length;
            return null;
          }
          if (src[si] != '=') {
            errOffset[0] = si - 1;
            return null;
          }
          si++;
        }
        si = skipNewlines(src, si);
        if (si < src.length) {
          trailingGarbage = si;
        }
        dlen = j;
        break;
      }
      int val = dbuf[0] << 18 | dbuf[1] << 12 | dbuf[2] << 6 | dbuf[3];
      out.write(val >> 16);
      if (dlen > 2) {
        out.write(val >> 8);
      }
      if (dlen > 3) {
        out.write(val);
      }
      if (trailingGarbage >= 0) {
        errOffset[0] = trailingGarbage;
        return null;
      }
    }
    return out.toByteArray();
  }

  private static int skipNewlines(byte[] src, int si) {
    int i = si;
    while (i < src.length && (src[i] == '\n' || src[i] == '\r')) {
      i++;
    }
    return i;
  }
}
