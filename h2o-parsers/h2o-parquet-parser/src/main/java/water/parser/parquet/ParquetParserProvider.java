package water.parser.parquet;

import org.apache.parquet.hadoop.VecParquetReader;
import water.DKV;
import water.Job;
import water.Key;
import water.fvec.ByteVec;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.*;

/**
 * Parquet parser provider.
 */
public class ParquetParserProvider extends ParserProvider {

  /* Setup for this parser */
  static ParserInfo PARQUET_INFO = new ParserInfo("PARQUET", DefaultParserProviders.MAX_CORE_PRIO + 30, true, false, false);

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
    // convert to an instance of ParquetParseSetup if needed
    ParseSetup setup = requestedSetup instanceof ParquetParser.ParquetParseSetup ?
            requestedSetup : requestedSetup.copyTo(new ParquetParser.ParquetParseSetup());
    // override incorrect type mappings (using the MessageFormat of the first file)
    Object frameOrVec = DKV.getGet(inputs[0]);
    ByteVec vec = (ByteVec) (frameOrVec instanceof Frame ? ((Frame) frameOrVec).vec(0) : frameOrVec);
    byte[] requestedTypes = setup.getColumnTypes();
    byte[] types = ParquetParser.correctTypeConversions(vec, requestedTypes);
    setup.setColumnTypes(types);
    for (int i = 0; i < types.length; i++)
      if (types[i] != requestedTypes[i])
        setup.addErrs(new ParseWriter.UnsupportedTypeOverride(inputs[0].toString(),Vec.TYPE_STR[types[i]], Vec.TYPE_STR[requestedTypes[i]], setup.getColumnNames()[i]));
    return setup;
  }

  @Override
  public ParseSetup setupLocal(Vec v, ParseSetup setup) {
    ((ParquetParser.ParquetParseSetup) setup).parquetMetadata = VecParquetReader.readFooterAsBytes(v);
    return setup;
  }

}
