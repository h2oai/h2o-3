package water.parser;

/** Which parse flavor is being used, and does it support parallel parsing.
 *
 * @deprecated only used to preserve compatibility with REST layer
 */
@Deprecated
public enum ParserType {
  GUESS(false),
  ARFF(true),
  XLS(false),
  XLSX(false),
  CSV(true),
  SVMLight(true),
  AVRO(true);

  final boolean _parallelParseSupported;

  ParserType( boolean par ) { _parallelParseSupported = par; }
  String toString( int ncols, byte separator ) {
    if( this== GUESS) return "";
    StringBuilder sb = new StringBuilder(name());
    sb.append(" data with ").append(ncols).append(" columns");
    if( this==CSV || this==ARFF  && separator != ParseSetup.GUESS_SEP) sb.append(" using '").append((char)separator).append("' (\\").append(separator).append("04d) as separator.");
    return sb.toString();
  }
}
