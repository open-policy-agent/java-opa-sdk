package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.open_policy_agent.opa.ast.types.RegoBoolean;
import io.github.open_policy_agent.opa.ast.types.RegoObject;
import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import org.junit.jupiter.api.Test;

public class UriBuiltinsTest {

  private final UriBuiltins uriBuiltins = new UriBuiltins();
  private final EvaluationContext context = new EvaluationContext.Builder().build();

  @Test
  public void parseLowercasesScheme() {
    RegoValue[] args = {new RegoString("HTTP://EXAMPLE.COM")};

    RegoObject result = uriBuiltins.parse(context, args);

    assertEquals(new RegoString("http"), result.getProperty("scheme"));
  }

  @Test
  public void parsePreservesRegistryBasedHostnameAndPort() {
    RegoValue[] args = {new RegoString("http://my_host:8080/path")};

    RegoObject result = uriBuiltins.parse(context, args);

    assertEquals(new RegoString("my_host"), result.getProperty("hostname"));
    assertEquals(new RegoString("8080"), result.getProperty("port"));
  }

  @Test
  public void parseAcceptsSpaceInPath() {
    RegoValue[] args = {new RegoString("https://example.com/path with spaces")};

    RegoObject result = uriBuiltins.parse(context, args);

    assertEquals(new RegoString("/path with spaces"), result.getProperty("path"));
    assertEquals(new RegoString("/path with spaces"), result.getProperty("raw_path"));
  }

  @Test
  public void parseAcceptsPipeInPath() {
    RegoValue[] args = {new RegoString("http://example.com/p|pe")};

    RegoObject result = uriBuiltins.parse(context, args);

    assertEquals(new RegoString("/p|pe"), result.getProperty("path"));
    assertEquals(new RegoString("/p|pe"), result.getProperty("raw_path"));
  }

  @Test
  public void isValidAcceptsSpaceInPath() {
    RegoValue[] args = {new RegoString("https://example.com/path with spaces")};

    RegoBoolean result = uriBuiltins.isValid(context, args);

    assertEquals(RegoBoolean.TRUE, result);
  }

  @Test
  public void isValidAcceptsPipeInPath() {
    RegoValue[] args = {new RegoString("http://example.com/p|pe")};

    RegoBoolean result = uriBuiltins.isValid(context, args);

    assertEquals(RegoBoolean.TRUE, result);
  }

  @Test
  public void isValidRejectsControlCharacters() {
    RegoValue[] args = {new RegoString("https://example.com/path\nwith-newline")};

    RegoBoolean result = uriBuiltins.isValid(context, args);

    assertEquals(RegoBoolean.FALSE, result);
  }

  @Test
  public void isValidRejectsInvalidPercentEscape() {
    RegoValue[] args = {new RegoString("https://example.com/path%ZZ")};

    RegoBoolean result = uriBuiltins.isValid(context, args);

    assertEquals(RegoBoolean.FALSE, result);
  }
}
