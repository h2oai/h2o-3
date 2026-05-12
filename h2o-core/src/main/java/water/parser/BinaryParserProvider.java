package water.parser;

import water.fvec.ByteVec;

/**
 * Base class for Binary format parsers that implements 2-phase ParseSetup.
 */
public abstract class BinaryParserProvider extends ParserProvider {

  /**
   * {@inheritDoc}
   */
  @Override
  public abstract ParseSetup guessInitSetup(ByteVec v, byte[] bits, ParseSetup userSetup);

  /**
   * {@inheritDoc}
   */
  @Override
  public abstract ParseSetup guessFinalSetup(ByteVec v, byte[] bits, ParseSetup ps);

  @Override
  @Deprecated
  public final ParseSetup guessSetup(ByteVec v, byte[] bits, byte sep, int ncols, boolean singleQuotes, int checkHeader, String[] columnNames, byte[] columnTypes, String[][] domains, String[][] naStrings) {
    ParseSetup ps = new ParseSetup(null, sep, singleQuotes, checkHeader,
            ncols, columnNames, columnTypes, domains, naStrings, null, false);
    return guessSetup(v, bits, ps);
  }

}
