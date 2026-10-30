package io.github.open_policy_agent.opa;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** The version of this SDK, taken from the build's project version. */
public final class SdkVersion {

  private static final String UNKNOWN = "unknown";
  private static final String VERSION = load();

  private SdkVersion() {}

  public static String get() {
    return VERSION;
  }

  private static String load() {
    try (InputStream in = SdkVersion.class.getResourceAsStream("sdk-version.properties")) {
      if (in == null) {
        return UNKNOWN;
      }
      final Properties properties = new Properties();
      properties.load(in);
      final String version = properties.getProperty("version", UNKNOWN);
      // An unfiltered copy (an IDE build without processResources) still holds the placeholder.
      return version.startsWith("$") ? UNKNOWN : version;
    } catch (IOException e) {
      return UNKNOWN;
    }
  }
}
