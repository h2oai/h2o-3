package water.parser;

import org.junit.Assert;
import org.junit.Test;

public class CsvParserTest {

  @Test
  public void determineTokens_multipleByteCharacters() {
    byte quoteType = '\'';
    byte delimiter = ',';
    // Japanese alphabet is represented as up to 3 bytes per character.
    String[] strings = CsvParser.determineTokens("'C1', 'C2', '契約状態1709'", delimiter, quoteType);
    Assert.assertEquals(3, strings.length);
    Assert.assertEquals("C1", strings[0]);
    Assert.assertEquals("C2", strings[1]);
    Assert.assertEquals("契約状態1709", strings[2]);
  }
}