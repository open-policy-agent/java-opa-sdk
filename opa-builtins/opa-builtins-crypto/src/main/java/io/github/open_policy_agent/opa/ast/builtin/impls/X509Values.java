package io.github.open_policy_agent.opa.ast.builtin.impls;

import io.github.open_policy_agent.opa.ast.types.RegoArray;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoNull;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

/**
 * Converts parsed certificates to the Rego value OPA produces, which is Go's {@code json.Marshal}
 * of OPA's {@code extendedCert} (an {@code x509.Certificate} plus {@code URIStrings}). Go encodes
 * byte slices as base64, nil slices as null, and uses the Go field names as keys.
 */
final class X509Values {

  private X509Values() {}

  static RegoArray certificates(List<X509Cert> certs) {
    List<RegoValue> values = new ArrayList<>(certs.size());
    for (X509Cert c : certs) {
      values.add(certificate(c));
    }
    return new RegoArray(values);
  }

  private static RegoObject certificate(X509Cert c) {
    RegoObject o = new RegoObject();
    put(o, "Raw", bytes(c.raw));
    put(o, "RawTBSCertificate", bytes(c.rawTbsCertificate));
    put(o, "RawSubjectPublicKeyInfo", bytes(c.rawSubjectPublicKeyInfo));
    put(o, "RawSubject", bytes(c.rawSubject));
    put(o, "RawIssuer", bytes(c.rawIssuer));
    put(o, "RawSignatureAlgorithm", bytes(c.rawSignatureAlgorithm));
    put(o, "Signature", bytes(c.signature));
    put(o, "SignatureAlgorithm", number(c.signatureAlgorithm));
    put(o, "PublicKeyAlgorithm", number(c.publicKeyAlgorithm));
    put(o, "PublicKey", publicKey(c.publicKey));
    put(o, "Version", number(c.version));
    put(o, "SerialNumber", new RegoBigInt(c.serialNumber));
    put(o, "Issuer", name(c.issuer));
    put(o, "Subject", name(c.subject));
    put(o, "NotBefore", new RegoString(c.notBefore.toJson()));
    put(o, "NotAfter", new RegoString(c.notAfter.toJson()));
    put(o, "KeyUsage", number(c.keyUsage));
    put(o, "Extensions", list(c.extensions, X509Values::extension));
    put(o, "ExtraExtensions", RegoNull.INSTANCE);
    put(o, "UnhandledCriticalExtensions", list(c.unhandledCriticalExtensions, X509Values::oid));
    put(o, "ExtKeyUsage", list(c.extKeyUsage, X509Values::number));
    put(o, "UnknownExtKeyUsage", list(c.unknownExtKeyUsage, X509Values::oid));
    put(o, "BasicConstraintsValid", RegoBoolean.of(c.basicConstraintsValid));
    put(o, "IsCA", RegoBoolean.of(c.isCa));
    put(o, "MaxPathLen", number(c.maxPathLen));
    put(o, "MaxPathLenZero", RegoBoolean.of(c.maxPathLenZero));
    put(o, "SubjectKeyId", bytes(c.subjectKeyId));
    put(o, "AuthorityKeyId", bytes(c.authorityKeyId));
    put(o, "OCSPServer", list(c.ocspServer, X509Values::byteString));
    put(o, "IssuingCertificateURL", list(c.issuingCertificateUrl, X509Values::byteString));
    put(o, "DNSNames", list(c.dnsNames, X509Values::byteString));
    put(o, "EmailAddresses", list(c.emailAddresses, X509Values::byteString));
    put(o, "IPAddresses", list(c.ipAddresses, ip -> new RegoString(X509Strings.ipToString(ip))));
    put(o, "URIs", list(c.uris, X509Values::url));
    put(o, "PermittedDNSDomainsCritical", RegoBoolean.of(c.permittedDnsDomainsCritical));
    put(o, "PermittedDNSDomains", list(c.permittedDnsDomains, X509Values::byteString));
    put(o, "ExcludedDNSDomains", list(c.excludedDnsDomains, X509Values::byteString));
    put(o, "PermittedIPRanges", list(c.permittedIpRanges, X509Values::ipNet));
    put(o, "ExcludedIPRanges", list(c.excludedIpRanges, X509Values::ipNet));
    put(o, "PermittedEmailAddresses", list(c.permittedEmailAddresses, X509Values::byteString));
    put(o, "ExcludedEmailAddresses", list(c.excludedEmailAddresses, X509Values::byteString));
    put(o, "PermittedURIDomains", list(c.permittedUriDomains, X509Values::byteString));
    put(o, "ExcludedURIDomains", list(c.excludedUriDomains, X509Values::byteString));
    put(o, "CRLDistributionPoints", list(c.crlDistributionPoints, X509Values::byteString));
    put(o, "PolicyIdentifiers", list(c.policyIdentifiers, X509Values::oid));
    put(o, "Policies", list(c.policies, der -> new RegoString(X509Parser.oidDerString(der))));
    put(o, "InhibitAnyPolicy", number(c.inhibitAnyPolicy));
    put(o, "InhibitAnyPolicyZero", RegoBoolean.of(c.inhibitAnyPolicyZero));
    put(o, "InhibitPolicyMapping", number(c.inhibitPolicyMapping));
    put(o, "InhibitPolicyMappingZero", RegoBoolean.of(c.inhibitPolicyMappingZero));
    put(o, "RequireExplicitPolicy", number(c.requireExplicitPolicy));
    put(o, "RequireExplicitPolicyZero", RegoBoolean.of(c.requireExplicitPolicyZero));
    put(o, "PolicyMappings", list(c.policyMappings, X509Values::policyMapping));
    put(o, "URIStrings", list(c.uris, u -> byteString(u.toString())));
    return o;
  }

  private static void put(RegoObject o, String key, RegoValue value) {
    o.setProp(new RegoString(key), value);
  }

  /** An object from alternating keys and values. */
  private static RegoObject object(Object... keysAndValues) {
    RegoObject o = new RegoObject();
    if ((keysAndValues.length & 1) != 0) {
      throw new IllegalArgumentException("X.509 object requires key/value pairs");
    }
    for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
      put(o, (String) keysAndValues[i], (RegoValue) keysAndValues[i + 1]);
    }
    return o;
  }

  private static RegoValue bytes(byte[] b) {
    return b == null ? RegoNull.INSTANCE : new RegoString(Base64.getEncoder().encodeToString(b));
  }

  private static RegoValue number(long v) {
    return new RegoBigInt(BigInteger.valueOf(v));
  }

  /** A Go string with arbitrary bytes, held as ISO-8859-1. */
  private static RegoValue byteString(String latin1) {
    return new RegoString(X509Strings.fromLatin1Bytes(latin1));
  }

  private static RegoValue oid(int[] oid) {
    List<RegoValue> arcs = new ArrayList<>(oid.length);
    for (int arc : oid) {
      arcs.add(number(arc));
    }
    return new RegoArray(arcs);
  }

  private static <T> RegoValue list(List<T> items, Function<T, RegoValue> f) {
    if (items == null) {
      return RegoNull.INSTANCE;
    }
    List<RegoValue> values = new ArrayList<>(items.size());
    for (T item : items) {
      values.add(f.apply(item));
    }
    return new RegoArray(values);
  }

  private static RegoValue name(X509Cert.Name n) {
    return object(
        "Country", list(n.country, RegoString::new),
        "Organization", list(n.organization, RegoString::new),
        "OrganizationalUnit", list(n.organizationalUnit, RegoString::new),
        "Locality", list(n.locality, RegoString::new),
        "Province", list(n.province, RegoString::new),
        "StreetAddress", list(n.streetAddress, RegoString::new),
        "PostalCode", list(n.postalCode, RegoString::new),
        "SerialNumber", new RegoString(n.serialNumber),
        "CommonName", new RegoString(n.commonName),
        "Names", list(n.names, atv -> object("Type", oid(atv.type()), "Value", attributeValue(atv.value()))),
        "ExtraNames", RegoNull.INSTANCE);
  }

  private static RegoValue attributeValue(Object v) {
    if (v == null) {
      return RegoNull.INSTANCE;
    } else if (v instanceof String s) {
      return new RegoString(s);
    } else if (v instanceof Long l) {
      return number(l);
    } else if (v instanceof Boolean b) {
      return RegoBoolean.of(b);
    } else if (v instanceof byte[] b) {
      return bytes(b);
    } else if (v instanceof int[] oid) {
      return oid(oid);
    } else if (v instanceof X509Time t) {
      return new RegoString(t.toJson());
    } else if (v instanceof X509Der.BitString bs) {
      return object("Bytes", bytes(bs.bytes()), "BitLength", number(bs.bitLength()));
    }
    X509Cert.RawValue raw = (X509Cert.RawValue) v;
    return object(
        "Class", number(raw.cls()),
        "Tag", number(raw.tag()),
        "IsCompound", RegoBoolean.of(raw.compound()),
        "Bytes", bytes(raw.bytes()),
        "FullBytes", bytes(raw.fullBytes()));
  }

  private static RegoValue extension(X509Cert.Extension e) {
    return object("Id", oid(e.id()), "Critical", RegoBoolean.of(e.critical()), "Value", bytes(e.value()));
  }

  private static RegoValue ipNet(X509Cert.IpNet n) {
    return object("IP", new RegoString(X509Strings.ipToString(n.ip())), "Mask", bytes(n.mask()));
  }

  private static RegoValue url(X509Url u) {
    return object(
        "Scheme", byteString(u.scheme),
        "Opaque", byteString(u.opaque),
        // Go's url.Userinfo has only unexported fields, so it encodes as an empty object.
        "User", u.user == null ? RegoNull.INSTANCE : new RegoObject(),
        "Host", byteString(u.host),
        "Path", byteString(u.path),
        "Fragment", byteString(u.fragment),
        "RawQuery", byteString(u.rawQuery),
        "RawPath", byteString(u.rawPath),
        "RawFragment", byteString(u.rawFragment),
        "ForceQuery", RegoBoolean.of(u.forceQuery),
        "OmitHost", RegoBoolean.of(u.omitHost));
  }

  private static RegoValue policyMapping(byte[][] mapping) {
    return object(
        "IssuerDomainPolicy", new RegoString(X509Parser.oidDerString(mapping[0])),
        "SubjectDomainPolicy", new RegoString(X509Parser.oidDerString(mapping[1])));
  }

  private static RegoValue publicKey(Object key) {
    if (key instanceof X509Cert.RsaKey k) {
      return object("N", new RegoBigInt(k.n()), "E", number(k.e()));
    } else if (key instanceof X509Cert.EcKey k) {
      // Go's elliptic.Curve implementations have no exported fields.
      return object("Curve", new RegoObject(), "X", new RegoBigInt(k.x()), "Y", new RegoBigInt(k.y()));
    } else if (key instanceof X509Cert.Ed25519Key k) {
      return bytes(k.key());
    } else if (key instanceof X509Cert.DsaKey k) {
      return object("P", new RegoBigInt(k.p()), "Q", new RegoBigInt(k.q()), "G", new RegoBigInt(k.g()), "Y", new RegoBigInt(k.y()));
    }
    // An ML-DSA key has no exported fields; an unknown key is nil.
    return key instanceof X509Cert.MlDsaKey ? new RegoObject() : RegoNull.INSTANCE;
  }
}
