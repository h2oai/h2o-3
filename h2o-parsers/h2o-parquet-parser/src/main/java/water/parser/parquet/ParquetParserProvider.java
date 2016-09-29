package water.parser.parquet;

import org.apache.parquet.hadoop.VecParquetReader;
import org.reflections.ReflectionUtils;
import water.H2O;
import water.Job;
import water.Key;
import water.fvec.ByteVec;
import water.fvec.FileVec;
import water.fvec.Vec;
import water.parser.DefaultParserProviders;
import water.parser.ParseSetup;
import water.parser.Parser;
import water.parser.ParserInfo;
import water.parser.ParserProvider;

import java.io.IOException;
import java.lang.reflect.Field;

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
    // pass through for now (just convert to an instance of ParquetParseSetup if needed)
    return requestedSetup instanceof ParquetParser.ParquetParseSetup ?
            requestedSetup : requestedSetup.copyTo(new ParquetParser.ParquetParseSetup());
  }

  @Override
  public ParseSetup setupLocal(Vec v, ParseSetup setup) {
    ((ParquetParser.ParquetParseSetup) setup).parquetMetadata = VecParquetReader.readFooterAsBytes(v);
    return setup;
  }

}
