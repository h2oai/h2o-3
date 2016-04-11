package water.parser;

import water.Freezable;
import water.Job;
import water.Key;

/**
 * Created by michal on 4/15/16.
 */
public interface ParserProvider<S extends ParserProvider<S>> extends Freezable<S> {
  /** Technical information for this parser */
  ParserInfo info();

  /** Create a new parser
   */
  Parser createParser(ParseSetup setup, Key<Job> jobKey);

  /** Returns parser setup of throws exception if input is not recognized */
  // FIXME: should be more flexible
  ParseSetup guessSetup(byte[] bits);
}
