/*
 * Entry point for the regoj CLI.
 */
package io.github.open_policy_agent.opa.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "regoj",
    mixinStandardHelpOptions = true,
    subcommands = {Eval.class})
public class Regoj {
  public static void main(String[] args) {
    int exitCode =
        new CommandLine(new Regoj()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
    System.exit(exitCode);
  }
}
