package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.util.Base64;
import java.util.Map;
import java.util.function.BiFunction;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

public class EncodingBuiltins {

  private static final Base64.Encoder ENCODER = Base64.getEncoder();
  private static final Base64.Decoder DECODER = Base64.getDecoder();
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder();
  private static final Base64.Encoder URL_ENCODER_NO_PADDING =
      Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    EncodingBuiltins instance = new EncodingBuiltins();
    return Map.of(
        "base64.decode", instance::decode,
        "base64.encode", instance::encode,
        "base64.is_valid", instance::isValid,
        "base64url.encode", instance::urlEncode,
        "base64url.encode_no_pad", instance::urlEncodeNoPad,
        "base64url.decode", instance::urlDecode);
  }

  @OpaBuiltin(
      name = "base64.decode",
      description = "Deserializes the base64 encoded input string.",
      categories = {"encoding"},
      args = {@OpaType(type = "string", name = "x", description = "string to decode")},
      result = @OpaType(type = "string", name = "y", description = "base64 deserialization of `x`"))
  public RegoString decode(EvaluationContext ctx, RegoValue[] args) {
    RegoString x = getArg(args, 0, RegoString.class);

    return new RegoString(new String(DECODER.decode(x.getValue())));
  }

  @OpaBuiltin(
      name = "base64.encode",
      description = "Serializes the input string into base64 encoding.",
      categories = {"encoding"},
      args = {@OpaType(type = "string", name = "x", description = "string to encode")},
      result = @OpaType(type = "string", name = "y", description = "base64 serialization of `x`"))
  public RegoString encode(EvaluationContext ctx, RegoValue[] args) {
    RegoString x = getArg(args, 0, RegoString.class);

    return new RegoString(new String(ENCODER.encode(x.getValue().getBytes())));
  }

  @OpaBuiltin(
      name = "base64.is_valid",
      description = "Returns true if the input string is a valid base64 encoded string.",
      categories = {"encoding"},
      args = {@OpaType(type = "string", name = "x", description = "string to check")},
      result =
          @OpaType(
              type = "boolean",
              name = "result",
              description = "`true` if `x` is valid base64 encoded value, `false` otherwise"))
  public RegoBoolean isValid(EvaluationContext ctx, RegoValue[] args) {
    RegoString x = getArg(args, 0, RegoString.class);
    try {
      DECODER.decode(x.getValue());
      return RegoBoolean.TRUE;
    } catch (IllegalArgumentException e) {
      return RegoBoolean.FALSE;
    }
  }

  @OpaBuiltin(
      name = "base64url.decode",
      description = "Deserializes the base64url encoded input string.",
      categories = {"encoding"},
      args = {@OpaType(type = "string", name = "x", description = "string to decode")},
      result =
          @OpaType(type = "string", name = "y", description = "base64url deserialization of `x`"))
  public RegoString urlDecode(EvaluationContext ctx, RegoValue[] args) {
    RegoString x = getArg(args, 0, RegoString.class);
    String decoded = new String(URL_DECODER.decode(x.getValue()));
    return new RegoString(decoded);
  }

  @OpaBuiltin(
      name = "base64url.encode",
      description = "Serializes the input string into base64url encoding.",
      categories = {"encoding"},
      args = {@OpaType(type = "string", name = "x", description = "string to encode")},
      result =
          @OpaType(type = "string", name = "y", description = "base64url serialization of `x`"))
  public RegoString urlEncode(EvaluationContext ctx, RegoValue[] args) {
    RegoString x = getArg(args, 0, RegoString.class);
    String encoded = URL_ENCODER.encodeToString(x.getValue().getBytes());
    return new RegoString(encoded);
  }

  @OpaBuiltin(
      name = "base64url.encode_no_pad",
      description = "Serializes the input string into base64url encoding without padding.",
      categories = {"encoding"},
      args = {@OpaType(type = "string", name = "x", description = "string to encode")},
      result =
          @OpaType(type = "string", name = "y", description = "base64url serialization of `x`"))
  public RegoString urlEncodeNoPad(EvaluationContext ctx, RegoValue[] args) {
    RegoString x = getArg(args, 0, RegoString.class);
    String encoded = URL_ENCODER_NO_PADDING.encodeToString(x.getValue().getBytes());
    return new RegoString(encoded);
    }
}
