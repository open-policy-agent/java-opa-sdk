package io.github.open_policy_agent.opa.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.bundle.Bundle;

/** Unit tests for bundle root conflict detection in Store implementations. */
public class ConflictingRootsTest {

  private Store store;

  @BeforeEach
  void setUp() {
    store = new InMem();
  }

  // Helper method to create a bundle with specified roots
  private Bundle createBundleWithRoots(String... roots) {
    Bundle.Builder builder = new Bundle.Builder();

    if (roots.length > 0) {
      Map<String, Object> manifest = new HashMap<>();
      List<String> rootList = new ArrayList<>();
      for (String root : roots) {
        rootList.add(root);
      }
      manifest.put("roots", rootList);
      builder.withManifest(manifest);
    }

    return builder.build();
  }

  @Test
  void testNoConflict_DifferentRoots() {
    // Arrange
    Bundle bundle1 = createBundleWithRoots("example");
    Bundle bundle2 = createBundleWithRoots("other");
    RegoObject data = new RegoObject();

    // Act & Assert - should not throw
    assertDoesNotThrow(
        () -> {
          store.write("bundle1", bundle1, data);
          store.write("bundle2", bundle2, data);
        });
  }

  @Test
  void testNoConflict_DifferentPrefixes() {
    // "example" and "example2" should NOT conflict
    Bundle bundle1 = createBundleWithRoots("example");
    Bundle bundle2 = createBundleWithRoots("example2");
    RegoObject data = new RegoObject();

    // Act & Assert - should not throw
    assertDoesNotThrow(
        () -> {
          store.write("bundle1", bundle1, data);
          store.write("bundle2", bundle2, data);
        });
  }

  @Test
  void testConflict_ExactMatch() {
    // Arrange
    Bundle bundle1 = createBundleWithRoots("example");
    Bundle bundle2 = createBundleWithRoots("example");
    RegoObject data = new RegoObject();

    // Act
    store.write("bundle1", bundle1, data);

    // Assert
    ConflictingRootsException exception =
        assertThrows(ConflictingRootsException.class, () -> store.write("bundle2", bundle2, data));

    assertTrue(exception.getMessage().contains("bundle2"));
    assertTrue(exception.getMessage().contains("example"));
    assertTrue(exception.getMessage().contains("bundle1"));
    assertEquals("bundle2", exception.getContext().get("bundleId"));
    assertEquals("example", exception.getContext().get("newRoot"));
    assertEquals("bundle1", exception.getContext().get("conflictingBundleId"));
    assertEquals("example", exception.getContext().get("conflictingRoot"));
  }

  @Test
  void testConflict_PrefixMatch_NewIsPrefix() {
    // "example" conflicts with "example/foo"
    Bundle bundle1 = createBundleWithRoots("example/foo");
    Bundle bundle2 = createBundleWithRoots("example");
    RegoObject data = new RegoObject();

    // Act
    store.write("bundle1", bundle1, data);

    // Assert
    assertThrows(ConflictingRootsException.class, () -> store.write("bundle2", bundle2, data));
  }

  @Test
  void testConflict_PrefixMatch_ExistingIsPrefix() {
    // "example/foo" conflicts with "example"
    Bundle bundle1 = createBundleWithRoots("example");
    Bundle bundle2 = createBundleWithRoots("example/foo");
    RegoObject data = new RegoObject();

    // Act
    store.write("bundle1", bundle1, data);

    // Assert
    assertThrows(ConflictingRootsException.class, () -> store.write("bundle2", bundle2, data));
  }

  @Test
  void testConflict_EmptyRootWithEmpty() {
    // Two bundles with empty roots should conflict
    Bundle bundle1 = createBundleWithRoots("");
    Bundle bundle2 = createBundleWithRoots("");
    RegoObject data = new RegoObject();

    // Act
    store.write("bundle1", bundle1, data);

    // Assert
    assertThrows(ConflictingRootsException.class, () -> store.write("bundle2", bundle2, data));
  }

  @Test
  void testConflict_EmptyRootWithNonEmpty() {
    // Empty root should conflict with any non-empty root
    Bundle bundle1 = createBundleWithRoots("");
    Bundle bundle2 = createBundleWithRoots("example");
    RegoObject data = new RegoObject();

    // Act
    store.write("bundle1", bundle1, data);

    // Assert
    assertThrows(ConflictingRootsException.class, () -> store.write("bundle2", bundle2, data));
  }

  @Test
  void testConflict_NonEmptyRootWithEmpty() {
    // Non-empty root should conflict with empty root
    Bundle bundle1 = createBundleWithRoots("example");
    Bundle bundle2 = createBundleWithRoots("");
    RegoObject data = new RegoObject();

    // Act
    store.write("bundle1", bundle1, data);

    // Assert
    assertThrows(ConflictingRootsException.class, () -> store.write("bundle2", bundle2, data));
  }

  @Test
  void testConflict_NoManifestTreatedAsEmptyRoot() {
    // Bundle without manifest should be treated as empty root and conflict with everything
    Bundle bundle1 = new Bundle.Builder().build(); // No manifest
    Bundle bundle2 = createBundleWithRoots("example");
    RegoObject data = new RegoObject();

    // Act
    store.write("bundle1", bundle1, data);

    // Assert
    assertThrows(ConflictingRootsException.class, () -> store.write("bundle2", bundle2, data));
  }

  @Test
  void testConflict_DeepPrefixMatch() {
    // "example/foo/bar" conflicts with "example/foo"
    Bundle bundle1 = createBundleWithRoots("example/foo");
    Bundle bundle2 = createBundleWithRoots("example/foo/bar");
    RegoObject data = new RegoObject();

    // Act
    store.write("bundle1", bundle1, data);

    // Assert
    assertThrows(ConflictingRootsException.class, () -> store.write("bundle2", bundle2, data));
  }

  @Test
  void testNoConflict_SiblingPaths() {
    // "example/foo" and "example/bar" should NOT conflict
    Bundle bundle1 = createBundleWithRoots("example/foo");
    Bundle bundle2 = createBundleWithRoots("example/bar");
    RegoObject data = new RegoObject();

    // Act & Assert - should not throw
    assertDoesNotThrow(
        () -> {
          store.write("bundle1", bundle1, data);
          store.write("bundle2", bundle2, data);
        });
  }

  @Test
  void testAllowRewriteSameBundle() {
    // Rewriting the same bundle ID should be allowed even with same root
    Bundle bundle1 = createBundleWithRoots("example");
    Bundle bundle1Updated = createBundleWithRoots("example");
    RegoObject data = new RegoObject();

    // Act & Assert - should not throw
    assertDoesNotThrow(
        () -> {
          store.write("bundle1", bundle1, data);
          store.write("bundle1", bundle1Updated, data); // Same ID, allowed
        });
  }

  @Test
  void testAllowChangingRootOfSameBundle() {
    // Changing the root of the same bundle should be allowed
    Bundle bundle1 = createBundleWithRoots("example");
    Bundle bundle1Updated = createBundleWithRoots("other");
    RegoObject data = new RegoObject();

    // Act & Assert - should not throw
    assertDoesNotThrow(
        () -> {
          store.write("bundle1", bundle1, data);
          store.write("bundle1", bundle1Updated, data); // Same ID, different root, allowed
        });
  }

  @Test
  void testMultipleNonConflictingBundles() {
    // Load multiple bundles with non-conflicting roots
    Bundle bundle1 = createBundleWithRoots("app1");
    Bundle bundle2 = createBundleWithRoots("app2");
    Bundle bundle3 = createBundleWithRoots("app3");
    RegoObject data = new RegoObject();

    // Act & Assert - should not throw
    assertDoesNotThrow(
        () -> {
          store.write("bundle1", bundle1, data);
          store.write("bundle2", bundle2, data);
          store.write("bundle3", bundle3, data);
        });
  }

  @Test
  void testConflictWithMultipleExistingBundles() {
    // New bundle conflicts with one of multiple existing bundles
    Bundle bundle1 = createBundleWithRoots("app1");
    Bundle bundle2 = createBundleWithRoots("app2");
    Bundle bundle3 = createBundleWithRoots("app1/subapp"); // Conflicts with bundle1
    RegoObject data = new RegoObject();

    // Act
    store.write("bundle1", bundle1, data);
    store.write("bundle2", bundle2, data);

    // Assert
    ConflictingRootsException exception =
        assertThrows(ConflictingRootsException.class, () -> store.write("bundle3", bundle3, data));

    assertEquals("bundle3", exception.getContext().get("bundleId"));
    assertEquals("app1/subapp", exception.getContext().get("newRoot"));
    assertEquals("bundle1", exception.getContext().get("conflictingBundleId"));
  }

  @Test
  void testErrorCode() {
    // Verify the error code is set correctly
    Bundle bundle1 = createBundleWithRoots("example");
    Bundle bundle2 = createBundleWithRoots("example");
    RegoObject data = new RegoObject();

    store.write("bundle1", bundle1, data);

    ConflictingRootsException exception =
        assertThrows(ConflictingRootsException.class, () -> store.write("bundle2", bundle2, data));

    assertEquals("opa_conflicting_roots", exception.getErrorCode());
  }
}
