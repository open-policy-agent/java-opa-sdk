package io.github.open_policy_agent.opa.ir.policy;

import java.util.List;
import java.util.Objects;

public class Static {
  private List<StringConst> strings;

  // JSON key is snake_case "builtin_funcs"; the mapper's SNAKE_CASE naming strategy maps it.
  private List<BuiltinFunc> builtinFuncs;

  private List<StringConst> files;

  public Static() {}

  public Static(
      List<StringConst> strings, List<BuiltinFunc> builtinFuncs, List<StringConst> files) {
    this.strings = strings;
    this.builtinFuncs = builtinFuncs;
    this.files = files;
  }

  public List<StringConst> getStrings() {
    return strings;
  }

  public void setStrings(List<StringConst> strings) {
    this.strings = strings;
  }

  public List<StringConst> getFiles() {
    return files;
  }

  public void setFiles(List<StringConst> files) {
    this.files = files;
  }

  public List<BuiltinFunc> getBuiltinFuncs() {
    return builtinFuncs;
  }

  public void setBuiltinFuncs(List<BuiltinFunc> builtinFuncs) {
    this.builtinFuncs = builtinFuncs;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Static aStatic = (Static) o;

    if (!Objects.equals(strings, aStatic.strings)) return false;
    if (!Objects.equals(builtinFuncs, aStatic.builtinFuncs)) return false;
    return Objects.equals(files, aStatic.files);
  }

  @Override
  public int hashCode() {
    int result = strings != null ? strings.hashCode() : 0;
    result = 31 * result + (builtinFuncs != null ? builtinFuncs.hashCode() : 0);
    result = 31 * result + (files != null ? files.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Static{"
        + "strings="
        + strings
        + ", builtinFuncs="
        + builtinFuncs
        + ", files="
        + files
        + '}';
  }
}
