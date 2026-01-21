package water.parser;

import water.Job;
import water.Key;
import water.fvec.ByteVec;
import water.fvec.Vec;

/**
 * Generic Parser provider.
 */
public abstract class ParserProvider {
  /** Technical information for this parser */
  public abstract ParserInfo info();

  /** Create a new parser
   */
  public abstract Parser createParser(ParseSetup setup, Key<Job> jobKey);

  /**
   *
   * @param v optional ByteVec (can be null if extracting eg. from a compressed file)
   * @param bits first bytes of the file
   * @param userSetup user specified setup
   * @return derived ParseSetup
   */
  public final ParseSetup guessSetup(ByteVec v, byte[] bits, ParseSetup userSetup) {
    return guessSetup_impl(v, bits, userSetup);
  }

  /**
   * Actual implementation of the guessSetup method. Should almost never be overridden (the only
   * exception is the GuessParserProvider).
   * @param v
   * @param bits
   * @param userSetup
   * @return
   */
  protected ParseSetup guessSetup_impl(ByteVec v, byte[] bits, ParseSetup userSetup) {
    ParseSetup ps = guessInitSetup(v, bits, userSetup);
    return guessFinalSetup(v, bits, ps).settzAdjustToLocal(userSetup._tz_adjust_to_local);
  }

  /**
   * Constructs initial ParseSetup from a given user setup
   *
   * Any exception thrown by this method will signal that this ParserProvider doesn't support
   * the input data.
   *
   * Parsers of data formats that provide metadata (eg. a binary file formats like Parquet) should use the
   * file metadata to identify the parse type and possibly other properties of the ParseSetup
   * that can be determined just from the metadata itself. The goal should be perform the least amount of operations
   * to correctly determine the ParseType (any exception means - format is not supported!).
   *
   * Note: Some file formats like CSV don't provide any metadata. In that case this method can return the final
   * ParseSetup.
   *
   * @param v optional ByteVec
   * @param bits first bytes of the file
   * @param userSetup user specified setup
   * @return null if this Provider cannot provide a parser for this file, otherwise an instance of ParseSetup
   * with correct setting of ParseSetup._parse_type
   */
  public ParseSetup guessInitSetup(ByteVec v, byte[] bits, ParseSetup userSetup) {
    return guessSetup(v, bits, userSetup._separator, userSetup._number_columns, userSetup._single_quotes,
            userSetup._check_header, userSetup._column_names, userSetup._column_types, userSetup._domains, userSetup._na_strings);
  }

  /**
   * Finalizes ParseSetup created by {@see guessInitSetup} using data read from a given ByteVec/bits.
   *
   * @param v optional ByteVec
   * @param bits first bytes of the file
   * @param ps parse setup as created by {@see guessInitSetup}
   * @return fully initialized ParseSetup
   */
  public ParseSetup guessFinalSetup(ByteVec v, byte[] bits, ParseSetup ps) {
    return ps; // by default assume the setup is already finalized
  }

  /** Returns parser setup of throws exception if input is not recognized */
  public ParseSetup guessSetup(ByteVec v, byte[] bits, byte sep, int ncols, boolean singleQuotes, int checkHeader, String[] columnNames, byte[] columnTypes, String[][] domains, String[][] naStrings) {
    throw new UnsupportedOperationException("Not implemented. This method is kept only for backwards compatibility. " +
            "Override methods guessInitSetup & guessFinalSetup if you are implementing a new parser.");
  }

  /** Create a parser specific setup.
   *
   * Useful if parser need a single
   * @param inputs  input keys
   * @param requiredSetup  user given parser setup
   * @return  parser specific setup
   */
  public abstract ParseSetup createParserSetup(Key[] inputs, ParseSetup requiredSetup);

  /**
   * Executed exactly once per-file-per-node during parse.
   * Do any file-related non-distributed setup here. E.g. ORC reader creates node-shared instance of a (non-serializable) Reader.
   * @param v
   * @param setup
   */

  public ParseSetup setupLocal(Vec v, ParseSetup setup){ return setup;}
}
