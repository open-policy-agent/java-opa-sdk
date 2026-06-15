package io.github.open_policy_agent.opa.ast.builtin.impls;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import java.io.IOException;
import java.io.StringReader;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.*;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

public class TokenBuiltins implements BuiltinProvider {

  private static final RegoString AUD_PROPERTY = new RegoString("aud");
  private static final RegoString ALG_PROPERTY = new RegoString("alg");
  private static final RegoString ISS_PROPERTY = new RegoString("iss");
  private static final RegoString TIME_PROPERTY = new RegoString("time");
  private static final RegoString EXP_PROPERTY = new RegoString("exp");
  private static final RegoString NBF_PROPERTY = new RegoString("nbf");
  private static final RegoString CERT_PROPERTY = new RegoString("cert");
  private static final RegoString SECRET_PROPERTY = new RegoString("secret");
  private static final RegoObject BLANK_OBJECT = new RegoObject();
  // Auto-register RegoValueModule (and any other Jackson modules on the classpath) via SPI so
  // RegoObject (de)serialization works without the AST types carrying Jackson annotations.
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper().findAndRegisterModules();

  static {
    Security.addProvider(new BouncyCastleProvider());
    JSON_MAPPER.enable(
        com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
  }

  @Override
  public Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    TokenBuiltins instance = new TokenBuiltins();
    return Map.ofEntries(
        Map.entry("io.jwt.decode", instance::decode),
        Map.entry("io.jwt.decode_verify", instance::decodeVerify),
        Map.entry("io.jwt.verify_ps256", instance::verifyPS256),
        Map.entry("io.jwt.verify_ps384", instance::verifyPS384),
        Map.entry("io.jwt.verify_ps512", instance::verifyPS512),
        Map.entry("io.jwt.verify_rs256", instance::verifyRS256),
        Map.entry("io.jwt.verify_rs384", instance::verifyRS384),
        Map.entry("io.jwt.verify_rs512", instance::verifyRS512),
        Map.entry("io.jwt.verify_hs256", instance::verifyHS256),
        Map.entry("io.jwt.verify_hs384", instance::verifyHS384),
        Map.entry("io.jwt.verify_hs512", instance::verifyHS512),
        Map.entry("io.jwt.verify_es256", instance::verifyES256),
        Map.entry("io.jwt.verify_es384", instance::verifyES384),
        Map.entry("io.jwt.verify_es512", instance::verifyES512),
        Map.entry("io.jwt.encode_sign", instance::encodeSign),
        Map.entry("io.jwt.encode_sign_raw", instance::encodeSignRaw));
  }

  private static PrivateKey parsePrivateKey(String key) throws IOException {
    // Check if it's a JWK (JSON format)
    if (key.trim().startsWith("{")) {
      try {
        return parsePrivateJWK(key);
      } catch (Exception e) {
        throw new IllegalArgumentException("Failed to parse private JWK: " + e.getMessage(), e);
      }
    }

    // Otherwise parse as PEM
    PEMParser pemParser = new PEMParser(new StringReader(key));
    Object pemObject = pemParser.readObject();
    pemParser.close();

    // Convert the Bouncy Castle object to a standard JCA PrivateKey
    JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
    java.security.PrivateKey privateKey;

    if (pemObject instanceof org.bouncycastle.openssl.PEMKeyPair) {
      org.bouncycastle.openssl.PEMKeyPair keyPair = (org.bouncycastle.openssl.PEMKeyPair) pemObject;
      privateKey = converter.getPrivateKey(keyPair.getPrivateKeyInfo());
    } else if (pemObject instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
      privateKey = converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) pemObject);
    } else {
      throw new IllegalArgumentException(
          "Unsupported PEM object type for private key: "
              + (pemObject != null ? pemObject.getClass().getName() : "null"));
    }
    return privateKey;
  }

  private static PrivateKey parsePrivateJWK(String jwkJson) throws ParseException {
    try {
      JWK jwk = JWK.parse(jwkJson);
      if (jwk instanceof RSAKey) {
        return ((RSAKey) jwk).toPrivateKey();
      } else if (jwk instanceof ECKey) {
        return ((ECKey) jwk).toPrivateKey();
      } else {
        throw new BuiltinError("Unsupported JWK key type for private key: " + jwk.getKeyType());
      }
    } catch (JOSEException e) {
      throw new BuiltinError(e.getMessage());
    }
  }

  private static String getValidPayload(JWSHeader header, RegoString payloadJson) {
    JOSEObjectType type = header.getType();

    String payload = payloadJson.getValue();
    // If typ is JWT (or null, which defaults to JWT), validate payload is JSON
    if (type == null || "JWT".equalsIgnoreCase(type.toString())) {

      if (payload == null || payload.trim().isEmpty()) {
        throw new BuiltinError("io.jwt.encode_sign_raw: type is JWT but payload is not JSON");
      }

      try {
        JSON_MAPPER.readTree(payload);
      } catch (IOException e) {
        throw new BuiltinError("io.jwt.encode_sign_raw: type is JWT but payload is not JSON");
      }
    } else {
      if (payload == null) {
        payload = "";
      }
    }
    return payload;
  }

  private static JWSVerifier createWeakKeyMACVerifier(byte[] secret, String alg) {
    String javaAlg = alg.replace("HS", "HmacSHA");
    return new JWSVerifier() {
      @Override
      public boolean verify(
          JWSHeader header, byte[] signingInput, com.nimbusds.jose.util.Base64URL signature)
          throws JOSEException {
        try {
          javax.crypto.Mac mac = javax.crypto.Mac.getInstance(javaAlg);
          javax.crypto.spec.SecretKeySpec secretKey =
              new javax.crypto.spec.SecretKeySpec(secret, javaAlg);
          mac.init(secretKey);
          byte[] expectedSignature = mac.doFinal(signingInput);
          byte[] actualSignature = signature.decode();
          return java.security.MessageDigest.isEqual(expectedSignature, actualSignature);
        } catch (Exception e) {
          throw new JOSEException("HMAC verification failed: " + e.getMessage(), e);
        }
      }

      @Override
      public java.util.Set<JWSAlgorithm> supportedJWSAlgorithms() {
        return java.util.Set.of(JWSAlgorithm.parse(alg));
      }

      @Override
      public com.nimbusds.jose.jca.JCAContext getJCAContext() {
        return new com.nimbusds.jose.jca.JCAContext();
      }
    };
  }

  private static PublicKey getPublicKey(String cert) throws IOException {
    // Check if it's a JWK (JSON format)
    if (cert.trim().startsWith("{")) {
      try {
        return parseJWK(cert);
      } catch (Exception e) {
        throw new BuiltinError("failed to parse a JWK key (set): " + e.getMessage());
      }
    }

    // OPA is throwing an error if there is something after the certificate,
    // bouncycastle handles this with grace, so we need to do an additional check
    // so we can match what OPA is doing

    String trimmedCert = cert.trim();
    boolean endsWithValidPemLabel =
        trimmedCert.endsWith("-----END CERTIFICATE-----")
            || trimmedCert.endsWith("-----END PUBLIC KEY-----")
            || trimmedCert.endsWith("-----END RSA PUBLIC KEY-----")
            || trimmedCert.endsWith("-----END EC PUBLIC KEY-----")
            || trimmedCert.endsWith("-----END RSA PRIVATE KEY-----")
            || trimmedCert.endsWith("-----END EC PRIVATE KEY-----")
            || trimmedCert.endsWith("-----END PRIVATE KEY-----");

    if (!endsWithValidPemLabel) {
      // If the cert ends with a PEM-like END marker (unrecognized block type),
      // the key cannot be extracted. Otherwise there is extra data after the block.
      boolean endsWithUnknownPemLabel =
          trimmedCert.endsWith("-----") && trimmedCert.lastIndexOf("-----END ") >= 0;
      if (endsWithUnknownPemLabel) {
        throw new BuiltinError("failed to extract a Key from the PEM certificate");
      }
      throw new BuiltinError("extra data after a PEM certificate block");
    }

    // Otherwise parse as PEM
    PEMParser pemParser = new PEMParser(new StringReader(cert));
    Object pemObject;
    try {
      pemObject = pemParser.readObject();
    } catch (IOException e) {
      throw new BuiltinError("failed to parse a PEM certificate");
    } finally {
      try { pemParser.close(); } catch (IOException ignored) {}
    }

    if (pemObject == null) {
      throw new BuiltinError("failed to parse a PEM certificate");
    }

    // Convert the Bouncy Castle object to a standard JCA PublicKey
    JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
    PublicKey publicKey;

    if (pemObject instanceof SubjectPublicKeyInfo) {
      publicKey = converter.getPublicKey((SubjectPublicKeyInfo) pemObject);
    } else if (pemObject instanceof org.bouncycastle.cert.X509CertificateHolder) {
      org.bouncycastle.cert.X509CertificateHolder certHolder =
          (org.bouncycastle.cert.X509CertificateHolder) pemObject;
      publicKey = converter.getPublicKey(certHolder.getSubjectPublicKeyInfo());
    } else if (pemObject instanceof org.bouncycastle.openssl.PEMKeyPair) {
      org.bouncycastle.openssl.PEMKeyPair keyPair = (org.bouncycastle.openssl.PEMKeyPair) pemObject;
      publicKey = converter.getPublicKey(keyPair.getPublicKeyInfo());
    } else {
      throw new BuiltinError("Unsupported PEM object type: " + pemObject.getClass().getName());
    }
    return publicKey;
  }

  private static PublicKey parseJWK(String jwkJson) throws ParseException {
    try {
      // First try to parse as a single JWK
      try {
        JWK jwk = JWK.parse(jwkJson);
        return extractPublicKey(jwk);
      } catch (ParseException e) {
        // If that fails, try to parse as a JWK Set
        JWKSet jwkSet = JWKSet.parse(jwkJson);
        // For now, return the first valid public key we find
        // In the future, we might need to match by kid or other criteria
        for (JWK jwk : jwkSet.getKeys()) {
          try {
            return extractPublicKey(jwk);
          } catch (Exception ignore) {
            // Try next key
          }
        }
        throw new BuiltinError("No valid public key found in JWK set");
      }
    } catch (JOSEException e) {
      throw new BuiltinError(e.getMessage());
    }
  }

  private static PublicKey extractPublicKey(JWK jwk) throws JOSEException {
    if (jwk instanceof RSAKey) {
      return ((RSAKey) jwk).toPublicKey();
    } else if (jwk instanceof ECKey) {
      return ((ECKey) jwk).toPublicKey();
    } else {
      throw new BuiltinError("Unsupported JWK key type: " + jwk.getKeyType());
    }
  }

  private RegoBoolean _verifyECDSA(String jwt, String cert, boolean strict, String algorithm) {
    try {
      PublicKey publicKey = getPublicKey(cert);

      // Check if the key is actually an EC key
      if (!(publicKey instanceof ECPublicKey)) {
        if (strict) {
          throw new BuiltinError(
              "Expected EC public key but got " + publicKey.getClass().getSimpleName());
        }
        return RegoBoolean.FALSE;
      }

      SignedJWT signedJWT = SignedJWT.parse(jwt);
      if (!validateJWTHeader(signedJWT, algorithm)) {
        return RegoBoolean.FALSE;
      }
      JWSVerifier verifier = new ECDSAVerifier((ECPublicKey) publicKey);
      return RegoBoolean.of(signedJWT.verify(verifier));
    } catch (ParseException | IOException | JOSEException e) {
      if (strict) {
        throw new BuiltinError(e.getMessage());
      }
      return RegoBoolean.FALSE;
    }
  }

  private RegoBoolean _verifyEdDSA(String jwt, String cert, boolean strict) {
    try {
      PublicKey publicKey = getPublicKey(cert);
      SignedJWT signedJWT = SignedJWT.parse(jwt);
      if (!validateJWTHeader(signedJWT, "EdDSA")) {
        return RegoBoolean.FALSE;
      }
      byte[] sigBytes = signedJWT.getSignature().decode();
      int lastDot = jwt.lastIndexOf('.');
      byte[] signingInput = jwt.substring(0, lastDot).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
      java.security.Signature sig = java.security.Signature.getInstance("Ed25519", "BC");
      sig.initVerify(publicKey);
      sig.update(signingInput);
      return RegoBoolean.of(sig.verify(sigBytes));
    } catch (ParseException | IOException | java.security.GeneralSecurityException e) {
      if (strict) {
        throw new BuiltinError(e.getMessage());
      }
      return RegoBoolean.FALSE;
    }
  }

  private RegoBoolean _verifyRSASSA(String jwt, String cert, boolean strict, String algorithm) {
    try {
      preValidateJWT(jwt, false);
      // Check if cert is a JWK Set, try all keys
      if (cert.trim().startsWith("{")) {
        try {
          // Try to parse as JWK Set first
          JWKSet jwkSet = JWKSet.parse(cert);
          // Try each RSA key in the set
          for (JWK jwk : jwkSet.getKeys()) {
            if (jwk instanceof RSAKey) {
              try {
                PublicKey publicKey = ((RSAKey) jwk).toPublicKey();
                SignedJWT signedJWT = SignedJWT.parse(jwt);
                JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) publicKey);
                if (signedJWT.verify(verifier)) {
                  return validateJWTHeader(signedJWT, algorithm)
                      ? RegoBoolean.TRUE
                      : RegoBoolean.FALSE;
                }
              } catch (Exception ignore) {
                // Try next key
              }
            }
          }
          return RegoBoolean.FALSE;
        } catch (ParseException e) {
          // Not a JWK Set, try as single JWK or fall through to PEM
        }
      }

      // Fall back to original behavior for single key
      PublicKey publicKey = getPublicKey(cert);

      // Check if the key is actually an RSA key
      if (!(publicKey instanceof RSAPublicKey)) {
        if (strict) {
          throw new BuiltinError(
              "Expected RSA public key but got " + publicKey.getClass().getSimpleName());
        }
        return RegoBoolean.FALSE;
      }

      SignedJWT signedJWT = SignedJWT.parse(jwt);
      if (!validateJWTHeader(signedJWT, algorithm)) {
        return RegoBoolean.FALSE;
      }
      JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) publicKey);
      return RegoBoolean.of(signedJWT.verify(verifier));
    } catch (BuiltinError be) {
      if (strict) throw be;
      return RegoBoolean.FALSE;
    } catch (ParseException | JOSEException | IOException e) {
      if (strict) {
        throw new BuiltinError(e.getMessage());
      }
      return RegoBoolean.FALSE;
    }
  }

  private RegoBoolean _verifyHMAC(String jwt, String secret, boolean strict, String algorithm) {
    preValidateJWT(jwt, false);
    try {
      if (secret == null) {
        return RegoBoolean.FALSE;
      }

      SignedJWT signedJWT = SignedJWT.parse(jwt);
      if (!validateJWTHeader(signedJWT, algorithm)) {
        return RegoBoolean.FALSE;
      }
      JWSVerifier verifier;
      try {
        verifier = new MACVerifier(secret.getBytes());
      } catch (com.nimbusds.jose.KeyLengthException e) {
        // Nimbus enforces minimum 256-bit key length for HMAC, but OPA allows weak keys
        // Create a custom verifier that bypasses this check
        verifier =
            createWeakKeyMACVerifier(
                secret.getBytes(), signedJWT.getHeader().getAlgorithm().getName());
      }
      return RegoBoolean.of(signedJWT.verify(verifier));
    } catch (ParseException | JOSEException e) {
      if (strict) {
        throw new BuiltinError(e.getMessage());
      }
      return RegoBoolean.FALSE;
    }
  }

  @OpaBuiltin(
      name = "io.jwt.encode_sign_raw",
      description = "Encodes and optionally signs a JSON Web Token.",
      args = {
        @OpaType(type = "string", name = "headers", description = "JWS Protected Header"),
        @OpaType(type = "string", name = "payload", description = "JWS Payload"),
        @OpaType(type = "string", name = "key", description = "JSON Web Key (RFC7517)")
      },
      result = @OpaType(type = "string", name = "output", description = "signed JWT"),
      nondeterministic = true)
  public RegoString encodeSignRaw(EvaluationContext ctx, RegoValue[] args) {
    // Check cache first
    if (ctx.getNdBuiltinCache() != null) {
      RegoValue cachedValue = ctx.getNdBuiltinCache().get("io.jwt.encode_sign_raw", args);
      if (cachedValue != null) {
        return (RegoString) cachedValue;
      }
    }

    RegoString headersJson = getArg(args, 0, RegoString.class);
    RegoString payloadJson = getArg(args, 1, RegoString.class);
    RegoString keyJson = getArg(args, 2, RegoString.class);

    // Check if typ is JWT, then payload must be valid JSON
    try {
      if (headersJson.getValue().isEmpty()) {
        throw new BuiltinError("io.jwt.encode_sign_raw: missing or invalid 'alg' header: cannot parse JSON: cannot parse empty string");
      }
      if (!headersJson.getValue().trim().startsWith("{")) {
        throw new BuiltinError("io.jwt.encode_sign_raw: missing or invalid 'alg' header: jwsbb: header \"alg\" not found");
      }
      JWSHeader header = JWSHeader.parse(headersJson.getValue());
      String payload = getValidPayload(header, payloadJson);
      RegoString result = encodeAndSign(headersJson.getValue(), payload, keyJson.getValue());

      // Cache and record this nondeterministic value for decision logging
      if (ctx.getNdBuiltinCache() != null) {
        ctx.getNdBuiltinCache().put("io.jwt.encode_sign_raw", args, result);
      }
      ctx.recordNdCacheValue("io.jwt.encode_sign_raw", args, result);

      return result;
    } catch (ParseException e) {
      String rawMsg = e.getMessage();
      String msg;
      if ("Invalid JSON object".equals(rawMsg)) {
        msg = "cannot parse JSON";
      } else if (rawMsg != null && rawMsg.contains("alg")) {
        msg = "jwsbb: header \"alg\" not found";
      } else {
        msg = rawMsg;
      }
      throw new BuiltinError("io.jwt.encode_sign_raw: missing or invalid 'alg' header: " + msg);
    }
  }

  @OpaBuiltin(
      name = "io.jwt.encode_sign",
      description =
          "Encodes and optionally signs a JSON Web Token. Inputs are taken as objects, not encoded strings (see `io.jwt.encode_sign_raw`).",
      args = {
        @OpaType(type = "object", name = "headers", description = "JWS Protected Header"),
        @OpaType(type = "object", name = "payload", description = "JWS Payload"),
        @OpaType(type = "object", name = "key", description = "JSON Web Key (RFC7517)")
      },
      result = @OpaType(type = "string", name = "output", description = "signed JWT"),
      nondeterministic = true)
  public RegoString encodeSign(EvaluationContext ctx, RegoValue[] args) {
    // Check cache first
    if (ctx.getNdBuiltinCache() != null) {
      RegoValue cachedValue = ctx.getNdBuiltinCache().get("io.jwt.encode_sign", args);
      if (cachedValue != null) {
        return (RegoString) cachedValue;
      }
    }

    RegoObject headers = getArg(args, 0, RegoObject.class);
    RegoObject payload = getArg(args, 1, RegoObject.class);
    RegoObject key = getArg(args, 2, RegoObject.class);

    try {
      String headersJson = JSON_MAPPER.writeValueAsString(headers);
      String payloadJson = JSON_MAPPER.writeValueAsString(payload);
      String keyJson = getKeyData(key);

      RegoString result = encodeAndSign(headersJson, payloadJson, keyJson);

      // Cache and record this nondeterministic value for decision logging
      if (ctx.getNdBuiltinCache() != null) {
        ctx.getNdBuiltinCache().put("io.jwt.encode_sign", args, result);
      }
      ctx.recordNdCacheValue("io.jwt.encode_sign", args, result);

      return result;

    } catch (Exception e) {
      throw new BuiltinError("io.jwt.encode_sign: failed to encode JWT - " + e.getMessage());
    }
  }

  private RegoString encodeAndSign(String headersJson, String payloadJson, String keyJson) {
    try {
      JWSHeader header = JWSHeader.parse(headersJson);

      // Validate exp and nbf claims before processing
      validateExpAndNbfClaims(payloadJson);

      // Create payload - could be empty, JSON object, or string
      Payload payload;
      // Check if it's a valid JSON object
      try {
        JWTClaimsSet.parse(payloadJson);
        // It's valid JSON, use it as-is
        payload = new Payload(payloadJson);
      } catch (ParseException e) {
        // Not valid JSON object, treat as string payload
        payload = new Payload(payloadJson);
      }

      JWSObject jwsObject = new JWSObject(header, payload);

      if (header.getAlgorithm() == null) {
        throw new BuiltinError("missing or invalid 'alg' header: jwsbb: header \"alg\" not found");
      }
      String alg = header.getAlgorithm().getName();

      JWSSigner signer;
      if (alg.startsWith("RS") || alg.startsWith("PS")) {
        signer = new RSASSASigner(parsePrivateKey(keyJson));
      } else if (alg.startsWith("HS")) {
        byte[] secretBytes = keyJson.getBytes();
        try {
          signer = new MACSigner(secretBytes);
        } catch (com.nimbusds.jose.KeyLengthException e) {
          // Nimbus enforces minimum 256-bit key length for HMAC, but OPA allows weak keys
          // Create a custom signer that bypasses this check
          signer = createWeakKeyMACSigner(secretBytes, alg);
        }
      } else if (alg.startsWith("ES")) {
        signer = new ECDSASigner((java.security.interfaces.ECPrivateKey) parsePrivateKey(keyJson));
      } else if (alg.equals("EdDSA")) {
        signer = createEdDSASigner(keyJson);
      } else {
        throw new BuiltinError("unknown JWS algorithm: " + alg);
      }

      jwsObject.sign(signer);
      return new RegoString(jwsObject.serialize());

    } catch (Exception e) {
      throw new BuiltinError("io.jwt.encode_sign: failed to encode JWT - " + e.getMessage());
    }
  }

  private void validateExpAndNbfClaims(String payloadJson) {
    if (payloadJson == null || payloadJson.trim().isEmpty()) {
      return;
    }

    try {
      com.fasterxml.jackson.databind.JsonNode root = JSON_MAPPER.readTree(payloadJson);

      // Check exp claim
      if (root.has("exp")) {
        com.fasterxml.jackson.databind.JsonNode expNode = root.get("exp");
        if (expNode.isNull()) {
          throw new BuiltinError("exp value must be a number");
        }
        if (!expNode.isNumber()) {
          throw new BuiltinError("exp value must be a number");
        }
      }

      // Check nbf claim
      if (root.has("nbf")) {
        com.fasterxml.jackson.databind.JsonNode nbfNode = root.get("nbf");
        if (nbfNode.isNull()) {
          throw new BuiltinError("nbf value must be a number");
        }
        if (!nbfNode.isNumber()) {
          throw new BuiltinError("nbf value must be a number");
        }
      }
    } catch (JsonProcessingException ignore) {
      // If it's not valid JSON, we'll let it through - it will be treated as a string payload
    }
  }

  private JWSSigner createWeakKeyMACSigner(byte[] secret, String alg) {
    String javaAlg = alg.replace("HS", "HmacSHA");
    return new JWSSigner() {
      @Override
      public com.nimbusds.jose.util.Base64URL sign(JWSHeader header, byte[] signingInput)
          throws JOSEException {
        try {
          javax.crypto.Mac mac = javax.crypto.Mac.getInstance(javaAlg);
          javax.crypto.spec.SecretKeySpec secretKey =
              new javax.crypto.spec.SecretKeySpec(secret, javaAlg);
          mac.init(secretKey);
          return com.nimbusds.jose.util.Base64URL.encode(mac.doFinal(signingInput));
        } catch (Exception e) {
          throw new JOSEException("HMAC signing failed: " + e.getMessage(), e);
        }
      }

      @Override
      public java.util.Set<JWSAlgorithm> supportedJWSAlgorithms() {
        return java.util.Set.of(JWSAlgorithm.parse(alg));
      }

      @Override
      public com.nimbusds.jose.jca.JCAContext getJCAContext() {
        return new com.nimbusds.jose.jca.JCAContext();
      }
    };
  }

  private JWSSigner createEdDSASigner(String keyJson) {
    try {
      com.fasterxml.jackson.databind.JsonNode jwkNode = JSON_MAPPER.readTree(keyJson);
      if (!jwkNode.has("d")) {
        throw new BuiltinError("EdDSA key must include private key (d parameter)");
      }
      byte[] dBytes = java.util.Base64.getUrlDecoder().decode(jwkNode.get("d").asText());
      String crv = jwkNode.has("crv") ? jwkNode.get("crv").asText() : "Ed25519";
      if (!"Ed25519".equals(crv)) {
        throw new BuiltinError("EdDSA: only Ed25519 curve is supported, got " + crv);
      }
      org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters privParams =
          new org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(dBytes, 0);
      return new JWSSigner() {
        @Override
        public com.nimbusds.jose.util.Base64URL sign(JWSHeader header, byte[] signingInput)
            throws JOSEException {
          try {
            org.bouncycastle.crypto.Signer bcSigner =
                new org.bouncycastle.crypto.signers.Ed25519Signer();
            bcSigner.init(true, privParams);
            bcSigner.update(signingInput, 0, signingInput.length);
            return com.nimbusds.jose.util.Base64URL.encode(bcSigner.generateSignature());
          } catch (Exception e) {
            throw new JOSEException("EdDSA signing failed: " + e.getMessage(), e);
          }
        }

        @Override
        public java.util.Set<JWSAlgorithm> supportedJWSAlgorithms() {
          return java.util.Set.of(JWSAlgorithm.EdDSA);
        }

        @Override
        public com.nimbusds.jose.jca.JCAContext getJCAContext() {
          return new com.nimbusds.jose.jca.JCAContext();
        }
      };
    } catch (BuiltinError be) {
      throw be;
    } catch (Exception e) {
      throw new BuiltinError("failed to create EdDSA signer: " + e.getMessage());
    }
  }

  private static int findFirstInvalidBase64Index(String str) {
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_')) {
        return i;
      }
    }
    return -1;
  }

  @OpaBuiltin(
      name = "io.jwt.decode_verify",
      description =
          "Verifies a JWT signature under parameterized constraints and decodes the claims if it is valid.\nSupports the following algorithms: HS256, HS384, HS512, RS256, RS384, RS512, ES256, ES384, ES512, PS256, PS384, PS512, and EdDSA.",
      args = {
        @OpaType(
            type = "string",
            name = "jwt",
            description =
                "JWT token whose signature is to be verified and whose claims are to be checked"),
        @OpaType(
            type = "object",
            name = "constraints",
            description = "claim verification constraints")
      },
      result =
          @OpaType(
              type = "array",
              name = "output",
              description =
                  "`[valid, header, payload]`:  if the input token is verified and meets the requirements of `constraints` then `valid` is `true`; `header` and `payload` are objects containing the JOSE header and the JWT claim set; otherwise, `valid` is `false`, `header` and `payload` are `{}`"),
      nondeterministic = true)
  public RegoArray decodeVerify(EvaluationContext ctx, RegoValue[] args) {
    // Check cache first
    if (ctx.getNdBuiltinCache() != null) {
      RegoValue cachedValue = ctx.getNdBuiltinCache().get("io.jwt.decode_verify", args);
      if (cachedValue != null) {
        return (RegoArray) cachedValue;
      }
    }

    RegoString jwt = getArg(args, 0, RegoString.class);
    RegoObject constraints = getArg(args, 1, RegoObject.class);

    String cert =
        constraints.hasProperty(CERT_PROPERTY)
            ? (String) constraints.getProperty(CERT_PROPERTY).nativeValue()
            : null;
    String secret =
        constraints.hasProperty(SECRET_PROPERTY)
            ? (String) constraints.getProperty(SECRET_PROPERTY).nativeValue()
            : null;

    RegoBoolean valid = verify(jwt.getValue(), cert, secret, ctx.isStrictBuiltinErrors());

    RegoArray result;
    if (valid.equals(RegoBoolean.TRUE)) {

      try {
        SignedJWT token = SignedJWT.parse(jwt.getValue());
        RegoValue[] parts = getDecodedArray(token, true);
        if (validateConstraints(
            constraints,
            (RegoObject) parts[1],
            (String) ((RegoObject) parts[0]).getProperty(ALG_PROPERTY).nativeValue(),
            ctx)) {
          result = new RegoArray(List.of(RegoBoolean.TRUE, parts[0], parts[1]));
        } else {
          result = new RegoArray(List.of(RegoBoolean.FALSE, BLANK_OBJECT, BLANK_OBJECT));
        }
      } catch (ParseException ignore) {
        result = new RegoArray(List.of(RegoBoolean.FALSE, BLANK_OBJECT, BLANK_OBJECT));
      }
    } else {
      result = new RegoArray(List.of(RegoBoolean.FALSE, BLANK_OBJECT, BLANK_OBJECT));
    }

    // Cache and record this nondeterministic value for decision logging
    if (ctx.getNdBuiltinCache() != null) {
      ctx.getNdBuiltinCache().put("io.jwt.decode_verify", args, result);
    }
    ctx.recordNdCacheValue("io.jwt.decode_verify", args, result);

    return result;
  }

  @OpaBuiltin(
      name = "io.jwt.verify_es256",
      description = "Verifies if a ES256 JWT signature is valid.",
      args = {
        @OpaType(
            type = "string",
            name = "jwt",
            description = "JWT token whose signature is to be verified"),
        @OpaType(
            type = "string",
            name = "certificate",
            description =
                "PEM encoded certificate, PEM encoded public key, or the JWK key (set) used to verify the signature")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if the signature is valid, `false` otherwise"))
  public RegoBoolean verifyES256(EvaluationContext ctx, RegoValue[] args) {
    RegoString jwt = getArg(args, 0, RegoString.class);
    RegoString cert = getArg(args, 1, RegoString.class);
    return _verifyECDSA(jwt.getValue(), cert.getValue(), ctx.isStrictBuiltinErrors(), "ES256");
  }

  @OpaBuiltin(
      name = "io.jwt.verify_es384",
      description = "Verifies if a ES384 JWT signature is valid.",
      args = {
        @OpaType(
            type = "string",
            name = "jwt",
            description = "JWT token whose signature is to be verified"),
        @OpaType(
            type = "string",
            name = "certificate",
            description =
                "PEM encoded certificate, PEM encoded public key, or the JWK key (set) used to verify the signature")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if the signature is valid, `false` otherwise"))
  public RegoBoolean verifyES384(EvaluationContext ctx, RegoValue[] args) {
    RegoString jwt = getArg(args, 0, RegoString.class);
    RegoString cert = getArg(args, 1, RegoString.class);
    return _verifyECDSA(jwt.getValue(), cert.getValue(), ctx.isStrictBuiltinErrors(), "ES384");
  }

  @OpaBuiltin(
      name = "io.jwt.verify_es512",
      description = "Verifies if a ES512 JWT signature is valid.",
      args = {
        @OpaType(
            type = "string",
            name = "jwt",
            description = "JWT token whose signature is to be verified"),
        @OpaType(
            type = "string",
            name = "certificate",
            description =
                "PEM encoded certificate, PEM encoded public key, or the JWK key (set) used to verify the signature")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if the signature is valid, `false` otherwise"))
  public RegoBoolean verifyES512(EvaluationContext ctx, RegoValue[] args) {
    RegoString jwt = getArg(args, 0, RegoString.class);
    RegoString cert = getArg(args, 1, RegoString.class);
    return _verifyECDSA(jwt.getValue(), cert.getValue(), ctx.isStrictBuiltinErrors(), "ES512");
  }

  @OpaBuiltin(
      name = "io.jwt.verify_rs256",
      description = "Verifies if a RS256 JWT signature is valid.",
      args = {
        @OpaType(
            type = "string",
            name = "jwt",
            description = "JWT token whose signature is to be verified"),
        @OpaType(
            type = "string",
            name = "certificate",
            description =
                "PEM encoded certificate, PEM encoded public key, or the JWK key (set) used to verify the signature")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if the signature is valid, `false` otherwise"))
  public RegoBoolean verifyRS256(EvaluationContext ctx, RegoValue[] args) {
    RegoString jwt = getArg(args, 0, RegoString.class);
    RegoString cert = getArg(args, 1, RegoString.class);
    return _verifyRSASSA(jwt.getValue(), cert.getValue(), ctx.isStrictBuiltinErrors(), "RS256");
  }

  @OpaBuiltin(
      name = "io.jwt.verify_rs384",
      description = "Verifies if a RS384 JWT signature is valid.",
      args = {
        @OpaType(
            type = "string",
            name = "jwt",
            description = "JWT token whose signature is to be verified"),
        @OpaType(
            type = "string",
            name = "certificate",
            description =
                "PEM encoded certificate, PEM encoded public key, or the JWK key (set) used to verify the signature")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if the signature is valid, `false` otherwise"))
  public RegoBoolean verifyRS384(EvaluationContext ctx, RegoValue[] args) {
    RegoString jwt = getArg(args, 0, RegoString.class);
    RegoString cert = getArg(args, 1, RegoString.class);
    return _verifyRSASSA(jwt.getValue(), cert.getValue(), ctx.isStrictBuiltinErrors(), "RS384");
  }

  @OpaBuiltin(
      name = "io.jwt.verify_rs512",
      description = "Verifies if a RS512 JWT signature is valid.",
      args = {
        @OpaType(
            type = "string",
            name = "jwt",
            description = "JWT token whose signature is to be verified"),
        @OpaType(
            type = "string",
            name = "certificate",
            description =
                "PEM encoded certificate, PEM encoded public key, or the JWK key (set) used to verify the signature")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if the signature is valid, `false` otherwise"))
  public RegoBoolean verifyRS512(EvaluationContext ctx, RegoValue[] args) {
    RegoString jwt = getArg(args, 0, RegoString.class);
    RegoString cert = getArg(args, 1, RegoString.class);
    return _verifyRSASSA(jwt.getValue(), cert.getValue(), ctx.isStrictBuiltinErrors(), "RS512");
  }

  @OpaBuiltin(
      name = "io.jwt.verify_ps256",
      description = "Verifies if a PS256 JWT signature is valid.",
      args = {
        @OpaType(
            type = "string",
            name = "jwt",
            description = "JWT token whose signature is to be verified"),
        @OpaType(
            type = "string",
            name = "certificate",
            description =
                "PEM encoded certificate, PEM encoded public key, or the JWK key (set) used to verify the signature")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if the signature is valid, `false` otherwise"))
  public RegoBoolean verifyPS256(EvaluationContext ctx, RegoValue[] args) {
    RegoString jwt = getArg(args, 0, RegoString.class);
    RegoString cert = getArg(args, 1, RegoString.class);
    return _verifyRSASSA(jwt.getValue(), cert.getValue(), ctx.isStrictBuiltinErrors(), "PS256");
  }

  @OpaBuiltin(
      name = "io.jwt.verify_ps384",
      description = "Verifies if a PS384 JWT signature is valid.",
      args = {
        @OpaType(
            type = "string",
            name = "jwt",
            description = "JWT token whose signature is to be verified"),
        @OpaType(
            type = "string",
            name = "certificate",
            description =
                "PEM encoded certificate, PEM encoded public key, or the JWK key (set) used to verify the signature")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if the signature is valid, `false` otherwise"))
  public RegoBoolean verifyPS384(EvaluationContext ctx, RegoValue[] args) {
    RegoString jwt = getArg(args, 0, RegoString.class);
    RegoString cert = getArg(args, 1, RegoString.class);
    return _verifyRSASSA(jwt.getValue(), cert.getValue(), ctx.isStrictBuiltinErrors(), "PS384");
  }

  @OpaBuiltin(
      name = "io.jwt.verify_ps512",
      description = "Verifies if a PS512 JWT signature is valid.",
      args = {
        @OpaType(
            type = "string",
            name = "jwt",
            description = "JWT token whose signature is to be verified"),
        @OpaType(
            type = "string",
            name = "certificate",
            description =
                "PEM encoded certificate, PEM encoded public key, or the JWK key (set) used to verify the signature")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if the signature is valid, `false` otherwise"))
  public RegoBoolean verifyPS512(EvaluationContext ctx, RegoValue[] args) {
    RegoString jwt = getArg(args, 0, RegoString.class);
    RegoString cert = getArg(args, 1, RegoString.class);
    return _verifyRSASSA(jwt.getValue(), cert.getValue(), ctx.isStrictBuiltinErrors(), "PS512");
  }

  @OpaBuiltin(
      name = "io.jwt.verify_hs256",
      description = "Verifies if a HS256 (HMAC-SHA256) JWT signature is valid.",
      args = {
        @OpaType(
            type = "string",
            name = "jwt",
            description = "JWT token whose signature is to be verified"),
        @OpaType(
            type = "string",
            name = "secret",
            description = "plain text secret used to verify the signature")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if the signature is valid, `false` otherwise"))
  public RegoBoolean verifyHS256(EvaluationContext ctx, RegoValue[] args) {
    RegoString jwt = getArg(args, 0, RegoString.class);
    RegoString secret = getArg(args, 1, RegoString.class);
    return _verifyHMAC(jwt.getValue(), secret.getValue(), ctx.isStrictBuiltinErrors(), "HS256");
  }

  @OpaBuiltin(
      name = "io.jwt.verify_hs384",
      description = "Verifies if a HS384 (HMAC-SHA384) JWT signature is valid.",
      args = {
        @OpaType(
            type = "string",
            name = "jwt",
            description = "JWT token whose signature is to be verified"),
        @OpaType(
            type = "string",
            name = "secret",
            description = "plain text secret used to verify the signature")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if the signature is valid, `false` otherwise"))
  public RegoBoolean verifyHS384(EvaluationContext ctx, RegoValue[] args) {
    RegoString jwt = getArg(args, 0, RegoString.class);
    RegoString secret = getArg(args, 1, RegoString.class);
    return _verifyHMAC(jwt.getValue(), secret.getValue(), ctx.isStrictBuiltinErrors(), "HS384");
  }

  @OpaBuiltin(
      name = "io.jwt.verify_hs512",
      description = "Verifies if a HS512 (HMAC-SHA512) JWT signature is valid.",
      args = {
        @OpaType(
            type = "string",
            name = "jwt",
            description = "JWT token whose signature is to be verified"),
        @OpaType(
            type = "string",
            name = "secret",
            description = "plain text secret used to verify the signature")
      },
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if the signature is valid, `false` otherwise"))
  public RegoBoolean verifyHS512(EvaluationContext ctx, RegoValue[] args) {
    RegoString jwt = (RegoString) args[0];
    RegoString secret = (RegoString) args[1];
    return _verifyHMAC(jwt.getValue(), secret.getValue(), ctx.isStrictBuiltinErrors(), "HS512");
  }

  @OpaBuiltin(
      name = "io.jwt.decode",
      description = "Decodes a JSON Web Token and outputs it as an object.",
      args = {@OpaType(type = "string", name = "jwt", description = "JWT token to decode")},
      result =
          @OpaType(
              type = "array",
              name = "output",
              description =
                  "`[header, payload, sig]`, where `header` and `payload` are objects; `sig` is the hexadecimal representation of the signature on the token."))
  public RegoArray decode(EvaluationContext ctx, RegoValue[] args) {
    try {
      RegoString jwt = getArg(args, 0, RegoString.class);
      preValidateJWT(jwt.getValue(), true);
      SignedJWT token = SignedJWT.parse(jwt.getValue());
      RegoValue[] parts = getDecodedArray(token, true);
      return new RegoArray(List.of(parts));
    } catch (ParseException e) {
      throw new BuiltinError("io.jwt.decode: failed to parse JWT - " + e.getMessage());
    }
  }

  /**
   * Validates the structure of a JWT string before parsing, matching Go's OPA behavior.
   *
   * @param jwtStr the JWT string to validate
   * @param validateEncoding whether to validate base64url encoding and JWE
   */
  private void preValidateJWT(String jwtStr, boolean validateEncoding) {
    String[] sections = jwtStr.split("\\.", -1);
    if (sections.length == 1) {
      throw new BuiltinError("encoded JWT had no period separators");
    }
    if (sections.length != 3) {
      throw new BuiltinError("encoded JWT must have 3 sections, found " + sections.length);
    }
    if (!validateEncoding) {
      return;
    }
    String[] sectionNames = {"JWT header", "JWT payload", "JWT signature"};
    for (int s = 0; s < 3; s++) {
      String section = sections[s];
      for (int i = 0; i < section.length(); i++) {
        char c = section.charAt(i);
        if (!isBase64UrlChar(c)) {
          throw new BuiltinError(
              sectionNames[s] + " had invalid encoding: illegal base64 data at input byte " + i);
        }
      }
    }
    // Check for JWE (header contains "enc" field)
    try {
      String headerJson = new String(
          java.util.Base64.getUrlDecoder().decode(padBase64(sections[0])),
          java.nio.charset.StandardCharsets.UTF_8);
      @SuppressWarnings("unchecked")
      Map<String, Object> headerMap = JSON_MAPPER.readValue(headerJson, Map.class);
      if (headerMap.containsKey("enc")) {
        throw new BuiltinError("JWT is a JWE object, which is not supported");
      }
    } catch (BuiltinError e) {
      throw e;
    } catch (Exception e) {
      // If we can't parse the header as JSON, let SignedJWT.parse handle it
    }
  }

  private static boolean isBase64UrlChar(char c) {
    return (c >= 'A' && c <= 'Z')
        || (c >= 'a' && c <= 'z')
        || (c >= '0' && c <= '9')
        || c == '-'
        || c == '_'
        || c == '=';
  }

  private static String padBase64(String base64url) {
    int pad = 4 - (base64url.length() % 4);
    if (pad == 4) return base64url;
    return base64url + "=".repeat(pad);
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder hexString = new StringBuilder(2 * bytes.length);
    for (byte b : bytes) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }

  private RegoBoolean verify(String jwt, String cert, String secret, boolean strict) {
    preValidateJWT(jwt, false);
    try {
      SignedJWT token = SignedJWT.parse(jwt);
      String algorithm = token.getHeader().getAlgorithm().getName();

      switch (algorithm) {
        case "RS256":
        case "RS384":
        case "RS512":
        case "PS256":
        case "PS384":
        case "PS512":
          return _verifyRSASSA(jwt, cert, strict, algorithm);
        case "HS256":
        case "HS384":
        case "HS512":
          return _verifyHMAC(jwt, secret, strict, algorithm);
        case "ES256":
        case "ES384":
        case "ES512":
          return _verifyECDSA(jwt, cert, strict, algorithm);
        case "EdDSA":
          return _verifyEdDSA(jwt, cert, strict);
        default:
          throw new BuiltinError("Unsupported algorithm: " + algorithm)
              .withContext("alg", algorithm);
      }
    } catch (ParseException e) {
      return RegoBoolean.FALSE;
    }
  }

  private RegoValue[] getDecodedArray(SignedJWT token, boolean validateSignature) {
    try {
      // Get header
      String headerJson = token.getHeader().toString();
      RegoObject header = JSON_MAPPER.readValue(headerJson, RegoObject.class);

      // Get payload - could be an object, a primitive value, or a nested JWT
      String payloadJson = token.getPayload().toString();

      RegoValue payload;
      try {
        // Try to parse as object first
        payload = JSON_MAPPER.readValue(payloadJson, RegoObject.class);
      } catch (Exception e) {
        // If that fails, test to see if the string is a JWT token
        try {
          token = SignedJWT.parse(payloadJson);
          return getDecodedArray(token, false);
        } catch (ParseException ignore) {
          // Then it's some other issue
          throw new BuiltinError(e.getMessage());
        }
      }

      // Nimbusdb is a little too good decoding signatures, so adding a check in to ensure proper errors
      // Validate signature contains only valid BASE64 URL characters (not strict decode which fails on padding)
      // Only validate on top-level JWT, not nested JWTs
      if (validateSignature) {
        String sigStr = token.getSignature().toString();
        if (!sigStr.matches("^[A-Za-z0-9_-]*$")) {
          throw new BuiltinError("JWT signature had invalid encoding: illegal base64 data at input byte " + findFirstInvalidBase64Index(sigStr));
        }
      }
      // Get signature as hex
      byte[] sigBytes = token.getSignature().decode();
      String sigHex = bytesToHex(sigBytes);
      RegoString sig = new RegoString(sigHex);

      return new RegoValue[] {header, payload, sig};
    } catch (Exception e) {
      throw new BuiltinError("io.jwt.decode: failed to decode JWT - " + e.getMessage());
    }
  }

  private boolean validateJWTHeader(SignedJWT signedJWT, String alg) {

    if (signedJWT.getHeader().getCustomParam("enc") != null) {
      throw new BuiltinError("JWT is a JWE object, which is not supported");
    }

    return signedJWT.getHeader().getAlgorithm().getName().equals(alg);
  }

  private boolean validateConstraints(
      RegoObject constraints, RegoObject payload, String alg, EvaluationContext ctx) {

    // Check aud (audience) - if present in either constraints or payload, must match
    RegoValue constraintAud = constraints.getProperty(AUD_PROPERTY);
    RegoValue payloadAud = payload.getProperty(AUD_PROPERTY);

    // If aud exists in either location (and is not null), both must exist and match
    boolean constraintHasAud = constraintAud != null && constraintAud != RegoNull.INSTANCE;
    boolean payloadHasAud = payloadAud != null && payloadAud != RegoNull.INSTANCE;

    if (constraintHasAud || payloadHasAud) {
      if (!constraintHasAud || !payloadHasAud) {
        return false;
      }

      // Check if aud values match - handle both string and array cases
      if (!audMatches(constraintAud, payloadAud)) {
        return false;
      }
    }

    // Check iss (issuer) - if present in constraints, must match payload
    RegoValue constraintIss = constraints.getProperty(ISS_PROPERTY);
    if (constraintIss != null && constraintIss != RegoNull.INSTANCE) {
      RegoValue payloadIss = payload.getProperty(ISS_PROPERTY);
      if (!constraintIss.equals(payloadIss)) {
        return false;
      }
    }

    // Check alg - if present in constraints, must match the header algorithm
    if (constraints.hasProperty(ALG_PROPERTY)) {
      if (!constraints.getProperty(ALG_PROPERTY).nativeValue().equals(alg)) {
        return false;
      }
    }

    // Get time from constraints or use current time from context
    long timeNanos;
    if (constraints.hasProperty(TIME_PROPERTY)) {
      timeNanos = ((Number) constraints.getProperty(TIME_PROPERTY).nativeValue()).longValue();
    } else {
      // getEvalStartTime() returns milliseconds, convert to nanoseconds
      timeNanos = ctx.getEvalStartTime() * 1_000_000L;
    }
    // Use double for time comparison to handle fractional seconds in JWT claims
    double timeSeconds = timeNanos / 1_000_000_000.0;

    // Check exp (expiration time) - JWT should not be expired
    RegoValue expValue = payload.getProperty(EXP_PROPERTY);
    if (expValue != null && expValue != RegoNull.INSTANCE) {
      double exp;
      try {
        Object expNative = expValue.nativeValue();
        if (expNative instanceof Number) {
          exp = ((Number) expNative).doubleValue();
        } else if (expNative instanceof String) {
          exp = Double.parseDouble((String) expNative);
        } else {
          throw new BuiltinError("exp value must be a number");
        }
      } catch (NumberFormatException e) {
        throw new BuiltinError("exp value must be a number");
      }

      if (timeSeconds >= exp) {
        return false; // Token has expired
      }
    }

    // Check nbf (not before) - JWT should not be used before this time
    RegoValue nbfValue = payload.getProperty(NBF_PROPERTY);
    if (nbfValue != null && nbfValue != RegoNull.INSTANCE) {
      double nbf;
      try {
        Object nbfNative = nbfValue.nativeValue();
        if (nbfNative instanceof Number) {
          nbf = ((Number) nbfNative).doubleValue();
        } else if (nbfNative instanceof String) {
          nbf = Double.parseDouble((String) nbfNative);
        } else {
          throw new BuiltinError("nbf value must be a number");
        }
      } catch (NumberFormatException e) {
        throw new BuiltinError("nbf value must be a number");
      }

      return !(timeSeconds < nbf); // Token not yet valid
    }

    return true;
  }

  /**
   * Check if audience values match. Handles cases where aud can be a string or array. - If both are
   * strings, they must be equal - If one is a string and the other is an array, the string must be
   * in the array - If both are arrays, they must be equal
   */
  private boolean audMatches(RegoValue constraintAud, RegoValue payloadAud) {
    // Both are the same type and equal
    if (constraintAud.equals(payloadAud)) {
      return true;
    }

    // Check if constraint is string and payload is array
    if (constraintAud instanceof RegoString && payloadAud instanceof RegoArray) {
      RegoArray payloadArray = (RegoArray) payloadAud;
      for (RegoValue item : payloadArray.getValue()) {
        if (constraintAud.equals(item)) {
          return true;
        }
      }
      return false;
    }

    // Check if constraint is array and payload is string
    if (constraintAud instanceof RegoArray && payloadAud instanceof RegoString) {
      RegoArray constraintArray = (RegoArray) constraintAud;
      for (RegoValue item : constraintArray.getValue()) {
        if (payloadAud.equals(item)) {
          return true;
        }
      }
      return false;
    }

    // Types don't match in a compatible way
    return false;
  }

  private String getKeyData(RegoObject key) throws Exception {
    // Check if key has a "cert" property (PEM format)
    if (key.hasProperty(CERT_PROPERTY)) {
      return (String) key.getProperty(CERT_PROPERTY).nativeValue();
    }

    // Otherwise, assume the key object itself is a JWK - convert to JSON string
    return JSON_MAPPER.writeValueAsString(key);
  }
}
