# X.509 test fixtures

Certificates used by `CryptoX509BuiltinsTest`. They were generated with Go 1.27.2 (the toolchain
OPA is built with) using `crypto/x509`, with hand-assembled DER where a case needs an encoding Go
does not produce itself (unusual name attribute types, time zone offsets, malformed extensions).

- `parse-*.pem`: inputs for `crypto.x509.parse_certificates`. The `.json` file of the same name is
  OPA's result for it: Go's `json.Marshal` of OPA's `extendedCert` values, re-serialized with
  sorted keys and no whitespace.
- `error-*.pem`: certificates Go refuses to parse (or, for `error-time-offset-24h`, to encode as
  JSON); the test asserts Go's error message.
- `verify-*.pem`: chains for `crypto.x509.parse_and_verify_certificates`, root first and leaf
  last. The expected outcome of each is Go's `x509.Certificate.Verify` with OPA's options;
  `verify-valid.json` is OPA's full result for `verify-valid.pem`. Some chains carry a signature
  whose encoding was assembled by hand (a PKCS#1 v1.5 DigestInfo without NULL parameters, an RSA-PSS
  salt of the wrong length, trailing data after an ECDSA signature, a non-canonical Ed25519 scalar);
  Go rejects all of them.

Unless a fixture's name says otherwise, certificates are valid from 2020-01-01 to 2120-01-01.
