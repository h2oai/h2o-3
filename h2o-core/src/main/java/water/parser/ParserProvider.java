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
    ParseSetup ps = guessFormatSetup(v, bits, userSetup);
    return guessDataSetup(v, bits, ps);
  }

  /**
   * Uses file metadata to identify the parse type and other properties of the ParseSetup
   * that can be determined just from the metadata itself. Correct implementation of this method should
   * only take the necessary steps to identify whether the given data can be parsed with a corresponding parser
   * (and nothing else!).
   *
   * The purpose of this method is to break the chain of parser providers if there is a compatible parser find.
   * In that can we don't continue in the chain - this ensures we will see the data parse-related error messages.
   *
   * @param v optional ByteVec
   * @param bits first bytes of the file
   * @param userSetup user specified setup
   * @return null if this Provider cannot provider a parse for this file, otherwise an instance of ParseSetup
   * with correct setting of ParseSetup._parse_type
   */
  public ParseSetup guessFormatSetup(ByteVec v, byte[] bits, ParseSetup userSetup) {
    return userSetup;
  }

  /**
   * Finalizes ParseSetup created by {@see guessFormatSetup} using data read from a given ByteVec/bits.
   * @param v optional ByteVec
   * @param bits first bytes of the file
   * @param ps parse setup as created by {@see guessFormatSetup}
   * @return fully initialized ParseSetup or null if provider doesn't handle this data format
   */
  public ParseSetup guessDataSetup(ByteVec v, byte[] bits, ParseSetup ps) {
    return guessSetup(v, bits, ps._separator, ps._number_columns, ps._single_quotes,
            ps._check_header, ps._column_names, ps._column_types, ps._domains, ps._na_strings);
  }

  /** Returns parser setup of throws exception if input is not recognized */
  // FIXME: should be more flexible
  public ParseSetup guessSetup(ByteVec v, byte[] bits, byte sep, int ncols, boolean singleQuotes, int checkHeader, String[] columnNames, byte[] columnTypes, String[][] domains, String[][] naStrings) {
    throw new UnsupportedOperationException("Not implemented. This method is kept only for backwards compatibility. " +
            "Override methods guessFormatSetup & guessDataSetup if you are implementing a new parser.");
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
