package io.github.open_policy_agent.opa.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Helpers for loading SPI implementations via {@link ServiceLoader} with a consistent
 * "at most one provider" policy and error message.
 *
 * <p>Several SPIs in this SDK ({@code PolicyReader}, {@code BundleParser}, {@code
 * ProtoBundleDecoder}, {@code AnnotationIntrospector}) permit only a single registered
 * implementation. They differ only in how they treat the zero-implementations case — hard error,
 * {@code null}, or a fallback — which callers express by mapping the returned {@link Optional}.
 */
public final class Services {

  private Services() {}

  /**
   * Loads the single registered implementation of {@code spi}, if any.
   *
   * @param spi the service interface to load
   * @param <T> the service type
   * @return the sole implementation, or {@link Optional#empty()} if none is registered
   * @throws IllegalStateException if more than one implementation is registered
   */
  public static <T> Optional<T> loadAtMostOne(Class<T> spi) {
    List<T> impls = new ArrayList<>();
    for (T impl : ServiceLoader.load(spi)) {
      impls.add(impl);
    }
    if (impls.size() > 1) {
      StringBuilder names = new StringBuilder();
      for (int i = 0; i < impls.size(); i++) {
        if (i > 0) {
          names.append(", ");
        }
        names.append(impls.get(i).getClass().getName());
      }
      throw new IllegalStateException(
          "Multiple "
              + spi.getSimpleName()
              + " implementations found on the classpath: "
              + names
              + ". Only one provider may be registered.");
    }
    return impls.isEmpty() ? Optional.empty() : Optional.of(impls.get(0));
  }
}
