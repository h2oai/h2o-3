package water.parser;

import water.Iced;

/**
 * A lightweight handle with basic information about parser.
 */
public class ParserInfo extends Iced<ParserInfo> {
  final String name;
  final int prior;
  final boolean isParallelParseSupported;

  public ParserInfo(String name, int prior, boolean isParallelParseSupported) {
    this.name = name;
    this.prior = prior;
    this.isParallelParseSupported = isParallelParseSupported;
  }

  /** Get name for this parser */
  public String name() {
    return name;
  }

  /** Get order priority for this parser. */
  public int priority() {
    return prior;
  }

  /** Does the parser support parallel parse? */
  public boolean isParallelParseSupported() {
    return isParallelParseSupported;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ParserInfo that = (ParserInfo) o;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return "ParserInfo{" +
           "name='" + name + '\'' +
           ", prior=" + prior +
           ", isParallelParseSupported=" + isParallelParseSupported +
           '}';
  }
}
