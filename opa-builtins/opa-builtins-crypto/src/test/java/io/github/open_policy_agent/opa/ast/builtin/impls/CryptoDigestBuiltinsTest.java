package io.github.open_policy_agent.opa.ast.builtin.impls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.open_policy_agent.opa.ast.types.RegoString;
import io.github.open_policy_agent.opa.ast.types.RegoValue;
import io.github.open_policy_agent.opa.rego.EvaluationContext;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// Expected values from opa 1.21.1.
class CryptoDigestBuiltinsTest {

  private static final RegoString KEY = new RegoString("k\u00E9y");
  private final CryptoBuiltins builtins = new CryptoBuiltins();
  private final EvaluationContext ctx = new EvaluationContext.Builder().build();

  static Stream<Arguments> digestCases() {
    return Stream.of(
        Arguments.of("crypto.md5", "", "d41d8cd98f00b204e9800998ecf8427e"),
        Arguments.of("crypto.md5", "a", "0cc175b9c0f1b6a831c399e269772661"),
        Arguments.of("crypto.md5", "hello", "5d41402abc4b2a76b9719d911017c592"),
        Arguments.of("crypto.md5", "\u00E9", "66ddcd97cfdeabb2f6fb8a999b4bc76f"),
        Arguments.of("crypto.md5", "\u00FF\u0080", "0d5b834db15223d4c98a188d6ab44a1c"),
        Arguments.of("crypto.md5", "\uD83D\uDE00", "2a02eac39d716a70ecf37579185927b6"),
        Arguments.of("crypto.md5", "The quick brown fox jumps over the lazy dog", "9e107d9d372bb6826bd81d3542a419d6"),
        Arguments.of("crypto.md5", "x".repeat(1000), "398533d48111e9f664b1f64cb10c4b63"),
        Arguments.of("crypto.sha1", "", "da39a3ee5e6b4b0d3255bfef95601890afd80709"),
        Arguments.of("crypto.sha1", "a", "86f7e437faa5a7fce15d1ddcb9eaeaea377667b8"),
        Arguments.of("crypto.sha1", "hello", "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d"),
        Arguments.of("crypto.sha1", "\u00E9", "bf15be717ac1b080b4f1c456692825891ff5073d"),
        Arguments.of("crypto.sha1", "\u00FF\u0080", "fc48850f133b9e4a80a5dfddc353f2c1e2a17245"),
        Arguments.of("crypto.sha1", "\uD83D\uDE00", "9c533688a979a858cbd6a43c9f91aba624651f18"),
        Arguments.of("crypto.sha1", "The quick brown fox jumps over the lazy dog", "2fd4e1c67a2d28fced849ee1bb76e7391b93eb12"),
        Arguments.of("crypto.sha1", "x".repeat(1000), "c3efa690fa3fdd2e2526853eed670538ea127638"),
        Arguments.of("crypto.sha256", "", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        Arguments.of("crypto.sha256", "a", "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb"),
        Arguments.of("crypto.sha256", "hello", "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"),
        Arguments.of("crypto.sha256", "\u00E9", "4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c"),
        Arguments.of("crypto.sha256", "\u00FF\u0080", "38b4df498103e63b86feca1ba23ca7e56f71baa02da2bb5dd1010b04736cc92b"),
        Arguments.of("crypto.sha256", "\uD83D\uDE00", "f0443a342c5ef54783a111b51ba56c938e474c32324d90c3a60c9c8e3a37e2d9"),
        Arguments.of("crypto.sha256", "The quick brown fox jumps over the lazy dog", "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592"),
        Arguments.of("crypto.sha256", "x".repeat(1000), "44f8354494a5ba03ba1792a8d3e9c534c47a9181980fde7a3f44b06ef2ae7c7f"));
  }

  static Stream<Arguments> hmacCases() {
    return Stream.of(
        Arguments.of("crypto.hmac.md5", "", "e4396df7df7d799b833d931d4c351b3d"),
        Arguments.of("crypto.hmac.md5", "a", "d773709bf4cca8376059e90b1c00efc1"),
        Arguments.of("crypto.hmac.md5", "hello", "856b7e3c9cf1a6bee6617c0972b994a4"),
        Arguments.of("crypto.hmac.md5", "\u00E9", "e119dc7c67e93fcf644d298f18ba2d83"),
        Arguments.of("crypto.hmac.md5", "\u00FF\u0080", "e7c0160e9d088713dfbdc17512b1cc27"),
        Arguments.of("crypto.hmac.md5", "\uD83D\uDE00", "2c8a228aa6335dbe29ad2e2eacb6abbb"),
        Arguments.of("crypto.hmac.md5", "The quick brown fox jumps over the lazy dog", "6d1f0cbfa01e65bda4fc2f6ad5340c48"),
        Arguments.of("crypto.hmac.md5", "x".repeat(1000), "ac6736fe5b53b539a57bfa0079a5e2fa"),
        Arguments.of("crypto.hmac.sha1", "", "9b1dd898e17164ba534a935e6cafae1ce33d21c8"),
        Arguments.of("crypto.hmac.sha1", "a", "2f569904c7c2a405f276b4fe5c4dfdb55c7ced6e"),
        Arguments.of("crypto.hmac.sha1", "hello", "6e55cad0809022c1859ed7c8b36d84f2293f892c"),
        Arguments.of("crypto.hmac.sha1", "\u00E9", "3cc801885bcf785eb53aaecf69d23194015c41df"),
        Arguments.of("crypto.hmac.sha1", "\u00FF\u0080", "0f6f64fc16dcbc4e9552e14381dfd9c3059d8a8b"),
        Arguments.of("crypto.hmac.sha1", "\uD83D\uDE00", "f9659c96faccfa4aec65f9267a4969041e59f253"),
        Arguments.of("crypto.hmac.sha1", "The quick brown fox jumps over the lazy dog", "5a446c3ed2564a5f147058741405fd4c0b44c63c"),
        Arguments.of("crypto.hmac.sha1", "x".repeat(1000), "5f5454c484c3abb02631fefdb9918bd110e5ce44"),
        Arguments.of("crypto.hmac.sha256", "", "c9aa0ba82c3959fb40e2be8b8e2ff5941d9964437c93114d65f2fb4b1331aff3"),
        Arguments.of("crypto.hmac.sha256", "a", "093462ec5c96f373410c2d841788bfd98ed56bf641fb7071ab6b2ca27258c82c"),
        Arguments.of("crypto.hmac.sha256", "hello", "79cfeb6965bcd287b61f0b49f367825f693bef587bab67607c777cac7abd7f4c"),
        Arguments.of("crypto.hmac.sha256", "\u00E9", "120794fd657d8a54faabf6ce14f05fcaaecf8c2db5e06fa50d5924b75789900c"),
        Arguments.of("crypto.hmac.sha256", "\u00FF\u0080", "8757198201e57a291963d71069a8d069cfad3feb6ffafdac1e8fda01fa9b6f8b"),
        Arguments.of("crypto.hmac.sha256", "\uD83D\uDE00", "eeb54c84dccc0fd0254b6b4b07d2e2d806b1c31288e5192f878e8e55635c19e7"),
        Arguments.of("crypto.hmac.sha256", "The quick brown fox jumps over the lazy dog", "422553336192a4ed06a5282d57747252f6c6254261954b99de5a63037f52166b"),
        Arguments.of("crypto.hmac.sha256", "x".repeat(1000), "d0ddafe47b542063fc399047c39bfea51726130a35adc63a346a558740406f0e"),
        Arguments.of("crypto.hmac.sha512", "", "6eda71efece1d7db68e5e97b0ad7b5dc28e264032cd0b0cf40fef394d777323798034f251be27275b7add17ce7b181ba6190f62581f9a866482b57144228ced2"),
        Arguments.of("crypto.hmac.sha512", "a", "63b589b444b146ba53972966423d58e926955bd387a14c4634d26f4db9ca05bf4a021138dc32107521113db2216286e5de332456535ef5120151cfd68f46ff38"),
        Arguments.of("crypto.hmac.sha512", "hello", "9cacc4af1d36e37f5a7d2afe27c284fef5a1cdc41b27e7a14d90043e0f3e2dd1d350aa9224e7f55d28d1637925946f361d0af6c1d173344c7c5e3770bf87979d"),
        Arguments.of("crypto.hmac.sha512", "\u00E9", "e3505ccbf5343b25c43864d449947c1f3e18a1c8cceb6988f88a04118541621a0b6064567b29ac911f5b670a4fa60ed49bedfa5f6406249995e5e60c6f62bfad"),
        Arguments.of("crypto.hmac.sha512", "\u00FF\u0080", "ff20d2229f89cccf52e86886396e08a46340d28760ca6eda01ebfee566ee29ed034fdffb20970a380f4db68953787105c1187f1798e9ba1ad504da27f32bf58d"),
        Arguments.of("crypto.hmac.sha512", "\uD83D\uDE00", "85bdec61bc6982036ec89da1772bec1a930cb10b838b5c2f0238742643b2441f2cc27fce15d7cb8b5fb9fabf67f42ae6f56e459f8c2c0352a8846f91e72ab3c9"),
        Arguments.of("crypto.hmac.sha512", "The quick brown fox jumps over the lazy dog", "89054b2e4e1c2eacbf2bf33cd9f87b5ebfb6472d603326a3e96018df720c6a1b3e54ac65c8b467de6d1d69253b1c9ee249311d6c6f5927e21b63e9cbc0799c6b"),
        Arguments.of("crypto.hmac.sha512", "x".repeat(1000), "50827db3453fc645b3e0155c7382825cede58ceafbbd9f4d07f5bdf223594bb6b52b148697b189a6284b82b75987361b25bbe876dc471c8d202ce84e126d93c2"));
  }

  @ParameterizedTest(name = "{0}({1})")
  @MethodSource("digestCases")
  void digestMatchesOpa(String builtin, String input, String expected) {
    final RegoValue result = builtins.builtins().get(builtin).apply(ctx, new RegoValue[] {new RegoString(input)});

    assertEquals(new RegoString(expected), result);
  }

  @ParameterizedTest(name = "{0}({1})")
  @MethodSource("hmacCases")
  void hmacMatchesOpa(String builtin, String input, String expected) {
    final RegoValue result = builtins.builtins().get(builtin).apply(ctx, new RegoValue[] {new RegoString(input), KEY});

    assertEquals(new RegoString(expected), result);
  }
}
