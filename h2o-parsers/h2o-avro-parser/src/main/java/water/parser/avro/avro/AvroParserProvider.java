package water.parser.avro.avro;

import water.Iced;
import water.Job;
import water.Key;
import water.parser.ParseSetup;
import water.parser.Parser;
import water.parser.ParserInfo;
import water.parser.ParserProvider;

/**
 * Avro parser provider.
 */
public class AvroParserProvider extends Iced<AvroParserProvider> implements ParserProvider<AvroParserProvider> {

  /* Setup for this parser */
  private static ParserInfo INFO = new ParserInfo("AVRO", 10, true);

  @Override
  public ParserInfo info() {
    return INFO;
  }

  @Override
  public Parser createParser(ParseSetup setup, Key<Job> jobKey) {
    return new AvroParser(setup, jobKey);
  }

  @Override
  public ParseSetup guessSetup(byte[] bits) {
    return AvroParser.guessSetup(bits);
  }
}
