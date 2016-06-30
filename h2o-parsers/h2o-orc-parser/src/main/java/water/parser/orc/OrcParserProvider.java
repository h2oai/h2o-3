package water.parser.orc;

import water.DKV;
import water.Iced;
import water.Job;
import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.ByteVec;
import water.fvec.Frame;
import water.parser.*;

/**
 * Orc parser provider.
 */
public class OrcParserProvider implements ParserProvider {

  /* Setup for this parser */
  static ParserInfo ORC_INFO = new ParserInfo("ORC", DefaultParserProviders.MAX_CORE_PRIO + 20, true, true);

  @Override
  public ParserInfo info() {
    return ORC_INFO;
  }

  @Override
  public Parser createParser(ParseSetup setup, Key<Job> jobKey) {
    return new OrcParser(setup, jobKey);
  }

  @Override
  public ParseSetup guessSetup(byte[] bits, byte sep, int ncols, boolean singleQuotes,
                               int checkHeader, String[] columnNames, byte[] columnTypes,
                               String[][] domains, String[][] naStrings) {
    return OrcParser.guessSetup(bits);
  }

  @Override
  public ParseSetup createParserSetup(Key[] inputs, ParseSetup requiredSetup) {

    assert inputs != null && inputs.length > 0 : "Inputs cannot be empty!";
    Key firstInput = inputs[0];
    Iced ice = DKV.getGet(firstInput);
    if (ice == null)
      throw new H2OIllegalArgumentException("Missing data", "Did not find any data under key " + firstInput);
    ByteVec bv = (ByteVec)(ice instanceof ByteVec ? ice : ((Frame)ice).vecs()[0]);
    byte [] bits = bv.getFirstBytes();

    try {
      OrcParser.OrcInfo OrcInfo = OrcParser.extractOrcInfo(bits, requiredSetup);
      return new OrcParser.OrcParseSetup(requiredSetup, OrcInfo.orcFileReader, OrcInfo.cumStripeSizes,
              OrcInfo.totalFileSize, OrcInfo.stripesInfo, OrcInfo.columnTypesString, OrcInfo.maxStripeSize,
              OrcInfo.toInclude, OrcInfo.allColumnNames);
    } catch (Throwable e) {
      throw new H2OIllegalArgumentException("Wrong data", "Cannot find Orc header in input file: " + firstInput, e);
    }
  }
}
