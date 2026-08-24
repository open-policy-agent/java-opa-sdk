package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.*;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ast.builtin.impls.utils.GoTimeLayoutConverter;

/**
 * Tests for GoTimeLayoutConverter using inputs that are valid in Go's time.Parse implementation.
 *
 * <p>Go reference time: Mon Jan 2 15:04:05 MST 2006
 */
public class GoTimeLayoutConverterTest {

  @Test
  public void testRFC822() {
    String[] patterns = GoTimeLayoutConverter.convert("RFC822");
    assertEquals(1, patterns.length);
    assertEquals("dd MMM yy HH:mm z", patterns[0]);

    // Test parsing a real RFC822 timestamp
    assertCanParse(patterns, "02 Jan 06 15:04 MST");
  }

  @Test
  public void testRFC822Z() {
    String[] patterns = GoTimeLayoutConverter.convert("RFC822Z");
    assertEquals(1, patterns.length);
    assertEquals("dd MMM yy HH:mm Z", patterns[0]);

    // Test parsing a real RFC822Z timestamp
    assertCanParse(patterns, "02 Jan 06 15:04 -0700");
  }

  @Test
  public void testRFC850() {
    String[] patterns = GoTimeLayoutConverter.convert("RFC850");
    assertEquals(2, patterns.length);
    assertEquals("EEEE, dd-MMM-yy HH:mm:ss z", patterns[0]);
    assertEquals("EEEE, dd-MMM-yy HH:mm:ss.SSSSSSSSS z", patterns[1]);

    // Test parsing without nanoseconds
    assertCanParse(patterns, "Monday, 02-Jan-06 15:04:05 MST");
  }

  @Test
  public void testRFC1123() {
    String[] patterns = GoTimeLayoutConverter.convert("RFC1123");
    assertEquals(2, patterns.length);
    assertEquals("E, dd MMM yy HH:mm:ss z", patterns[0]);
    assertEquals("E, dd MMM yy HH:mm:ss.SSSSSSSSS z", patterns[1]);

    // Test parsing a real RFC1123 timestamp
    assertCanParse(patterns, "Mon, 02 Jan 06 15:04:05 MST");
  }

  @Test
  public void testRFC1123Z() {
    String[] patterns = GoTimeLayoutConverter.convert("RFC1123Z");
    assertEquals(2, patterns.length);
    assertEquals("E, dd MMM yyyy HH:mm:ss Z", patterns[0]);
    assertEquals("E, dd MMM yyyy HH:mm:ss.SSSSSSSSS Z", patterns[1]);

    // Test parsing a real RFC1123Z timestamp
    assertCanParse(patterns, "Mon, 02 Jan 2006 15:04:05 -0700");
  }

  @Test
  public void testRFC3339() {
    String[] patterns = GoTimeLayoutConverter.convert("RFC3339");
    assertEquals(2, patterns.length);
    assertEquals("yyyy-MM-dd'T'HH:mm:ssXXX", patterns[0]);
    assertEquals("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX", patterns[1]);

    // Test parsing RFC3339 timestamps (with and without nanoseconds)
    assertCanParse(patterns, "2006-01-02T15:04:05Z");
    assertCanParse(patterns, "2006-01-02T15:04:05+07:00");
  }

  @Test
  public void testRFC3339Nano() {
    String[] patterns = GoTimeLayoutConverter.convert("RFC3339Nano");
    assertEquals(2, patterns.length);
    assertEquals("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX", patterns[0]);
    assertEquals("yyyy-MM-dd'T'HH:mm:ssXXX", patterns[1]);

    // Test parsing with nanoseconds
    assertCanParse(patterns, "2006-01-02T15:04:05.999999999Z");
    // Also test without nanoseconds (Go is forgiving)
    assertCanParse(patterns, "2006-01-02T15:04:05Z");
  }

  @Test
  public void testBasicDateLayout() {
    String[] patterns = GoTimeLayoutConverter.convert("2006-01-02");
    assertEquals(1, patterns.length);
    assertEquals("yyyy-MM-dd", patterns[0]);

    assertCanParse(patterns, "2023-12-25");
  }

  @Test
  public void testBasicTimeLayout() {
    String[] patterns = GoTimeLayoutConverter.convert("15:04:05");
    assertEquals(2, patterns.length);
    assertEquals("HH:mm:ss", patterns[0]);
    assertEquals("HH:mm:ss.SSSSSSSSS", patterns[1]);

    assertCanParse(patterns, "15:04:05");
  }

  @Test
  public void testDateTimeLayout() {
    String[] patterns = GoTimeLayoutConverter.convert("2006-01-02 15:04:05");
    assertEquals(2, patterns.length);
    assertEquals("yyyy-MM-dd HH:mm:ss", patterns[0]);
    assertEquals("yyyy-MM-dd HH:mm:ss.SSSSSSSSS", patterns[1]);

    assertCanParse(patterns, "2023-12-25 14:30:45");
  }

  @Test
  public void testDateTimeWithTimezone() {
    String[] patterns = GoTimeLayoutConverter.convert("2006-01-02 15:04:05 -07:00");
    assertEquals(2, patterns.length);
    assertEquals("yyyy-MM-dd HH:mm:ss XXX", patterns[0]);

    assertCanParse(patterns, "2023-12-25 14:30:45 -05:00");
  }

  @Test
  public void testYearFormats() {
    // Four-digit year
    String[] patterns1 = GoTimeLayoutConverter.convert("2006");
    assertEquals("yyyy", patterns1[0]);

    // Two-digit year
    String[] patterns2 = GoTimeLayoutConverter.convert("06");
    assertEquals("yy", patterns2[0]);
  }

  @Test
  public void testMonthFormats() {
    // Full month name
    String[] patterns1 = GoTimeLayoutConverter.convert("January");
    assertEquals("MMMM", patterns1[0]);

    // Abbreviated month name
    String[] patterns2 = GoTimeLayoutConverter.convert("Jan");
    assertEquals("MMM", patterns2[0]);

    // Two-digit month
    String[] patterns3 = GoTimeLayoutConverter.convert("01");
    assertEquals("MM", patterns3[0]);

    // Single-digit month (standalone)
    String[] patterns4 = GoTimeLayoutConverter.convert("1");
    assertEquals("M", patterns4[0]);
  }

  @Test
  public void testDayFormats() {
    // Two-digit day
    String[] patterns1 = GoTimeLayoutConverter.convert("02");
    assertEquals("dd", patterns1[0]);

    // Single-digit day (standalone)
    String[] patterns2 = GoTimeLayoutConverter.convert("2");
    assertEquals("d", patterns2[0]);

    // Space-padded day
    String[] patterns3 = GoTimeLayoutConverter.convert("_2");
    assertEquals(" d", patterns3[0]);
  }

  @Test
  public void testWeekdayFormats() {
    // Full weekday name
    String[] patterns1 = GoTimeLayoutConverter.convert("Monday");
    assertEquals("EEEE", patterns1[0]);

    // Abbreviated weekday name
    String[] patterns2 = GoTimeLayoutConverter.convert("Mon");
    assertEquals("EEE", patterns2[0]);
  }

  @Test
  public void testHourFormats() {
    // 24-hour format
    String[] patterns1 = GoTimeLayoutConverter.convert("15");
    assertTrue(patterns1[0].contains("HH"));

    // 12-hour format (two-digit)
    String[] patterns2 = GoTimeLayoutConverter.convert("03");
    assertTrue(patterns2[0].contains("hh"));

    // 12-hour format (single-digit, standalone)
    String[] patterns3 = GoTimeLayoutConverter.convert("3");
    assertEquals("h", patterns3[0]);
  }

  @Test
  public void testMinuteFormats() {
    // Two-digit minute
    String[] patterns1 = GoTimeLayoutConverter.convert("04");
    assertTrue(patterns1[0].contains("mm"));

    // Single-digit minute (standalone)
    String[] patterns2 = GoTimeLayoutConverter.convert("4");
    assertEquals("m", patterns2[0]);
  }

  @Test
  public void testSecondFormats() {
    // Two-digit second
    String[] patterns1 = GoTimeLayoutConverter.convert("05");
    assertTrue(patterns1[0].contains("ss"));

    // Single-digit second (standalone)
    String[] patterns2 = GoTimeLayoutConverter.convert("5");
    assertEquals("s", patterns2[0]);
  }

  @Test
  public void testNanosecondFormats() {
    // Nanoseconds (9 digits) - .000 variant
    String[] patterns1 = GoTimeLayoutConverter.convert(".000000000");
    assertEquals(".SSSSSSSSS", patterns1[0]);

    // Nanoseconds (9 digits) - .999 variant
    String[] patterns2 = GoTimeLayoutConverter.convert(".999999999");
    assertEquals(".SSSSSSSSS", patterns2[0]);

    // Microseconds (6 digits) - .000 variant
    String[] patterns3 = GoTimeLayoutConverter.convert(".000000");
    assertEquals(".SSSSSS", patterns3[0]);

    // Microseconds (6 digits) - .999 variant
    String[] patterns4 = GoTimeLayoutConverter.convert(".999999");
    assertEquals(".SSSSSS", patterns4[0]);

    // Milliseconds (3 digits) - .000 variant
    String[] patterns5 = GoTimeLayoutConverter.convert(".000");
    assertEquals(".SSS", patterns5[0]);

    // Milliseconds (3 digits) - .999 variant
    String[] patterns6 = GoTimeLayoutConverter.convert(".999");
    assertEquals(".SSS", patterns6[0]);
  }

  @Test
  public void testAmPmFormats() {
    // Uppercase AM/PM
    String[] patterns1 = GoTimeLayoutConverter.convert("PM");
    assertEquals("a", patterns1[0]);

    // Lowercase am/pm
    String[] patterns2 = GoTimeLayoutConverter.convert("pm");
    assertEquals("a", patterns2[0]);
  }

  @Test
  public void testTimezoneFormats() {
    // -07:00 format
    String[] patterns1 = GoTimeLayoutConverter.convert("-07:00");
    assertEquals("XXX", patterns1[0]);

    // -0700 format
    String[] patterns2 = GoTimeLayoutConverter.convert("-0700");
    assertEquals("XX", patterns2[0]);

    // -07 format
    String[] patterns3 = GoTimeLayoutConverter.convert("-07");
    assertEquals("X", patterns3[0]);

    // Z07:00 format
    String[] patterns4 = GoTimeLayoutConverter.convert("Z07:00");
    assertEquals("XXX", patterns4[0]);

    // Z0700 format
    String[] patterns5 = GoTimeLayoutConverter.convert("Z0700");
    assertEquals("XX", patterns5[0]);

    // Z07 format
    String[] patterns6 = GoTimeLayoutConverter.convert("Z07");
    assertEquals("X", patterns6[0]);

    // MST (timezone abbreviation)
    String[] patterns7 = GoTimeLayoutConverter.convert("MST");
    assertEquals("zzz", patterns7[0]);
  }

  @Test
  public void testComplexLayout() {
    // Mon Jan 2 15:04:05 MST 2006 (Go's reference time)
    String[] patterns = GoTimeLayoutConverter.convert("Mon Jan 2 15:04:05 MST 2006");
    assertEquals(2, patterns.length);
    assertEquals("EEE MMM d HH:mm:ss zzz yyyy", patterns[0]);
    assertEquals("EEE MMM d HH:mm:ss.SSSSSSSSS zzz yyyy", patterns[1]);

    assertCanParse(patterns, "Mon Jan 2 15:04:05 UTC 2006");
  }

  @Test
  public void testLayoutWithSlashes() {
    String[] patterns = GoTimeLayoutConverter.convert("01/02/2006");
    assertEquals("MM'/'dd'/'yyyy", patterns[0]);

    assertCanParse(patterns, "12/25/2023");
  }

  @Test
  public void testLayoutWithLiteralT() {
    // ISO 8601 format
    String[] patterns = GoTimeLayoutConverter.convert("2006-01-02T15:04:05Z07:00");
    assertEquals(2, patterns.length);
    assertTrue(patterns[0].contains("'T'"));
    assertTrue(patterns[0].contains("XXX"));

    assertCanParse(patterns, "2023-12-25T14:30:45+00:00");
  }

  @Test
  public void testStandaloneDigitsNotReplacedInLargerNumbers() {
    // Ensure "1" in "2015" doesn't get replaced
    String[] patterns = GoTimeLayoutConverter.convert("2006-01-02");
    assertFalse(patterns[0].contains("M0M6")); // Should be yyyy-MM-dd
    assertEquals("yyyy-MM-dd", patterns[0]);
  }

  @Test
  public void testNanosecondVariantsReturned() {
    // When layout contains seconds, should return variant with nanoseconds too
    String[] patterns = GoTimeLayoutConverter.convert("2006-01-02 15:04:05");
    assertEquals(2, patterns.length);
    assertEquals("yyyy-MM-dd HH:mm:ss", patterns[0]);
    assertEquals("yyyy-MM-dd HH:mm:ss.SSSSSSSSS", patterns[1]);
  }

  @Test
  public void testNanosecondLayoutReturnsVariantWithout() {
    // When layout explicitly has nanoseconds, should also return variant without
    String[] patterns = GoTimeLayoutConverter.convert("2006-01-02 15:04:05.000000000");
    assertEquals(2, patterns.length);
    assertEquals("yyyy-MM-dd HH:mm:ss.SSSSSSSSS", patterns[0]);
    assertEquals("yyyy-MM-dd HH:mm:ss", patterns[1]);
  }

  @Test
  public void test12HourFormatWithAmPm() {
    String[] patterns = GoTimeLayoutConverter.convert("3:04:05 PM");
    assertEquals(2, patterns.length);
    assertEquals("h:mm:ss a", patterns[0]);
    assertEquals("h:mm:ss.SSSSSSSSS a", patterns[1]);

    assertCanParse(patterns, "2:30:45 PM");
  }

  @Test
  public void testFullMonthAndWeekdayNames() {
    String[] patterns = GoTimeLayoutConverter.convert("Monday, January 2, 2006");
    assertEquals("EEEE, MMMM d, yyyy", patterns[0]);

    assertCanParse(patterns, "Monday, December 25, 2023");
  }

  @Test
  public void testCommonLogFormat() {
    String[] patterns = GoTimeLayoutConverter.convert("02/Jan/2006:15:04:05 -0700");
    assertEquals(2, patterns.length);
    assertTrue(patterns[0].contains("dd'/'MMM'/'yyyy"));
    assertTrue(patterns[0].contains("XX"));

    assertCanParse(patterns, "25/Dec/2023:14:30:45 -0500");
  }

  @Test
  public void testNginxLogFormat() {
    // Nginx default log format
    String[] patterns = GoTimeLayoutConverter.convert("02/Jan/2006:15:04:05 -0700");
    assertCanParse(patterns, "10/Oct/2023:13:55:36 -0700");
  }

  @Test
  public void testSyslogFormat() {
    // Common syslog timestamp format
    String[] patterns = GoTimeLayoutConverter.convert("Jan _2 15:04:05");
    assertEquals(2, patterns.length);
    // _2 becomes " d", Jan becomes "MMM"
    assertTrue(patterns[0].contains("MMM"));
    assertTrue(patterns[0].contains(" d"));
    assertTrue(patterns[0].contains("HH:mm:ss"));

    assertCanParse(patterns, "Dec  1 08:30:15");
  }

  @Test
  public void testApacheAccessLogFormat() {
    // Apache access log format: [02/Jan/2006:15:04:05 -0700]
    String[] patterns = GoTimeLayoutConverter.convert("02/Jan/2006:15:04:05 -0700");
    assertCanParse(patterns, "15/Aug/2023:10:45:30 +0000");
  }

  @Test
  public void testDatabaseTimestampFormat() {
    // Common database timestamp format
    String[] patterns = GoTimeLayoutConverter.convert("2006-01-02 15:04:05.000000");
    assertEquals(2, patterns.length);
    assertEquals("yyyy-MM-dd HH:mm:ss.SSSSSS", patterns[0]);

    assertCanParse(patterns, "2023-12-25 14:30:45.123456");
  }

  @Test
  public void testLayoutWithLiteralText() {
    // Format with literal text mixed in
    // Note: This assumes literal text is passed through as-is
    // For actual use, literals should be enclosed in single quotes in the Go layout
    String[] patterns = GoTimeLayoutConverter.convert("2006-01-02 15:04:05");
    assertTrue(patterns[0].contains("yyyy-MM-dd"));
    assertTrue(patterns[0].contains("HH:mm:ss"));

    assertCanParse(patterns, "2023-12-25 14:30:45");
  }

  @Test
  public void testLayoutWithParentheses() {
    // Format with parentheses
    String[] patterns = GoTimeLayoutConverter.convert("2006-01-02 (Monday)");
    assertTrue(patterns[0].contains("yyyy-MM-dd"));
    assertTrue(patterns[0].contains("EEEE"));

    assertCanParse(patterns, "2023-12-25 (Monday)");
  }

  @Test
  public void testEmailDateFormat() {
    // RFC 2822 email date format (similar to RFC1123)
    String[] patterns = GoTimeLayoutConverter.convert("Mon, 02 Jan 2006 15:04:05 -0700");
    assertCanParse(patterns, "Mon, 25 Dec 2023 14:30:45 -0500");
  }

  @Test
  public void testCookieExpiresFormat() {
    // HTTP cookie Expires format
    String[] patterns = GoTimeLayoutConverter.convert("Mon, 02-Jan-2006 15:04:05 MST");
    assertCanParse(patterns, "Mon, 25-Dec-2023 14:30:45 UTC");
  }

  @Test
  public void testCustomApplicationFormat() {
    // Custom format that combines multiple elements
    String[] patterns = GoTimeLayoutConverter.convert("Monday, January 2nd, 2006 at 3:04 PM");
    assertTrue(patterns[0].contains("EEEE"));
    assertTrue(patterns[0].contains("MMMM"));
    assertTrue(patterns[0].contains("h:mm a"));
  }

  @Test
  public void testYearMonthOnlyFormat() {
    String[] patterns = GoTimeLayoutConverter.convert("2006-01");
    assertEquals("yyyy-MM", patterns[0]);
  }

  @Test
  public void testYearOnlyFormat() {
    String[] patterns = GoTimeLayoutConverter.convert("2006");
    assertEquals("yyyy", patterns[0]);
  }

  @Test
  public void testTimeOnlyWithNanoseconds() {
    String[] patterns = GoTimeLayoutConverter.convert("15:04:05.999999999");
    assertEquals(2, patterns.length);
    assertEquals("HH:mm:ss.SSSSSSSSS", patterns[0]);
    assertEquals("HH:mm:ss", patterns[1]);

    assertCanParse(patterns, "14:30:45.123456789");
    assertCanParse(patterns, "14:30:45");
  }

  @Test
  public void test12HourWithAllComponents() {
    String[] patterns = GoTimeLayoutConverter.convert("3:04:05.000 PM");
    assertEquals(2, patterns.length);
    assertTrue(patterns[0].contains("h:mm:ss.SSS a"));

    assertCanParse(patterns, "2:30:45.123 PM");
  }

  @Test
  public void testMultipleSpaces() {
    // Format with multiple spaces
    String[] patterns = GoTimeLayoutConverter.convert("2006-01-02  15:04:05");
    assertTrue(patterns[0].contains("yyyy-MM-dd  HH:mm:ss"));
  }

  @Test
  public void testZuluTimeFormat() {
    // Zulu time (UTC) format - Z is a literal character here
    // Use RFC3339 which properly handles Z
    String[] patterns = GoTimeLayoutConverter.convert("RFC3339");
    assertCanParse(patterns, "2023-12-25T14:30:45Z");
  }

  @Test
  public void testMixedSeparators() {
    // Format with mixed separators
    String[] patterns = GoTimeLayoutConverter.convert("2006/01/02-15:04:05");
    assertTrue(patterns[0].contains("yyyy'/'MM'/'dd"));
    assertTrue(patterns[0].contains("HH:mm:ss"));

    assertCanParse(patterns, "2023/12/25-14:30:45");
  }

  @Test
  public void testCompactFormat() {
    // Compact format without separators
    String[] patterns = GoTimeLayoutConverter.convert("20060102150405");
    assertEquals("yyyyMMddHHmmss", patterns[0]);
  }

  @Test
  public void testEdgeCaseWithAdjacentNumbers() {
    // Ensure adjacent number patterns don't interfere
    String[] patterns = GoTimeLayoutConverter.convert("200601021504");
    // Should be: yyyy MM dd HH mm (year month day hour minute)
    assertEquals("yyyyMMddHHmm", patterns[0]);
  }

  /**
   * Helper method to assert that at least one of the patterns can successfully parse the given
   * value.
   */
  private void assertCanParse(String[] patterns, String value) {
    boolean parsed = false;
    for (String pattern : patterns) {
      try {
        // Go's layouts use fixed English month/day/AM-PM names regardless of the
        // JVM's default locale, so pin the formatter locale rather than inheriting it.
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
        TemporalAccessor result = formatter.parse(value);
        assertNotNull(result);
        parsed = true;
        break;
      } catch (Exception e) {
        // Try next pattern
      }
    }
    assertTrue(
        parsed,
        "None of the patterns could parse '"
            + value
            + "'. Patterns: "
            + String.join(", ", patterns));
  }
}
