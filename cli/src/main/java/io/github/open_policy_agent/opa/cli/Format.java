package io.github.open_policy_agent.opa.cli;

final class Format {

  private Format() {}

  static String duration(long nanos) {
    if (nanos < 1000) {
      return nanos + "ns";
    } else if (nanos < 1000000) {
      return String.format("%.3fµs", nanos / 1000.0);
    } else if (nanos < 1000000000) {
      return String.format("%.3fms", nanos / 1000000.0);
    } else {
      return String.format("%.3fs", nanos / 1000000000.0);
    }
  }

  static String fileRef(int fileIdx, String[] fileNames) {
    if (fileIdx >= 0 && fileNames != null && fileIdx < fileNames.length) {
      return "/" + fileNames[fileIdx];
    }
    return "/<unknown>";
  }
}
