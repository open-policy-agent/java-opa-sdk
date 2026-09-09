package io.github.open_policy_agent.opa.jackson;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.open_policy_agent.opa.metrics.Metrics;
import io.github.open_policy_agent.opa.metrics.SimpleMetrics;
import java.time.Duration;

/**
 * Jackson {@link SimpleModule} that adds {@code @JsonValue} behavior to {@link SimpleMetrics}'s inner metrics.
 * Register this module to have {@link Metrics.Timer} serialize as the underlying
 * {@link Duration} value, {@link Metrics.Counter} as its {@code int} value and
 * {@link Metrics.Histogram} as its {@link Metrics.Histogram.Values} stats, rather than as default
 * beans. None of them expose a Jackson-visible property, so without these mixins serializing one
 * fails outright.
 *
 * <p>Applied at the {@link Metrics} interface level via mixins, so they cover any implementation,
 * not just the ones returned by {@link SimpleMetrics}.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * ObjectMapper mapper = new ObjectMapper().registerModule(new MetricsModule());
 * String json = mapper.writeValueAsString(simpleMetrics.timer("foo"));
 * }</pre>
 */
public class MetricsModule extends SimpleModule {

  public MetricsModule() {
    super("opa-metrics");
    setMixInAnnotation(Metrics.Timer.class, TimerMixin.class);
    setMixInAnnotation(Metrics.Counter.class, CounterMixin.class);
    setMixInAnnotation(Metrics.Histogram.class, HistogramMixin.class);
  }

  interface TimerMixin {
    @JsonValue
    Duration value();
  }

  interface CounterMixin {
    @JsonValue
    int value();
  }

  interface HistogramMixin {
    @JsonValue
    Metrics.Histogram.Values value();
  }
}
