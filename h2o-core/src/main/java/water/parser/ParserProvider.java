package water.parser;

import water.Freezable;
import water.Job;
import water.Key;

/**
 * FIXME: remove freezable and self annotation
 */
public interface ParserProvider {
  /** Technical information for this parser */
  ParserInfo info();

  /** Create a new parser
   */
  Parser createParser(ParseSetup setup, Key<Job> jobKey);

  /** Returns parser setup of throws exception if input is not recognized */
  // FIXME: should be more flexible
  ParseSetup guessSetup(byte[] bits, byte sep, int ncols, boolean singleQuotes, int checkHeader, String[] columnNames, byte[] columnTypes, String[][] domains, String[][] naStrings );

  /** Create a parser specific setup.
   *
   * Useful if parser need a single
   * @param inputs  input keys
   * @param requiredSetup  user given parser setup
   * @return  parser specific setup
   */
  ParseSetup createParserSetup(Key[] inputs, ParseSetup requiredSetup);
}
