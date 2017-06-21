package water.parser;

import water.H2O;
import water.Iced;

/**
 * A lightweight handle with basic information about parser.
 */
public class ParserInfo extends Iced<ParserInfo> {
  /** Name of parser */
  final String name;
  /** Priority of the parser which is used by guesser */
  final int prior;
  /** Does this parser support parallel parse. */
  final boolean isParallelParseSupported;
  /** Does this parser support stream parse. */
  final boolean isStreamParseSupported;
  /** Does this parser need post update of vector categoricals. */
  final boolean isDomainProvided;


  public ParserInfo(String name, int prior, boolean isParallelParseSupported, boolean isStreamParseSupported,
                    boolean isDomainProvided) {
    this.name = name;
    this.prior = prior;
    this.isParallelParseSupported = isParallelParseSupported;
    this.isStreamParseSupported = isStreamParseSupported;
    this.isDomainProvided = isDomainProvided;
  }
  public ParserInfo(String name, int prior, boolean isParallelParseSupported, boolean isDomainProvided) {
    this(name, prior, isParallelParseSupported, true, isDomainProvided);
  }
  public ParserInfo(String name, int prior, boolean isParallelParseSupported) {
    this(name, prior, isParallelParseSupported, false);
  }

  /** Get name for this parser */
  public String name() {
    return name;
  }

  /** Get order priority for this parser. */
  public int priority() {
    return prior;
  }

  // TOO_MANY_KEYS_COUNT specifies when to disable parallel parse. We want to cover a scenario when
  // we are working with too many keys made of small files - in this case the distributed parse
  // doesn't work well because of the way chunks are distributed to nodes. We should switch to a local
  // parse to make sure the work is uniformly distributed across the whole cluster.
  public static final int TOO_MANY_KEYS_COUNT = 128;
  // A file is considered to be small if it can fit into <SMALL_FILE_NCHUNKS> number of chunks.
  public static final int SMALL_FILE_NCHUNKS = 10;

  public enum ParseMethod {StreamParse, DistributedParse}
  /*
  localSetup.disableParallelParse ||
   */
  public ParseMethod parseMethod(int nfiles, int nchunks){
    if(isStreamParseSupported()) {
      if (!isParallelParseSupported() || (nfiles > TOO_MANY_KEYS_COUNT && (nchunks <= SMALL_FILE_NCHUNKS)))
        return ParseMethod.StreamParse;
    }
    if(isParallelParseSupported())
      return ParseMethod.DistributedParse;
    throw H2O.unimpl();
  }

  /** Does the parser support parallel parse? */
  public boolean isParallelParseSupported() {
    return isParallelParseSupported;
  }

  /** Does the parser support stream parse? */
  public boolean isStreamParseSupported() {
    return isStreamParseSupported;
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
