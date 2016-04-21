package water.parser.avro;

import water.DKV;
import water.Iced;
import water.Job;
import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.ByteVec;
import water.fvec.Frame;
import water.parser.DefaultParserProviders;
import water.parser.ParseSetup;
import water.parser.Parser;
import water.parser.ParserInfo;
import water.parser.ParserProvider;

/**
 * Avro parser provider.
 */
public class AvroParserProvider implements ParserProvider {

  /* Setup for this parser */
  static ParserInfo AVRO_INFO = new ParserInfo("AVRO", DefaultParserProviders.MAX_CORE_PRIO + 10, true);

  @Override
  public ParserInfo info() {
    return AVRO_INFO;
  }

  @Override
  public Parser createParser(ParseSetup setup, Key<Job> jobKey) {
    return new AvroParser(setup, jobKey);
  }

  @Override
  public ParseSetup guessSetup(byte[] bits, byte sep, int ncols, boolean singleQuotes,
                               int checkHeader, String[] columnNames, byte[] columnTypes,
                               String[][] domains, String[][] naStrings) {
    return AvroParser.guessSetup(bits);
  }

  @Override
  public ParseSetup createParserSetup(Key[] inputs, ParseSetup requiredSetup) {
    // We need to get header of Avro file to configure the Avro parser.
    // The code expects that inputs are consistent and extract only header
    // from the first file.
    // Also expect that files are not compressed
    assert inputs != null && inputs.length > 0 : "Inputs cannot be empty!";
    Key firstInput = inputs[0];
    Iced ice = DKV.getGet(firstInput);
    if (ice == null) throw new H2OIllegalArgumentException("Missing data", "Did not find any data under key " + firstInput);
    ByteVec bv = (ByteVec)(ice instanceof ByteVec ? ice : ((Frame)ice).vecs()[0]);
    byte [] bits = bv.getFirstBytes();

    try {
      AvroParser.AvroInfo avroInfo = AvroParser.extractAvroInfo(bits);
      return new AvroParser.AvroParseSetup(requiredSetup, avroInfo.header, avroInfo.firstBlockSize);
    } catch (Throwable e) {
      throw new H2OIllegalArgumentException("Wrong data", "Cannot find Avro header in input file: " + firstInput, e);
    }
  }
}
