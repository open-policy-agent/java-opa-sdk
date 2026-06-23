package io.github.open_policy_agent.opa.gson;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.github.open_policy_agent.opa.metrics.Metrics;
import java.io.IOException;

/**
 * Gson {@link TypeAdapterFactory} that serializes {@link Metrics} types to match the JSON shape
 * produced by OPA's decision logs ({@code timer_<name>_ns} convention).
 *
 * <ul>
 *   <li>{@link Metrics.Timer} &rarr; nanoseconds (long)
 *   <li>{@link Metrics.Counter} &rarr; integer value
 *   <li>{@link Metrics.Histogram} &rarr; its {@link Metrics.Histogram.Values} object
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * Gson gson = new GsonBuilder()
 *     .registerTypeAdapterFactory(new MetricsTypeAdapterFactory())
 *     .create();
 * String json = gson.toJson(timer);
 * }</pre>
 */
public class MetricsTypeAdapterFactory implements TypeAdapterFactory {

  @SuppressWarnings("unchecked")
  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    if (Metrics.Timer.class.isAssignableFrom(type.getRawType())) {
      return (TypeAdapter<T>) new TimerAdapter();
    }
    if (Metrics.Counter.class.isAssignableFrom(type.getRawType())) {
      return (TypeAdapter<T>) new CounterAdapter();
    }
    if (Metrics.Histogram.class.isAssignableFrom(type.getRawType())) {
      return (TypeAdapter<T>) new HistogramAdapter(gson);
    }
    return null;
  }

  private static final class TimerAdapter extends TypeAdapter<Metrics.Timer> {
    @Override
    public void write(JsonWriter out, Metrics.Timer timer) throws IOException {
      if (timer == null) {
        out.nullValue();
      } else {
        out.value(timer.value().toNanos());
      }
    }

    @Override
    public Metrics.Timer read(JsonReader in) {
      throw new UnsupportedOperationException("Deserialization of Metrics.Timer is not supported");
    }
  }

  private static final class CounterAdapter extends TypeAdapter<Metrics.Counter> {
    @Override
    public void write(JsonWriter out, Metrics.Counter counter) throws IOException {
      if (counter == null) {
        out.nullValue();
      } else {
        out.value(counter.value());
      }
    }

    @Override
    public Metrics.Counter read(JsonReader in) {
      throw new UnsupportedOperationException(
          "Deserialization of Metrics.Counter is not supported");
    }
  }

  private static final class HistogramAdapter extends TypeAdapter<Metrics.Histogram> {
    private final TypeAdapter<Metrics.Histogram.Values> valuesAdapter;

    HistogramAdapter(Gson gson) {
      this.valuesAdapter = gson.getAdapter(Metrics.Histogram.Values.class);
    }

    @Override
    public void write(JsonWriter out, Metrics.Histogram histogram) throws IOException {
      if (histogram == null) {
        out.nullValue();
      } else {
        valuesAdapter.write(out, histogram.value());
      }
    }

    @Override
    public Metrics.Histogram read(JsonReader in) {
      throw new UnsupportedOperationException(
          "Deserialization of Metrics.Histogram is not supported");
    }
  }
}
