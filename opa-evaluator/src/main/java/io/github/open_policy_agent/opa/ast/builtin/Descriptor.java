package io.github.open_policy_agent.opa.ast.builtin;

import java.util.List;

/** Descriptor for an OPA builtin function, matching the format from opa-cap.json */
public class Descriptor {
  public String name;

  public String description;

  public List<String> categories;

  public Declaration decl;

  public String infix;

  public Boolean nondeterministic;

  public Descriptor() {}

  public Descriptor(String name) {
    this.name = name;
  }

  public Descriptor(String name, Declaration decl) {
    this.name = name;
    this.decl = decl;
  }

  public static class Declaration {
    public String type = "function";

    public List<Argument> args;

    public Argument result;

    public Declaration() {}

    public Declaration(List<Argument> args, Argument result) {
      this.args = args;
      this.result = result;
    }
  }

  public static class Argument {
    public String type;

    public String name;

    public String description;

    public List<Argument> of;

    public Dynamic dynamic;

    public Argument() {}

    public Argument(String type) {
      this.type = type;
    }

    public Argument(String type, String name, String description) {
      this.type = type;
      this.name = name;
      this.description = description;
    }

    public Argument(List<Argument> of) {
      this.type = "any";
      this.of = of;
    }
  }

  /** Represents dynamic type information for collections */
  public static class Dynamic {
    public String type;

    public Argument key;

    public Argument value;

    public Dynamic() {}

    public Dynamic(String type) {
      this.type = type;
    }

    public Dynamic(Argument key, Argument value) {
      this.key = key;
      this.value = value;
    }
  }
}
