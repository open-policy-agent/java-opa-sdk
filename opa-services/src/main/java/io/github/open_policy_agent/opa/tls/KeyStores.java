package io.github.open_policy_agent.opa.tls;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

/** Internal helpers for building in-memory PKCS12 keystores from parsed PEM material. */
final class KeyStores {

  static final String ALIAS = "key";
  private static final char[] EMPTY_PASSWORD = new char[0];

  private KeyStores() {}

  /**
   * Build a {@link KeyManager}{@code []} from a single cert chain + private key, wrapped in an
   * in-memory PKCS12 store. The password is empty since the store never leaves process memory.
   */
  static KeyManager[] keyManagers(List<X509Certificate> chain, PrivateKey key)
      throws GeneralSecurityException, IOException {
    KeyStore ks = KeyStore.getInstance("PKCS12");
    ks.load(null, EMPTY_PASSWORD);
    ks.setKeyEntry(ALIAS, key, EMPTY_PASSWORD, chain.toArray(new X509Certificate[0]));
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(ks, EMPTY_PASSWORD);
    return kmf.getKeyManagers();
  }
}
