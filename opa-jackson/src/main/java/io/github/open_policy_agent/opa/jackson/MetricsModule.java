package io.github.open_policy_agent.opa.jackson;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.open_policy_agent.opa.metrics.Metrics;
import io.github.open_policy_agent.opa.metrics.SimpleMetrics;
import java.time.Duration;

/**
 * Jackson {@link SimpleModule} that adds {@code @JsonValue} behavior to {@link SimpleMetrics}'s inner {@code Timer}.
 * Register this module to have {@link Metrics.Timer} serialize as the underlying
 * {@link Duration} value rather than a default bean.
 *
 * <p>Applied at the {@link Metrics.Timer} interface level via a mixin, so it covers any Timer
 * implementation, not just the one returned by {@link SimpleMetrics}.
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
  }

  abstract static class TimerMixin {
    @JsonValue
    abstract Duration value();
  }
}
