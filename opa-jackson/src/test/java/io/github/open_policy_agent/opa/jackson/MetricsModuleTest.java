package io.github.open_policy_agent.opa.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.open_policy_agent.opa.metrics.Metrics;
import io.github.open_policy_agent.opa.metrics.SimpleMetrics;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class MetricsModuleTest {

  // The MetricsModule shim restores @JsonValue on Metrics.Timer that was lost when SimpleMetrics
  // moved into the JSON-free opa-evaluator module. Jackson 3 has java.time support built-in,
  // so no separate module registration is needed for Duration.
  private final ObjectMapper mapper =
      JsonMapper.builder().addModule(new MetricsModule()).build();

  @Test
  void timer_jsonShapeMatchesDirectDurationSerialization() {
    SimpleMetrics metrics = new SimpleMetrics();
    Metrics.Timer timer = metrics.timer("rego_query_eval");
    timer.start();
    timer.stop();

    String timerJson = mapper.writeValueAsString(timer);
    String durationJson = mapper.writeValueAsString(timer.value());

    // The whole point of @JsonValue here: serializing the Timer must produce exactly what
    // serializing its underlying Duration produces.
    assertThat(timerJson).isEqualTo(durationJson);
  }
}
