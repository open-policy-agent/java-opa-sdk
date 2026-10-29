package io.github.open_policy_agent.opa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

class SdkVersionTest {

  @Test
  void matchesTheBuildsProjectVersion() {
    assumeTrue(System.getProperty("sdk.version") != null, "set by the Gradle test task");
    assertEquals(System.getProperty("sdk.version"), SdkVersion.get());
  }
}
