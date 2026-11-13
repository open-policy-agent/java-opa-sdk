package io.github.open_policy_agent.opa.ast.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TypeUtilsSurrogateTest {

  @Test
  void textWithoutLoneSurrogatesIsReturnedUnchanged() {
    final String text = "plain \uD83D\uDE00 text";
    assertThat(TypeUtils.replaceLoneSurrogates(text)).isSameAs(text);
  }

  @Test
  void loneSurrogatesBecomeReplacementCharacters() {
    assertThat(TypeUtils.replaceLoneSurrogates("a\uD83Db")).isEqualTo("a\uFFFDb");
    assertThat(TypeUtils.replaceLoneSurrogates("a\uDE00b")).isEqualTo("a\uFFFDb");
    assertThat(TypeUtils.replaceLoneSurrogates("\uD83D")).isEqualTo("\uFFFD");
    assertThat(TypeUtils.replaceLoneSurrogates("\uDE00\uD83D")).isEqualTo("\uFFFD\uFFFD");
  }

  @Test
  void validPairsSurviveAroundLoneSurrogates() {
    assertThat(TypeUtils.replaceLoneSurrogates("\uD83D\uD83D\uDE00\uDE00")).isEqualTo("\uFFFD\uD83D\uDE00\uFFFD");
    assertThat(TypeUtils.replaceLoneSurrogates("\uD83D\uDE00\uD83D")).isEqualTo("\uD83D\uDE00\uFFFD");
  }

  @Test
  void decodedStringReplacesLoneSurrogates() {
    assertThat(TypeUtils.decodedString("a\uD83D").getValue()).isEqualTo("a\uFFFD");
  }
}
