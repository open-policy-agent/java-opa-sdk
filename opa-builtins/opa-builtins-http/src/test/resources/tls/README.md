# TLS test material

Used by `HttpBuiltinsTlsTest`. Everything here is for tests only and valid for 100 years.

- `ca.pem`: CA that signed the server and client certificates.
- `other-ca.pem`: an unrelated CA, for "wrong CA" cases.
- `server.p12` (password `changeit`): server certificate for `localhost` / `127.0.0.1`, with its key.
- `client-rsa.pem` with `client-rsa-pkcs8.key` and `client-rsa-pkcs1.key`: the same RSA key as PKCS#8 and PKCS#1.
- `client-ec.pem` with `client-ec-pkcs8.key` and `client-ec-sec1.key`: the same EC key as PKCS#8 and SEC1.

Generated with OpenSSL, for example:

```sh
openssl req -x509 -newkey rsa:2048 -nodes -keyout ca.key -out ca.pem -days 36500 -subj "/CN=java-opa-sdk test CA" \
  -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign"
openssl req -newkey rsa:2048 -nodes -keyout server.key -out server.csr -subj "/CN=localhost"
openssl x509 -req -in server.csr -CA ca.pem -CAkey ca.key -CAcreateserial -out server.pem -days 36500 \
  -extfile <(printf "subjectAltName=DNS:localhost,IP:127.0.0.1\nextendedKeyUsage=serverAuth")
openssl pkcs12 -export -in server.pem -inkey server.key -certfile ca.pem -out server.p12 -passout pass:changeit
openssl genrsa -traditional -out client-rsa-pkcs1.key 2048
openssl pkcs8 -topk8 -nocrypt -in client-rsa-pkcs1.key -out client-rsa-pkcs8.key
openssl ecparam -name prime256v1 -genkey -noout -out client-ec-sec1.key
openssl pkcs8 -topk8 -nocrypt -in client-ec-sec1.key -out client-ec-pkcs8.key
# client certificates: a CSR for each key, signed by the CA with extendedKeyUsage=clientAuth
```
