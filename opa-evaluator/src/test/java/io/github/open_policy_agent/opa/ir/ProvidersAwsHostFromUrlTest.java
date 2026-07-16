package io.github.open_policy_agent.opa.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.open_policy_agent.opa.ast.builtin.impls.ProvidersAwsBuiltins;
import io.github.open_policy_agent.opa.ast.types.RegoBigInt;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.math.BigInteger;
import java.util.function.BiFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Verifies SigV4 host header reconstruction matches Go's {@code url.Host} (host:port when present). */
@DisplayName("providers.aws host header")
class ProvidersAwsHostFromUrlTest {

  private static final long SIGNING_TS = 1451311705000000000L;

  @Test
  void explicitPortIncludedInHostHeader() {
    assertHostHeader("https://example.com:8443/path", "example.com:8443");
  }

  @Test
  void portlessUrlUsesHostOnly() {
    assertHostHeader("http://example.com", "example.com");
  }

  private static void assertHostHeader(String url, String expectedHost) {
    BiFunction<EvaluationContext, RegoValue[], RegoValue> fn =
        new ProvidersAwsBuiltins().builtins().get("providers.aws.sign_req");

    RegoObject req = new RegoObject();
    req.setProp(new RegoString("method"), new RegoString("get"));
    req.setProp(new RegoString("url"), new RegoString(url));

    RegoObject config = new RegoObject();
    config.setProp(new RegoString("aws_access_key"), new RegoString("MYAWSACCESSKEYGOESHERE"));
    config.setProp(
        new RegoString("aws_secret_access_key"), new RegoString("MYAWSSECRETACCESSKEYGOESHERE"));
    config.setProp(new RegoString("aws_service"), new RegoString("s3"));
    config.setProp(new RegoString("aws_region"), new RegoString("us-east-1"));

    RegoValue[] args =
        new RegoValue[] {req, config, new RegoBigInt(BigInteger.valueOf(SIGNING_TS))};

    RegoObject signed = (RegoObject) fn.apply(null, args);
    RegoObject headers = (RegoObject) signed.getProperty("headers");
    String actualHost = ((RegoString) headers.getProperty("host")).getValue();
    assertEquals(expectedHost, actualHost);
  }
}
