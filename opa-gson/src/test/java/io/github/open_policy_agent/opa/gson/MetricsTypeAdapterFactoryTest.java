package io.github.open_policy_agent.opa.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.open_policy_agent.opa.metrics.Metrics;
import io.github.open_policy_agent.opa.metrics.SimpleMetrics;
import org.junit.jupiter.api.Test;

/**
 * Parallel to opa-jackson's MetricsModuleTest. Verifies that MetricsTypeAdapterFactory produces
 * JSON matching OPA's decision log shape ({@code timer_<name>_ns} = nanoseconds as a long).
 */
class MetricsTypeAdapterFactoryTest {

  private final Gson gson =
      new GsonBuilder().registerTypeAdapterFactory(new MetricsTypeAdapterFactory()).create();

  @Test
  void timer_serializesAsNanoseconds() {
    SimpleMetrics metrics = new SimpleMetrics();
    Metrics.Timer timer = metrics.timer("rego_query_eval");
    timer.start();
    timer.stop();

    String json = gson.toJson(timer, Metrics.Timer.class);

    // Must be a plain number (nanoseconds), matching DecisionLogPlugin's timer.value().toNanos()
    long nanos = timer.value().toNanos();
    assertThat(json).isEqualTo(String.valueOf(nanos));
  }

  @Test
  void timer_jsonShapeMatchesDirectNanosSerialization() {
    SimpleMetrics metrics = new SimpleMetrics();
    Metrics.Timer timer = metrics.timer("rego_query_eval");
    timer.start();
    timer.stop();

    String timerJson = gson.toJson(timer, Metrics.Timer.class);
    String nanosJson = gson.toJson(timer.value().toNanos());

    // Serializing the Timer must produce exactly what serializing its nanosecond value produces.
    assertThat(timerJson).isEqualTo(nanosJson);
  }

  @Test
  void timer_null_serializesAsNull() {
    String json = gson.toJson(null, Metrics.Timer.class);
    assertThat(json).isEqualTo("null");
  }
}
