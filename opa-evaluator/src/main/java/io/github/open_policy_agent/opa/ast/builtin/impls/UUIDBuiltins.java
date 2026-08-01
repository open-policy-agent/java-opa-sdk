package io.github.open_policy_agent.opa.ast.builtin.impls;

import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaDynamic;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

public class UUIDBuiltins {

  private static final long UUID_EPOCH_OFFSET_100NS = 122192928000000000L;
  private static final String RFC4122 = "uuid.rfc4122";
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Pattern CANONICAL_UUID_PATTERN =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
  private static final Pattern COMPACT_UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");

  public static Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    UUIDBuiltins instance = new UUIDBuiltins();
    return Map.of(
        "uuid.parse", instance::parse,
        "uuid.rfc4122", instance::rfc4122);
  }

  @OpaBuiltin(
      name = "uuid.parse",
      description = "Parses a UUID string into its RFC 4122 metadata.",
      categories = {"uuid"},
      args = {@OpaType(type = "string", name = "uuid", description = "UUID string to parse")},
      result =
          @OpaType(
              type = "object",
              name = "result",
              description = "parsed UUID metadata",
              dynamic = @OpaDynamic(keyType = "string", valueType = "any")))
  public RegoValue parse(EvaluationContext ctx, RegoValue[] args) {
    String input = getArg(args, 0, RegoString.class).getValue();
    UUID uuid = parseUuid(input);
    if (uuid == null) {
      return null;
    }

    RegoObject result = new RegoObject();
    int version = uuid.version();
    result.setProperty("variant", new RegoString(variantName(uuid.variant())));
    result.setProperty("version", RegoInt32.of(version));

    if (version == 1 || version == 2) {
      byte[] bytes = toBytes(uuid);
      result.setProperty("clocksequence", RegoInt32.of(clockSequence(uuid)));
      result.setProperty("macvariables", new RegoString(macVariables(bytes[10])));
      result.setProperty("nodeid", new RegoString(nodeId(bytes)));
      result.setProperty("time", new RegoBigInt((timestamp(uuid) - UUID_EPOCH_OFFSET_100NS) * 100L));

      if (version == 2) {
        result.setProperty("domain", new RegoString(domain(bytes[9])));
        result.setProperty(
            "id", new RegoBigInt(Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 0, 4).getInt())));
      }
    }

    return result;
  }

  @OpaBuiltin(
      name = "uuid.rfc4122",
      description = "Returns a version 4 RFC 4122 UUID.",
      categories = {"uuid"},
      args = {
        @OpaType(
            type = "string",
            name = "key",
            description = "cache key for deterministic UUID generation during evaluation")
      },
      result = @OpaType(type = "string", name = "uuid", description = "RFC 4122 UUID"),
      nondeterministic = true)
  public RegoString rfc4122(EvaluationContext ctx, RegoValue[] args) {
    RegoString cachedValue = getEvaluationCacheValue(ctx, args);
    if (cachedValue != null) {
      return cachedValue;
    }

    getArg(args, 0, RegoString.class);
    RegoString result = new RegoString(randomVersion4Uuid());

    if (ctx != null) {
      ctx.recordNdCacheValue(RFC4122, args, result);
    }

    return result;
  }

  private UUID parseUuid(String input) {
    String normalized = normalize(input);
    if (normalized == null) {
      return null;
    }

    try {
      return UUID.fromString(normalized);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private String normalize(String input) {
    String value;
    switch (input.length()) {
      case 32:
        if (!COMPACT_UUID_PATTERN.matcher(input).matches()) {
          return null;
        }
        return input.substring(0, 8)
            + "-"
            + input.substring(8, 12)
            + "-"
            + input.substring(12, 16)
            + "-"
            + input.substring(16, 20)
            + "-"
            + input.substring(20);
      case 36:
        value = input;
        break;
      case 38:
        if (!input.startsWith("{") || !input.endsWith("}")) {
          return null;
        }
        value = input.substring(1, input.length() - 1);
        break;
      case 45:
        if (!input.startsWith("urn:uuid:")) {
          return null;
        }
        value = input.substring("urn:uuid:".length());
        break;
      default:
        return null;
    }
    return CANONICAL_UUID_PATTERN.matcher(value).matches() ? value : null;
  }

  private long timestamp(UUID uuid) {
    long most = uuid.getMostSignificantBits();
    long timeLow = (most >>> 32) & 0xffffffffL;
    long timeMid = (most >>> 16) & 0xffffL;
    long timeHigh = most & 0x0fffL;
    return (timeHigh << 48) | (timeMid << 32) | timeLow;
  }

  private int clockSequence(UUID uuid) {
    return (int) ((uuid.getLeastSignificantBits() >>> 48) & 0x3fffL);
  }

  private String variantName(int variant) {
    switch (variant) {
      case 0:
        return "Reserved";
      case 2:
        return "RFC4122";
      case 6:
        return "Microsoft";
      default:
        return "Future";
    }
  }

  private String domain(byte value) {
    switch (Byte.toUnsignedInt(value)) {
      case 0:
        return "Person";
      case 1:
        return "Group";
      case 2:
        return "Org";
      default:
        return "Domain" + Byte.toUnsignedInt(value);
    }
  }

  private String macVariables(byte value) {
    int bits = value & 0b11;
    switch (bits) {
      case 0b11:
        return "local:multicast";
      case 0b01:
        return "global:multicast";
      case 0b10:
        return "local:unicast";
      default:
        return "global:unicast";
    }
  }

  private String nodeId(byte[] bytes) {
    StringBuilder result = new StringBuilder(17);
    for (int i = 10; i < bytes.length; i++) {
      if (i > 10) {
        result.append("-");
      }
      result.append(String.format("%02x", Byte.toUnsignedInt(bytes[i])));
    }
    return result.toString();
  }

  private byte[] toBytes(UUID uuid) {
    ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
    buffer.putLong(uuid.getMostSignificantBits());
    buffer.putLong(uuid.getLeastSignificantBits());
    return buffer.array();
  }

  private RegoString getEvaluationCacheValue(EvaluationContext ctx, RegoValue[] args) {
    if (ctx == null) {
      return null;
    }
    for (EvaluationContext.CacheCall call :
        ctx.getNdCacheValues().getOrDefault(RFC4122, java.util.List.of())) {
      if (Arrays.equals(call.getArgs(), args) && call.getResult() instanceof RegoString value) {
        return value;
      }
    }
    return null;
  }

  private String randomVersion4Uuid() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x40);
    bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    return new UUID(buffer.getLong(), buffer.getLong()).toString();
  }
}
