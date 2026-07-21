package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.BiFunction;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

public class HexBuiltins {

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    HexBuiltins instance = new HexBuiltins();
    // @formatter:off
    return Map.of(
        "hex.decode", instance::decode,
        "hex.encode", instance::encode);
    // @formatter:on
  }

  @OpaBuiltin(
      name = "hex.encode",
      description = "Serializes the input string using hex-encoding.",
      categories = {"encoding"},
      args = {@OpaType(name = "x", description = "string to encode")},
      result = @OpaType(name = "y", description = "serialization of `x` using hex-encoding"))
  public RegoString encode(EvaluationContext ctx, RegoValue[] args) {
    String input = getArg(args, 0, RegoString.class).getValue();
    StringBuilder hex = new StringBuilder();

    for (byte b : input.getBytes(StandardCharsets.UTF_8)) {
      hex.append(String.format("%02x", b));
    }

    return new RegoString(hex.toString());
  }

  @OpaBuiltin(
      name = "hex.decode",
      description = "Deserializes the hex-encoded input string.",
      categories = {"encoding"},
      args = {@OpaType(name = "x", description = "a hex-encoded string")},
      result = @OpaType(name = "y", description = "deserialized from `x`"))
  public RegoString decode(EvaluationContext ctx, RegoValue[] args) {
    String hexInput = getArg(args, 0, RegoString.class).getValue();

    // Validate all characters first (Go's hex.DecodeString checks chars before length)
    for (int i = 0; i < hexInput.length(); i++) {
      char c = hexInput.charAt(i);
      if (hexCharToInt(c) == -1) {
        throw new BuiltinError(String.format("invalid byte: U+%04X '%c'", (int) c, c));
      }
    }

    // Validate hex string length (must be even)
    if (hexInput.length() % 2 != 0) {
      throw new BuiltinError("encoding/hex: odd length hex string");
    }

    byte[] bytes = new byte[hexInput.length() / 2];

    for (int i = 0; i < hexInput.length(); i += 2) {
      int high = hexCharToInt(hexInput.charAt(i));
      int low = hexCharToInt(hexInput.charAt(i + 1));
      bytes[i / 2] = (byte) ((high << 4) | low);
    }

    return new RegoString(new String(bytes, StandardCharsets.UTF_8));
  }

  private int hexCharToInt(char c) {
    if (c >= '0' && c <= '9') {
      return c - '0';
    } else if (c >= 'a' && c <= 'f') {
      return c - 'a' + 10;
    } else if (c >= 'A' && c <= 'F') {
      return c - 'A' + 10;
    }
    return -1;
  }
}
