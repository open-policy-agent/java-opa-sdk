// This file contains a Java translation of code from the Go project:
//   crypto/x509 (verify.go), Copyright 2011 The Go Authors.
// That code is used under the BSD-style license reproduced in
// src/main/resources/META-INF/licenses/LICENSE-golang.txt (Copyright 2009 The Go Authors).
// The translation and the rest of this file are licensed under the Apache License 2.0.
package io.github.open_policy_agent.opa.ast.builtin.impls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Certificate policy validation (RFC 5280, 6.1, as updated by RFC 9618), ported from Go 1.27's
 * {@code policiesValid} for the options OPA uses: any policy is acceptable and no policy flags are
 * set initially. A chain then fails only when a CA requires an explicit policy that the chain does
 * not provide, or maps a policy to or from anyPolicy.
 */
final class X509Policies {

  /** DER contents of anyPolicy (2.5.29.32.0), as an ISO-8859-1 key. */
  private static final String ANY_POLICY = X509Strings.latin1(new byte[] {0x55, 0x1d, 0x20, 0x00});

  private X509Policies() {}

  private static final class Node {
    final String validPolicy;
    List<String> expectedPolicySet;
    final Set<Node> parents = new LinkedHashSet<>();
    final Set<Node> children = new LinkedHashSet<>();

    Node(String validPolicy, List<Node> parents) {
      this.validPolicy = validPolicy;
      this.expectedPolicySet = List.of(validPolicy);
      for (Node p : parents) {
        p.children.add(this);
        this.parents.add(p);
      }
    }
  }

  private static final class Graph {
    final List<Map<String, Node>> strata = new ArrayList<>();
    Map<String, List<Node>> parentIndex = new LinkedHashMap<>();
    int depth;

    Graph() {
      Map<String, Node> root = new LinkedHashMap<>();
      root.put(ANY_POLICY, new Node(ANY_POLICY, List.of()));
      strata.add(root);
    }

    void insert(Node n) {
      strata.get(depth).put(n.validPolicy, n);
    }

    List<Node> parentsWithExpected(String expected) {
      List<Node> parents = depth == 0 ? null : parentIndex.get(expected);
      return parents == null ? List.of() : parents;
    }

    Node parentWithAnyPolicy() {
      return depth == 0 ? null : strata.get(depth - 1).get(ANY_POLICY);
    }

    Iterable<Node> parents() {
      return depth == 0 ? List.of() : new ArrayList<>(strata.get(depth - 1).values());
    }

    Map<String, Node> leaves() {
      return strata.get(depth);
    }

    Node leafWithPolicy(String policy) {
      return strata.get(depth).get(policy);
    }

    void deleteLeaf(String policy) {
      Node n = strata.get(depth).remove(policy);
      if (n == null) {
        return;
      }
      for (Node p : n.parents) {
        p.children.remove(n);
      }
      for (Node c : n.children) {
        c.parents.remove(n);
      }
    }

    List<Node> validPolicyNodes() {
      List<Node> valid = new ArrayList<>();
      for (int i = depth; i >= 0; i--) {
        for (Node n : strata.get(i).values()) {
          if (n.validPolicy.equals(ANY_POLICY) || n.parents.size() != 1) {
            continue;
          }
          if (n.parents.iterator().next().validPolicy.equals(ANY_POLICY)) {
            valid.add(n);
          }
        }
      }
      return valid;
    }

    void prune() {
      for (int i = depth - 1; i > 0; i--) {
        List<Node> stratum = new ArrayList<>(strata.get(i).values());
        for (Node n : stratum) {
          if (n.children.isEmpty()) {
            for (Node p : n.parents) {
              p.children.remove(n);
            }
            strata.get(i).remove(n.validPolicy);
          }
        }
      }
    }

    void incrDepth() {
      parentIndex = new LinkedHashMap<>();
      for (Node n : strata.get(depth).values()) {
        for (String e : n.expectedPolicySet) {
          parentIndex.computeIfAbsent(e, k -> new ArrayList<>()).add(n);
        }
      }
      depth++;
      strata.add(new LinkedHashMap<>());
    }
  }

  /** Whether the chain (leaf first, trust anchor last) satisfies the policy constraints. */
  static boolean valid(List<X509Cert> chain) {
    if (chain.size() == 1) {
      return true;
    }
    int n = chain.size() - 1;
    Graph pg = new Graph();
    long inhibitAnyPolicy = n + 1;
    long explicitPolicy = n + 1;
    long policyMapping = n + 1;

    for (int i = n - 1; i >= 0; i--) {
      X509Cert cert = chain.get(i);
      boolean isSelfSigned = Arrays.equals(cert.rawIssuer, cert.rawSubject);

      // 6.1.3 (e) and (f), as updated by RFC 9618.
      if (cert.policies == null) {
        pg = null;
      }
      if (explicitPolicy == 0 && pg == null) {
        return false;
      }

      if (pg != null) {
        pg.incrDepth();
        Set<String> policies = new HashSet<>();
        // 6.1.3 (d) (1)
        for (byte[] der : cert.policies) {
          String policy = X509Strings.latin1(der);
          policies.add(policy);
          if (policy.equals(ANY_POLICY)) {
            continue;
          }
          List<Node> parents = pg.parentsWithExpected(policy);
          if (parents.isEmpty()) {
            Node anyParent = pg.parentWithAnyPolicy();
            if (anyParent != null) {
              parents = List.of(anyParent);
            }
          }
          if (!parents.isEmpty()) {
            pg.insert(new Node(policy, parents));
          }
        }
        // 6.1.3 (d) (2): our chain index is inverted relative to the RFC's.
        if (policies.contains(ANY_POLICY) && (inhibitAnyPolicy > 0 || n - i < n && isSelfSigned)) {
          Map<String, List<Node>> missing = new LinkedHashMap<>();
          Map<String, Node> leaves = pg.leaves();
          for (Node p : pg.parents()) {
            for (String expected : p.expectedPolicySet) {
              if (!leaves.containsKey(expected)) {
                missing.computeIfAbsent(expected, k -> new ArrayList<>()).add(p);
              }
            }
          }
          for (Map.Entry<String, List<Node>> m : missing.entrySet()) {
            pg.insert(new Node(m.getKey(), m.getValue()));
          }
        }
        // 6.1.3 (d) (3)
        pg.prune();

        if (i != 0 && cert.policyMappings != null) {
          // 6.1.4 (b)
          Map<String, List<String>> mappings = new LinkedHashMap<>();
          for (byte[][] mapping : cert.policyMappings) {
            String issuer = X509Strings.latin1(mapping[0]);
            String subject = X509Strings.latin1(mapping[1]);
            if (policyMapping > 0) {
              if (issuer.equals(ANY_POLICY) || subject.equals(ANY_POLICY)) {
                return false;
              }
              mappings.computeIfAbsent(issuer, k -> new ArrayList<>()).add(subject);
            } else {
              pg.deleteLeaf(issuer);
            }
          }
          pg.prune();
          for (Map.Entry<String, List<String>> m : mappings.entrySet()) {
            Node matching = pg.leafWithPolicy(m.getKey());
            if (matching != null) {
              matching.expectedPolicySet = m.getValue();
            } else {
              Node anyLeaf = pg.leafWithPolicy(ANY_POLICY);
              if (anyLeaf != null) {
                Node mapped = new Node(m.getKey(), List.of(anyLeaf));
                mapped.expectedPolicySet = m.getValue();
                pg.insert(mapped);
              }
            }
          }
        }
      }

      if (i != 0) {
        // 6.1.4 (h)
        if (!isSelfSigned) {
          explicitPolicy = Math.max(0, explicitPolicy - 1);
          policyMapping = Math.max(0, policyMapping - 1);
          inhibitAnyPolicy = Math.max(0, inhibitAnyPolicy - 1);
        }
        // 6.1.4 (i) and (j)
        if ((cert.requireExplicitPolicy > 0 || cert.requireExplicitPolicyZero) && cert.requireExplicitPolicy < explicitPolicy) {
          explicitPolicy = cert.requireExplicitPolicy;
        }
        if ((cert.inhibitPolicyMapping > 0 || cert.inhibitPolicyMappingZero) && cert.inhibitPolicyMapping < policyMapping) {
          policyMapping = cert.inhibitPolicyMapping;
        }
        if ((cert.inhibitAnyPolicy > 0 || cert.inhibitAnyPolicyZero) && cert.inhibitAnyPolicy < inhibitAnyPolicy) {
          inhibitAnyPolicy = cert.inhibitAnyPolicy;
        }
      }
    }

    // 6.1.5 (a) and (b)
    if (explicitPolicy > 0) {
      explicitPolicy--;
    }
    if (chain.get(0).requireExplicitPolicyZero) {
      explicitPolicy = 0;
    }

    // 6.1.5 (g), as updated by RFC 9618. With anyPolicy as the initial user policy set, the
    // user-constrained policy set equals the authority-constrained one.
    boolean anyValidPolicy = false;
    if (pg != null) {
      anyValidPolicy = !pg.validPolicyNodes().isEmpty() || pg.leafWithPolicy(ANY_POLICY) != null;
    }
    return explicitPolicy != 0 || anyValidPolicy;
  }
}
