package water.parser;

import water.Iced;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Vec;

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
  /** Does this parser need post update of vector categoricals. */
  final boolean isDomainProvided;

  public byte[] strToColumnTypes(int numcols,String[] strs) {
    if (strs == null) return null;
    assert numcols == strs.length;
    byte[] types = new byte[strs.length];
    for(int i=0; i< types.length;i++) {
      switch (strs[i].toLowerCase()) {
        case "unknown": types[i] = Vec.T_BAD;  break;
        case "uuid":    types[i] = Vec.T_UUID; break;
        case "string":  types[i] = Vec.T_STR;  break;
        case "float":
        case "real":
        case "double":
        case "int":
        case "numeric": types[i] = Vec.T_NUM;  break;
        case "categorical":
        case "factor":
        case "enum":    types[i] = Vec.T_CAT;  break;
        case "time":    types[i] = Vec.T_TIME; break;
        default:        types[i] = Vec.T_BAD;
          throw new H2OIllegalArgumentException("Provided column type "+ strs[i] + " is unknown.  Cannot proceed with parse due to invalid argument.");
      }
    }
    return types;
  }

  public ParserInfo(String name, int prior, boolean isParallelParseSupported, boolean isDomainProvided) {
    this.name = name;
    this.prior = prior;
    this.isParallelParseSupported = isParallelParseSupported;
    this.isDomainProvided = isDomainProvided;
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
