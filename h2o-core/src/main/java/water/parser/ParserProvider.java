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

  /** Returns parser setup of throws exception if input is not recognized */
  // FIXME: should be more flexible
  public abstract ParseSetup guessSetup(ByteVec v, byte[] bits, byte sep, int ncols, boolean singleQuotes, int checkHeader, String[] columnNames, byte[] columnTypes, String[][] domains, String[][] naStrings );

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
