package water.parser;

import java.util.List;

import water.Job;
import water.Key;
import water.exceptions.H2OUnsupportedDataFileException;
import water.fvec.ByteVec;
import water.util.Log;

/**
 * Default parsers provided by H2O.
 *
 * The parser are registered via service providers interface into
 * <code>{@link ParserService}</code>.
 */
public final class DefaultParserProviders {

  /** Default parser handles */
  public static final ParserInfo ARFF_INFO = new ParserInfo("ARFF", 0, true);
  public static final ParserInfo XLS_INFO = new ParserInfo("XLS", 100, false);
  public static final ParserInfo XLSX_INFO = new ParserInfo("XLSX", 102, false);
  public static final ParserInfo SVMLight_INFO = new ParserInfo("SVMLight", 1000, true);
  public static final ParserInfo CSV_INFO = new ParserInfo("CSV", Integer.MAX_VALUE, true);
  public static final ParserInfo GUESS_INFO = new ParserInfo("GUESS", -10000, false);
  /** Priority of non-core parsers should begin here.*/
  public static final int MAX_CORE_PRIO = 10000;

  public final static class ArffParserProvider extends AbstractParserProvide  {

    @Override
    public ParserInfo info() {
      return ARFF_INFO;
    }

    @Override
    public Parser createParser(ParseSetup setup, Key<Job> jobKey) {
      return new ARFFParser(setup, jobKey);
    }

    @Override
    public ParseSetup guessSetup(ByteVec bv, byte[] bits, byte sep, int ncols, boolean singleQuotes,
                                 int checkHeader, String[] columnNames, byte[] columnTypes,
                                 String[][] domains, String[][] naStrings) {
      return ARFFParser.guessSetup(bv, bits, sep, singleQuotes, columnNames, naStrings);
    }
  }

  public final static class XlsParserProvider extends AbstractParserProvide  {

    @Override
    public ParserInfo info() {
      return XLS_INFO;
    }

    @Override
    public Parser createParser(ParseSetup setup, Key<Job> jobKey) {
      return new XlsParser(setup, jobKey);
    }

    @Override
    public ParseSetup guessSetup(ByteVec bv, byte[] bits, byte sep, int ncols, boolean singleQuotes,
                                 int checkHeader, String[] columnNames, byte[] columnTypes,
                                 String[][] domains, String[][] naStrings) {
      return XlsParser.guessSetup(bits);
    }
  }

  public final static class SVMLightParserProvider extends AbstractParserProvide {

    @Override
    public ParserInfo info() {
      return SVMLight_INFO;
    }

    @Override
    public Parser createParser(ParseSetup setup, Key<Job> jobKey) {
      return new SVMLightParser(setup, jobKey);
    }

    @Override
    public ParseSetup guessSetup(ByteVec bv, byte[] bits, byte sep, int ncols, boolean singleQuotes,
                                 int checkHeader, String[] columnNames, byte[] columnTypes,
                                 String[][] domains, String[][] naStrings) {
      return SVMLightParser.guessSetup(bits);
    }
  }

  public final static class CsvParserProvider extends AbstractParserProvide {

    @Override
    public ParserInfo info() {
      return CSV_INFO;
    }

    @Override
    public Parser createParser(ParseSetup setup, Key<Job> jobKey) {
      return new CsvParser(setup, jobKey);
    }

    @Override
    public ParseSetup guessSetup(ByteVec bv, byte[] bits, byte sep, int ncols, boolean singleQuotes,
                                 int checkHeader, String[] columnNames, byte[] columnTypes,
                                 String[][] domains, String[][] naStrings) {
      return CsvParser.guessSetup(bits, sep, ncols, singleQuotes, checkHeader, columnNames, columnTypes, naStrings);
    }
  }

  public final static class GuessParserProvider extends AbstractParserProvide {

    @Override
    public ParserInfo info() {
      return GUESS_INFO;
    }

    @Override
    protected ParseSetup guessSetup_impl(ByteVec bv, byte[] bits, ParseSetup userSetup) {
      List<ParserProvider> pps = ParserService.INSTANCE.getAllProviders(true); // Sort them based on priorities

      ParseSetup parseSetup = null;
      ParserProvider provider = null;

      for (ParserProvider pp : pps) {
        // Do not do recursive call
        if (pp == this || pp.info().equals(GUESS_INFO)) continue;
        // Else try to guess with given provider
        try {
          ParseSetup ps = pp.guessInitSetup(bv, bits, userSetup);
          if (ps != null) { // found a parser for the data type
            provider = pp;
            parseSetup = ps;
            break;
          }
        } catch (H2OUnsupportedDataFileException e) {
          throw e;
        } catch (Throwable ignore) {
          /*ignore failed parse attempt*/
          Log.trace("Guesser failed for parser type", pp.info(), ignore);
        }
      }

      if (provider == null)
        throw new ParseDataset.H2OParseException("Cannot determine file type.");

      // finish parse setup & don't ignore the exceptions
      return provider.guessFinalSetup(bv, bits, parseSetup);
    }

    @Override
    public Parser createParser(ParseSetup setup, Key<Job> jobKey) {
      throw new UnsupportedOperationException("Guess parser provided does not know how to create a new parser! Use a specific parser!");
    }

  }

  static abstract class AbstractParserProvide extends ParserProvider {

    @Override
    public ParseSetup createParserSetup(Key[] inputs, ParseSetup requiredSetup) {
      return requiredSetup;
    }
  }
}
