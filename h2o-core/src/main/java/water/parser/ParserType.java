package water.parser;

/** Which parse flavor is being used, and does it support parallel parsing.
 */
public enum ParserType {
  AUTO(false), ARFF(true), XLS(false), XLSX(false), CSV(true), SVMLight(true);
  final boolean _parallelParseSupported;
  private ParserType( boolean par ) { _parallelParseSupported = par; }
  String toString( int ncols, byte separator ) {
    if( this==AUTO ) return "";
    StringBuilder sb = new StringBuilder(name());
    sb.append(" data with ").append(ncols).append(" columns");
    if( this==CSV || this==ARFF  && separator != ParseSetup.AUTO_SEP) sb.append(" using '").append((char)separator).append("' (\\").append(separator).append("04d) as separator.");
    return sb.toString();
  }
}
