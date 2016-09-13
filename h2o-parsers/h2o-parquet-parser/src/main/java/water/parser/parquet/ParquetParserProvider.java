package water.parser.parquet;

import water.Job;
import water.Key;
import water.fvec.ByteVec;
import water.parser.DefaultParserProviders;
import water.parser.ParseSetup;
import water.parser.Parser;
import water.parser.ParserInfo;
import water.parser.ParserProvider;

/**
 * Parquet parser provider.
 */
public class ParquetParserProvider extends ParserProvider {

  /* Setup for this parser */
  static ParserInfo PARQUET_INFO = new ParserInfo("PARQUET", DefaultParserProviders.MAX_CORE_PRIO + 30, true, false);

  @Override
  public ParserInfo info() {
    return PARQUET_INFO;
  }

  @Override
  public Parser createParser(ParseSetup setup, Key<Job> jobKey) {
    return new ParquetParser(setup, jobKey);
  }

  @Override
  public ParseSetup guessSetup(ByteVec vec, byte[] bits, byte sep, int ncols, boolean singleQuotes,
                               int checkHeader, String[] columnNames, byte[] columnTypes,
                               String[][] domains, String[][] naStrings) {
    return ParquetParser.guessSetup(vec, bits);
  }

  @Override
  public ParseSetup createParserSetup(Key[] inputs, ParseSetup requestedSetup) {
    // pass through for now
    return requestedSetup;
  }

}
