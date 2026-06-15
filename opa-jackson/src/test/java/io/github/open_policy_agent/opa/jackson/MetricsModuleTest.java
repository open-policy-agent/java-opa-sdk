package io.github.open_policy_agent.opa.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.open_policy_agent.opa.metrics.Metrics;
import io.github.open_policy_agent.opa.metrics.SimpleMetrics;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class MetricsModuleTest {

  // Mirrors a real consumer: JavaTimeModule is required for Jackson to handle Duration; the
  // MetricsModule shim restores @JsonValue on Metrics.Timer that was lost when SimpleMetrics moved
  // into the JSON-free opa-evaluator module.
  private final ObjectMapper mapper =
      new ObjectMapper().registerModule(new JavaTimeModule()).registerModule(new MetricsModule());

  @Test
  void timer_jsonShapeMatchesDirectDurationSerialization() throws IOException {
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
