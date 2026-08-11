package io.github.open_policy_agent.opa.proto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import opa.bundle.v1.Annotations;
import opa.bundle.v1.AuthorAnnotation;
import opa.bundle.v1.CompileAnnotation;
import opa.bundle.v1.Manifest;
import opa.bundle.v1.RelatedResourceAnnotation;
import opa.bundle.v1.SchemaAnnotation;
import opa.bundle.v1.WasmResolver;

/**
 * Maps a decoded proto {@link Manifest} into the {@code Map<String, Object>} shape produced for the
 * JSON {@code .manifest} by the {@code BundleParser}, so downstream consumers see identical metadata
 * regardless of the bundle's wire format.
 *
 * <p>Field presence mirrors OPA's JSON marshaling exactly: fields tagged {@code omitempty} (or
 * emitted conditionally by a custom {@code MarshalJSON}, as {@code ast.Annotations} does) are
 * included only when non-empty/non-default, so the proto path never emits a key the JSON reader
 * would drop. Annotation {@code location} is intentionally never emitted: OPA gates it behind a
 * marshal option that is off by default, so standard {@code opa build} manifests omit it.
 */
final class ManifestMapper {

  private ManifestMapper() {}

  static Map<String, Object> toMap(Manifest m) {
    Map<String, Object> out = new LinkedHashMap<>();

    // Revision has no omitempty in OPA's JSON tags — always present.
    out.put("revision", m.getRevision());

    // Roots is a pointer in Go (omitempty); roots_set distinguishes an explicitly-set list
    // (possibly empty) from an absent one.
    if (m.getRootsSet()) {
      out.put("roots", new ArrayList<>(m.getRootsList()));
    }

    if (m.getWasmCount() > 0) {
      List<Object> wasm = new ArrayList<>(m.getWasmCount());
      for (WasmResolver w : m.getWasmList()) {
        wasm.add(wasmResolver(w));
      }
      out.put("wasm", wasm);
    }

    if (m.hasRegoVersion()) {
      out.put("rego_version", m.getRegoVersion());
    }

    if (m.getFileRegoVersionsCount() > 0) {
      out.put("file_rego_versions", new LinkedHashMap<>(m.getFileRegoVersionsMap()));
    }

    if (m.hasMetadata()) {
      out.put("metadata", StructConverter.toMap(m.getMetadata()));
    }

    return out;
  }

  private static Map<String, Object> wasmResolver(WasmResolver w) {
    // bundle.WasmResolver: entrypoint, module, annotations are all omitempty.
    Map<String, Object> out = new LinkedHashMap<>();
    putIfNotEmpty(out, "entrypoint", w.getEntrypoint());
    putIfNotEmpty(out, "module", w.getModule());
    if (w.getAnnotationsCount() > 0) {
      List<Object> annotations = new ArrayList<>(w.getAnnotationsCount());
      for (Annotations a : w.getAnnotationsList()) {
        annotations.add(annotations(a));
      }
      out.put("annotations", annotations);
    }
    return out;
  }

  private static Map<String, Object> annotations(Annotations a) {
    // Mirrors ast.Annotations.MarshalJSON: scope is always present; the rest are emitted only when
    // non-empty/true.
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("scope", a.getScope());
    putIfNotEmpty(out, "title", a.getTitle());
    putIfNotEmpty(out, "description", a.getDescription());
    if (a.getEntrypoint()) {
      out.put("entrypoint", true);
    }
    if (a.getOrganizationsCount() > 0) {
      out.put("organizations", new ArrayList<>(a.getOrganizationsList()));
    }
    if (a.getRelatedResourcesCount() > 0) {
      List<Object> related = new ArrayList<>(a.getRelatedResourcesCount());
      for (RelatedResourceAnnotation r : a.getRelatedResourcesList()) {
        Map<String, Object> rm = new LinkedHashMap<>();
        rm.put("ref", r.getRef());
        putIfNotEmpty(rm, "description", r.getDescription());
        related.add(rm);
      }
      out.put("related_resources", related);
    }
    if (a.getAuthorsCount() > 0) {
      List<Object> authors = new ArrayList<>(a.getAuthorsCount());
      for (AuthorAnnotation author : a.getAuthorsList()) {
        Map<String, Object> am = new LinkedHashMap<>();
        am.put("name", author.getName());
        putIfNotEmpty(am, "email", author.getEmail());
        authors.add(am);
      }
      out.put("authors", authors);
    }
    if (a.getSchemasCount() > 0) {
      List<Object> schemas = new ArrayList<>(a.getSchemasCount());
      for (SchemaAnnotation s : a.getSchemasList()) {
        Map<String, Object> sm = new LinkedHashMap<>();
        sm.put("path", s.getPath());
        putIfNotEmpty(sm, "schema", s.getSchema());
        if (s.hasDefinition()) {
          sm.put("definition", StructConverter.toObject(s.getDefinition()));
        }
        schemas.add(sm);
      }
      out.put("schemas", schemas);
    }
    if (a.hasCompile()) {
      CompileAnnotation c = a.getCompile();
      Map<String, Object> cm = new LinkedHashMap<>();
      if (c.getUnknownsCount() > 0) {
        cm.put("unknowns", new ArrayList<>(c.getUnknownsList()));
      }
      putIfNotEmpty(cm, "mask_rule", c.getMaskRule());
      out.put("compile", cm);
    }
    if (a.hasCustom()) {
      out.put("custom", StructConverter.toMap(a.getCustom()));
    }
    if (a.hasLabels()) {
      out.put("labels", StructConverter.toMap(a.getLabels()));
    }
    return out;
  }

  private static void putIfNotEmpty(Map<String, Object> out, String key, String value) {
    if (!value.isEmpty()) {
      out.put(key, value);
    }
  }
}
