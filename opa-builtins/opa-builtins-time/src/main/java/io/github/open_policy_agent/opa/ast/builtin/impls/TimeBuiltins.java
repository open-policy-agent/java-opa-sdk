package io.github.open_policy_agent.opa.ast.builtin.impls;

import static java.lang.Math.abs;
import static io.github.open_policy_agent.opa.ast.builtin.impls.utils.ArgHelper.getArg;

import java.math.BigDecimal;
import java.math.BigInteger;
import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoInt32;
import io.github.open_policy_agent.opa.ast.types.RegoNumber;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinError;
import io.github.open_policy_agent.opa.ast.builtin.BuiltinProvider;
import io.github.open_policy_agent.opa.ast.builtin.OpaBuiltin;
import io.github.open_policy_agent.opa.ast.builtin.OpaType;
import io.github.open_policy_agent.opa.ast.builtin.impls.utils.GoTimeLayoutConverter;
import io.github.open_policy_agent.opa.rego.EvaluationContext;

public class TimeBuiltins implements BuiltinProvider {
  @Override
  public Map<String, BiFunction<EvaluationContext, RegoValue[], RegoValue>> builtins() {
    TimeBuiltins instance = new TimeBuiltins();
    return Map.of(
        "time.weekday", instance::weekday,
        "time.parse_ns", instance::parseNs,
        "time.add_date", instance::addDate,
        "time.date", instance::date,
        "time.parse_rfc3339_ns", instance::parseRFC3339Ns,
        "time.clock", instance::clock,
        "time.now_ns", instance::nowNs,
        "time.diff", instance::diff,
        "time.parse_duration_ns", instance::parseDurationNs,
        "time.format", instance::format);
    }

  @OpaBuiltin(
      name = "time.format",
      description = "Returns the formatted timestamp for the nanoseconds since epoch.",
      args = {
        @OpaType(
            type = "any",
            name = "x",
            description =
                "a number representing the nanoseconds since the epoch (UTC); or a "
                    + "two-element array of the nanoseconds, and a timezone string; or a "
                    + "three-element array of ns, timezone string and a layout string or "
                    + "golang defined formatting constant (see golang supported time formats)")
      },
      result =
          @OpaType(
              type = "string",
              name = "formatted timestamp",
              description =
                  "the formatted timestamp represented for the nanoseconds since the epoch in the supplied timezone (or UTC)"))
  public RegoValue format(EvaluationContext ctx, RegoValue[] args) {
        ZonedDateTime x;
        String format = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX";
        if (args[0] instanceof RegoArray && args[0].length() == 3) {
            List<RegoValue> rArgs = ((RegoArray) args[0]).getValue();
            RegoArray ns = new RegoArray(rArgs.subList(0, 3));
            x = getZonedDateTime(ns);
            String goFormat = ((RegoString)rArgs.get(2)).getValue();
      format =
          GoTimeLayoutConverter.convert(goFormat)[
              0]; // Taking the first one as formatting will be able to use the pattern
        } else {
            x = getZonedDateTime(args[0]);
        }

        String formatted = DateTimeFormatter.ofPattern(format).format(x);

        return new RegoString(formatted);
    }

  @OpaBuiltin(
      name = "time.parse_duration_ns",
      description = "Returns the duration in nanoseconds represented by a string.",
      args = {
        @OpaType(
            type = "string",
            name = "duration",
            description =
                "a duration like \"3m\"; see the [Go `time` package documentation](https://golang.org/pkg/time/#ParseDuration) for more details")
      },
      result =
          @OpaType(type = "number", name = "ns", description = "the `duration` in nanoseconds"))
  public RegoValue parseDurationNs(EvaluationContext ctx, RegoValue[] args) {
        String duration = getArg(args, 0, RegoString.class).getValue();

        long ns;

        if (duration.endsWith("ns")) {
            duration = duration.substring(0, duration.length() - 2);
            ns = Long.parseLong(duration);
        } else if (duration.endsWith("us") || duration.endsWith("µs")) {
            duration = duration.substring(0, duration.length() - 2);
            ns = Long.parseLong(duration) * 1000;
        } else if (duration.endsWith("ms")) {
            duration = duration.substring(0, duration.length() - 2);
            ns = Long.parseLong(duration) * 1_000_000;
        } else if (duration.endsWith("s")) {
            duration = duration.substring(0, duration.length() - 1);
            ns = Long.parseLong(duration) * 1_000_000_000;
        } else if (duration.endsWith("m")) {
            duration = duration.substring(0, duration.length() - 1);
            ns = Long.parseLong(duration) * 1_000_000_000 * 60;
        } else if (duration.endsWith("h")) {
            duration = duration.substring(0, duration.length() - 1);
            ns = Long.parseLong(duration) * 1_000_000_000 * 60 * 60;
        } else {
      throw new BuiltinError("invalid duration format: " + duration);
        }
        return new RegoBigInt(ns);
    }

  @OpaBuiltin(
      name = "time.diff",
      description =
          "Returns the difference between two unix timestamps in nanoseconds (with optional timezone strings).",
      args = {
        @OpaType(
            type = "any",
            name = "ns1",
            description =
                "nanoseconds since the epoch; or a two-element array of the nanoseconds, and a timezone string"),
        @OpaType(
            type = "any",
            name = "ns2",
            description =
                "nanoseconds since the epoch; or a two-element array of the nanoseconds, and a timezone string")
      },
      result =
          @OpaType(
              type = "array",
              name = "output",
              description =
                  "difference between `ns1` and `ns2` (in their supplied timezones, if supplied, or UTC) as array of numbers: `[years, months, days, hours, minutes, seconds]`"))
  public RegoValue diff(EvaluationContext ctx, RegoValue[] args) {
    ZonedDateTime ns1 = getZonedDateTime(args[0]);
    ZonedDateTime ns2 = getZonedDateTime(args[1]);

    // Convert both to UTC to ensure we're comparing the same instant
    ZonedDateTime utc1 = ns1.withZoneSameInstant(ZoneOffset.UTC);
    ZonedDateTime utc2 = ns2.withZoneSameInstant(ZoneOffset.UTC);

    // Use Period for date-based calculations (years, months, days)
    Period period = Period.between(utc1.toLocalDate(), utc2.toLocalDate());
    Duration duration = Duration.between(utc1, utc2);

    RegoInt32 years = RegoInt32.of(abs(period.getYears()));
    RegoInt32 months = RegoInt32.of(abs(period.getMonths()));
    RegoInt32 days = RegoInt32.of(abs(period.getDays()));
    RegoInt32 hours = RegoInt32.of(abs(duration.toHoursPart()));
    RegoInt32 minutes = RegoInt32.of(abs(duration.toMinutesPart()));
    RegoInt32 seconds = RegoInt32.of(abs(duration.toSecondsPart()));

    return new RegoArray(Arrays.asList(years, months, days, hours, minutes, seconds));
  }

  @OpaBuiltin(
      name = "time.now_ns",
      description = "Returns the current time since epoch in nanoseconds.",
      result = @OpaType(type = "number", name = "now", description = "nanoseconds since epoch"),
      nondeterministic = true)
  public RegoValue nowNs(EvaluationContext ctx, RegoValue[] args) {
    // Check cache first
    if (ctx.getNdBuiltinCache() != null) {
      RegoValue cachedValue = ctx.getNdBuiltinCache().get("time.now_ns", args);
      if (cachedValue != null) {
        return cachedValue;
      }
    }

    RegoBigInt result = new RegoBigInt(ctx.getEvalStartTime() * 1_000_000L);

    // Cache and record this nondeterministic value for decision logging
    if (ctx.getNdBuiltinCache() != null) {
      ctx.getNdBuiltinCache().put("time.now_ns", args, result);
    }
    ctx.recordNdCacheValue("time.now_ns", args, result);

    return result;
  }

  @OpaBuiltin(
      name = "time.clock",
      description =
          "Returns the `[hour, minute, second]` of the day for the nanoseconds since epoch.",
      args = {
        @OpaType(
            type = "any",
            name = "x",
            description =
                "a number representing the nanoseconds since the epoch (UTC); or a two-element array of the nanoseconds, and a timezone string")
      },
      result =
          @OpaType(
              type = "array",
              name = "output",
              description =
                  "the `hour`, `minute` (0-59), and `second` (0-59) representing the time of day for the nanoseconds since epoch in the supplied timezone (or UTC)"))
  public RegoValue clock(EvaluationContext ctx, RegoValue[] args) {
        ZonedDateTime x = getZonedDateTime(args[0]);

    RegoInt32 hour = RegoInt32.of(x.getHour());
    RegoInt32 minute = RegoInt32.of(x.getMinute());
    RegoInt32 second = RegoInt32.of(x.getSecond());
        return new RegoArray(Arrays.asList(hour, minute, second));
    }

  @OpaBuiltin(
      name = "time.date",
      description = "Returns the `[year, month, day]` for the nanoseconds since epoch.",
      args = {
        @OpaType(
            type = "any",
            name = "x",
            description =
                "a number representing the nanoseconds since the epoch (UTC); or a two-element array of the nanoseconds, and a timezone string")
      },
      result =
          @OpaType(
              type = "array",
              name = "date",
              description = "an array of `year`, `month` (1-12), and `day` (1-31)"))
  public RegoValue date(EvaluationContext ctx, RegoValue[] args) {
        ZonedDateTime x = getZonedDateTime(args[0]);

    RegoInt32 year = RegoInt32.of(x.getYear());
    RegoInt32 month = RegoInt32.of(x.getMonthValue());
    RegoInt32 day = RegoInt32.of(x.getDayOfMonth());
        return new RegoArray(Arrays.asList(year, month, day));
    }

  @OpaBuiltin(
      name = "time.weekday",
      description =
          "Returns the day of the week (Monday, Tuesday, ...) for the nanoseconds since epoch.",
      args = {
        @OpaType(
            type = "any",
            name = "x",
            description =
                "a number representing the nanoseconds since the epoch (UTC); or a two-element array of the nanoseconds, and a timezone string")
      },
      result =
          @OpaType(
              type = "string",
              name = "day",
              description =
                  "the weekday represented by `ns` nanoseconds since the epoch in the supplied timezone (or UTC)"))
  public RegoValue weekday(EvaluationContext ctx, RegoValue[] args) {
        ZonedDateTime zoned = getZonedDateTime(args[0]);

        DayOfWeek dayOfWeek = zoned.getDayOfWeek();
        return new RegoString(dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
    }

  @OpaBuiltin(
      name = "time.add_date",
      description =
          "Returns the nanoseconds since epoch after adding years, months and days to "
              + "nanoseconds. Month & day values outside their usual ranges after the operation "
              + "and will be normalized - for example, October 32 would become November 1. "
              + "`undefined` if the result would be outside the valid time range that can fit "
              + "within an `int64`.",
      args = {
        @OpaType(type = "number", name = "ns", description = "nanoseconds since the epoch"),
        @OpaType(type = "number", name = "years", description = "number of years to add"),
        @OpaType(type = "number", name = "months", description = "number of months to add"),
        @OpaType(type = "number", name = "days", description = "number of days to add")
      },
      result =
          @OpaType(
              type = "number",
              name = "output",
              description =
                  "nanoseconds since the epoch representing the input time, with years, months and days added"))
  public RegoValue addDate(EvaluationContext ctx, RegoValue[] args) {
        try {
            RegoNumber nsArg = getArg(args, 0, RegoNumber.class);
            long ns = nsArg.getBigIntValue().longValueExact();
            int years = getArg(args, 1, RegoNumber.class).getBigIntValue().intValueExact();
            int months = getArg(args, 2, RegoNumber.class).getBigIntValue().intValueExact();
            int days = getArg(args, 3, RegoNumber.class).getBigIntValue().intValueExact();

            long seconds = ns / 1_000_000_000;
            long nanos = ns % 1_000_000_000;
            Instant originalInstant = Instant.ofEpochSecond(seconds, nanos);
            ZonedDateTime newDateTime = originalInstant.atZone(ZoneId.of("UTC")).plusYears(years).plusMonths(months).plusDays(days);

            BigInteger newEpochNano = BigInteger.valueOf(newDateTime.toInstant().getEpochSecond())
                    .multiply(BigInteger.valueOf(1_000_000_000L))
                    .add(BigInteger.valueOf(newDateTime.toInstant().getNano()));

            return bigIntToRegoNs(newEpochNano);
        } catch (ArithmeticException ignore) {
            throw new BuiltinError("time outside of valid range");
        }
    }

  @OpaBuiltin(
      name = "time.parse_rfc3339_ns",
      description =
          "Returns the time in nanoseconds parsed from the string in RFC3339 format. `undefined` if the result would be outside the valid time range that can fit within an `int64`.",
      args = {
        @OpaType(
            type = "string",
            name = "value",
            description = "input string to parse in RFC3339 format")
      },
      result =
          @OpaType(
              type = "number",
              name = "ns",
              description = "`value` in nanoseconds since epoch"))
  public RegoValue parseRFC3339Ns(EvaluationContext ctx, RegoValue[] args) {
        String value = getArg(args, 0, RegoString.class).getValue();
        RegoBigInt exact = parseNsFormat("RFC3339Nano", value);
        double d = exact.getValue().doubleValue();
        return new RegoBigInt(new BigDecimal(Double.toString(d)).toBigInteger());
    }

  @OpaBuiltin(
      name = "time.parse_ns",
      description =
          "Returns the time in nanoseconds parsed from the string in the given format. `undefined` if the result would be outside the valid time range that can fit within an `int64`.",
      args = {
        @OpaType(
            type = "string",
            name = "layout",
            description =
                "format used for parsing, see the [Go `time` package documentation](https://golang.org/pkg/time/#Parse) for more details"),
        @OpaType(
            type = "string",
            name = "value",
            description = "input to parse according to `layout`")
      },
      result =
          @OpaType(
              type = "number",
              name = "ns",
              description = "`value` in nanoseconds since epoch"))
  public RegoValue parseNs(EvaluationContext ctx, RegoValue[] args) {

        String goLayout = getArg(args, 0, RegoString.class).getValue();
        String value = getArg(args, 1, RegoString.class).getValue();

        // OPA serializes time.parse_ns results as IEEE 754 float64 (Go's json.Marshal behavior).
        // Round-trip through double so boundary values match OPA's output.
        RegoBigInt exact = parseNsFormat(goLayout, value);
        double d = exact.getValue().doubleValue();
        return new RegoBigInt(new BigDecimal(Double.toString(d)).toBigInteger());
    }

    private RegoBigInt parseNsFormat(String goLayout, String value) {
    String[] javaPattern = GoTimeLayoutConverter.convert(goLayout);
        TemporalAccessor parsed = null;
        for (String pattern : javaPattern) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                parsed = formatter.parse(value);
                break;
            } catch (DateTimeParseException ignored) {
            }
        }
        if (parsed == null) {
      throw new BuiltinError("invalid time format: " + goLayout);
        }
        if (parsed.isSupported(ChronoField.INSTANT_SECONDS)) {
            Instant instant = Instant.from(parsed);

            long epochSeconds = instant.getEpochSecond();
            int nanoOfSecond = instant.getNano();

            BigInteger epochNanoseconds = BigInteger.valueOf(epochSeconds)
                    .multiply(BigInteger.valueOf(1_000_000_000L))
                    .add(BigInteger.valueOf(nanoOfSecond));

            return bigIntToRegoNs(epochNanoseconds);
        } else {
            // Handle granularities that don't support seconds (e.g., date-only formats)
            LocalDateTime localDateTime;

            if (parsed.isSupported(ChronoField.HOUR_OF_DAY)) {
                // Has time components but not instant seconds
                LocalTime time = LocalTime.from(parsed);
                LocalDate date = LocalDate.from(parsed);
                localDateTime = LocalDateTime.of(date, time);
            } else if (parsed.isSupported(ChronoField.DAY_OF_MONTH)) {
                // Date-only format
                LocalDate date = LocalDate.from(parsed);
                localDateTime = date.atStartOfDay();
            } else if (parsed.isSupported(ChronoField.YEAR)) {
                // Year-only or partial date formats
                int year = parsed.get(ChronoField.YEAR);
                int month = parsed.isSupported(ChronoField.MONTH_OF_YEAR) ? parsed.get(ChronoField.MONTH_OF_YEAR) : 1;
                int day = parsed.isSupported(ChronoField.DAY_OF_MONTH) ? parsed.get(ChronoField.DAY_OF_MONTH) : 1;
                localDateTime = LocalDateTime.of(year, month, day, 0, 0, 0);
            } else {
        throw new BuiltinError("invalid time format: " + goLayout);
            }

            // Convert to instant using UTC timezone
            Instant instant = localDateTime.atZone(ZoneOffset.UTC).toInstant();
            long epochSeconds = instant.getEpochSecond();
            int nanoOfSecond = instant.getNano();

            BigInteger epochNanoseconds = BigInteger.valueOf(epochSeconds)
                    .multiply(BigInteger.valueOf(1_000_000_000L))
                    .add(BigInteger.valueOf(nanoOfSecond));

            return bigIntToRegoNs(epochNanoseconds);
        }
    }

    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private RegoBigInt bigIntToRegoNs(BigInteger epochNanoseconds) {
        if (epochNanoseconds.compareTo(LONG_MIN) < 0 || epochNanoseconds.compareTo(LONG_MAX) > 0) {
            throw new BuiltinError("time outside of valid range");
        }
        return new RegoBigInt(epochNanoseconds.longValue());
    }

    private ZonedDateTime zonedDateTimeFromNano(long nanoseconds, String timezone) {
        long epochSeconds = nanoseconds / 1_000_000_000L;
        int nanoAdjustment = (int) (nanoseconds % 1_000_000_000L);
        Instant instant = Instant.ofEpochSecond(epochSeconds, nanoAdjustment);

    ZoneId zoneId = ZoneId.of(timezone);
        return instant.atZone(zoneId);
    }

    private ZonedDateTime getZonedDateTime(RegoValue arg) {
        ZonedDateTime ns;
        try {
            if (arg instanceof RegoBigInt) {
                ns = zonedDateTimeFromNano(((RegoBigInt) arg).getValue().longValueExact(), "UTC");
            } else if (arg instanceof RegoArray) {
                String timezone = ((RegoString) ((RegoArray) arg).getValue().get(1)).getValue();
                ns = zonedDateTimeFromNano(
                        ((RegoBigInt) ((RegoArray) arg).getValue().get(0)).getValue().longValueExact(),
                        timezone.isEmpty() ? "UTC" : timezone
                );
            } else {
        throw new BuiltinError("invalid argument type: " + arg.getTypeName());
            }
        }catch (ArithmeticException ignore) {
            throw new BuiltinError("timestamp too big");
        }
        return ns;
    }

}
