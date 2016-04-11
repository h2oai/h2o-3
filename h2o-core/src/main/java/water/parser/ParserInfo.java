package water.parser;

/**
 * Created by michal on 4/15/16.
 */
public class ParserInfo {
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

  public boolean isParallelParseSupported() {
    return isParallelParseSupported;
  }
}
