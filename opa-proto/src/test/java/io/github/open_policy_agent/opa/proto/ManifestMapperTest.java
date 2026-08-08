package io.github.open_policy_agent.opa.proto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import opa.bundle.v1.Annotations;
import opa.bundle.v1.AuthorAnnotation;
import opa.bundle.v1.Location;
import opa.bundle.v1.Manifest;
import opa.bundle.v1.RelatedResourceAnnotation;
import opa.bundle.v1.WasmResolver;
import org.junit.jupiter.api.Test;

/**
 * Verifies the proto manifest decoder honors OPA's omitempty / conditional-MarshalJSON semantics, so
 * it never emits keys the JSON {@code .manifest} reader would drop.
 */
class ManifestMapperTest {

  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstAnnotation(Map<String, Object> manifest) {
    List<Object> wasm = (List<Object>) manifest.get("wasm");
    Map<String, Object> resolver = (Map<String, Object>) wasm.get(0);
    List<Object> annotations = (List<Object>) resolver.get("annotations");
    return (Map<String, Object>) annotations.get(0);
  }

  @Test
  @SuppressWarnings("unchecked")
  void emptyOptionalFieldsAreOmitted() {
    Manifest m =
        Manifest.newBuilder()
            .setRevision("")
            .setRootsSet(true)
            .addRoots("")
            .addWasm(
                WasmResolver.newBuilder()
                    .setEntrypoint("") // omitempty -> omitted
                    .setModule("") // omitempty -> omitted
                    .addAnnotations(
                        Annotations.newBuilder()
                            .setScope("rule")
                            .setTitle("") // omitted
                            .setDescription("") // omitted
                            .setEntrypoint(false) // omitted
                            .addRelatedResources(
                                RelatedResourceAnnotation.newBuilder()
                                    .setRef("https://example.com")
                                    .setDescription("")) // omitted
                            .addAuthors(AuthorAnnotation.newBuilder().setName("me").setEmail(""))
                            // Location is set but OPA omits it by default, so it must not appear.
                            .setLocation(Location.newBuilder().setFile("f").setRow(1).setCol(2))))
            .build();

    Map<String, Object> out = ManifestMapper.toMap(m);

    Map<String, Object> resolver = (Map<String, Object>) ((List<Object>) out.get("wasm")).get(0);
    assertThat(resolver).doesNotContainKeys("entrypoint", "module");

    Map<String, Object> ann = firstAnnotation(out);
    assertThat(ann).containsEntry("scope", "rule");
    assertThat(ann).doesNotContainKeys("title", "description", "entrypoint", "location");

    Map<String, Object> related = (Map<String, Object>) ((List<Object>) ann.get("related_resources")).get(0);
    assertThat(related).containsEntry("ref", "https://example.com").doesNotContainKey("description");

    Map<String, Object> author = (Map<String, Object>) ((List<Object>) ann.get("authors")).get(0);
    assertThat(author).containsEntry("name", "me").doesNotContainKey("email");
  }

  @Test
  void populatedOptionalFieldsArePresent() {
    Manifest m =
        Manifest.newBuilder()
            .addWasm(
                WasmResolver.newBuilder()
                    .setEntrypoint("entry")
                    .setModule("mod.wasm")
                    .addAnnotations(
                        Annotations.newBuilder()
                            .setScope("rule")
                            .setTitle("t")
                            .setDescription("d")
                            .setEntrypoint(true)))
            .build();

    Map<String, Object> ann = firstAnnotation(ManifestMapper.toMap(m));
    assertThat(ann)
        .containsEntry("scope", "rule")
        .containsEntry("title", "t")
        .containsEntry("description", "d")
        .containsEntry("entrypoint", true);
  }
}
