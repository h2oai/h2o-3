package water.parser;

import org.junit.Assert;
import org.junit.Test;
import water.fvec.Vec;
import water.util.StringUtils;

import java.util.StringTokenizer;

import static org.junit.Assert.*;

public class CsvParserTest {

  @Test
  public void determineTokens_multipleByteCharacters() {
    byte quoteType = '\'';
    byte delimiter = ',';
    // Japanese alphabet is represented as up to 3 bytes per character.
    String[] strings = CsvParser.determineTokens("'C1', 'C2', '契約状態1709'", delimiter, quoteType);
    assertEquals(3, strings.length);
    assertEquals("C1", strings[0]);
    assertEquals("C2", strings[1]);
    assertEquals("契約状態1709", strings[2]);
  }

  @Test
  public void testParseMultipleQuotes(){
    ParseSetup parseSetup = new ParseSetup();
    parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
    parseSetup._check_header = ParseSetup.NO_HEADER;
    parseSetup._separator = ',';
    parseSetup._column_types = new byte[]{Vec.T_STR};
    parseSetup._column_names = new String[]{"Name"};
    parseSetup._number_columns = 1;
    CsvParser csvParser = new CsvParser(parseSetup, null);

    final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("\"\")\"\""), 0);
    final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
    final ParseWriter outWriter = csvParser.parseChunk(0, byteAryData, parseWriter);

    assertEquals(1, outWriter.lineNum());
    assertFalse(outWriter.hasErrors());
  }

  @Test
  public void testRecognizeEOLWithoutQuote(){
    ParseSetup parseSetup = new ParseSetup();
    parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
    parseSetup._check_header = ParseSetup.NO_HEADER;
    parseSetup._separator = ',';
    parseSetup._column_types = new byte[]{Vec.T_STR};
    parseSetup._column_names = new String[]{"IsDepDelayed","fYear","fMonth","fDayofMonth","fDayOfWeek","UniqueCarrier","Origin","Dest","Distance"};
    parseSetup._number_columns = 9;
    CsvParser csvParser = new CsvParser(parseSetup, null);

    final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("\"YES\",\"f1987\",\"f10\",\"f14\",\"f3\",\"PS\",\"SAN\",\"SFO\",447\n" +
            "\"NO\",\"f1987\",\"f10\",\"f18\",\"f7\",\"PS\",\"SAN\",\"SFO\",447"), 0); // first two lines of airlines training dataset
    final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
    final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

    assertEquals(2, outWriter.lineNum());
    assertEquals(0, outWriter._invalidLines);
    assertFalse(outWriter.hasErrors());

    for (int lineIndex = 1; lineIndex < 3; lineIndex++) {
      for (int colIndex = 0; colIndex < parseSetup._number_columns; colIndex++) {
        assertNotNull(outWriter._data[lineIndex][colIndex]);
        assertFalse(outWriter._data[lineIndex][colIndex].isEmpty());
      }
    }

  }

  @Test
  public void testParseTitanicTrain_multiple_qotes(){
    ParseSetup parseSetup = new ParseSetup();
    parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
    parseSetup._check_header = ParseSetup.NO_HEADER;
    parseSetup._separator = ',';
    parseSetup._column_types = new byte[]{Vec.T_STR};
    parseSetup._column_names = new String[]{"PassengerId","Survived","Pclass","Name","Sex","Age","SibSp","Parch","Ticket","Fare","Cabin","Embarked"};
    parseSetup._number_columns = 12;
    CsvParser csvParser = new CsvParser(parseSetup, null);

    final String parsedString = "102,0,3,\"Petroff, Mr. Pastcho (\"\"Pentcho\"\")\",male,,0,0,349215,7.8958,,S";
    final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf(parsedString), 0); // first two lines of airlines training dataset
    final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
    final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

    assertEquals(1, outWriter.lineNum());
    assertEquals(0, outWriter._invalidLines);
    assertFalse(outWriter.hasErrors());

    final StringTokenizer stringTokenizer = new StringTokenizer(parsedString, ",");

    for (int lineIndex = 1; lineIndex < 2; lineIndex++) {
      for (int colIndex = 0; colIndex < parseSetup._number_columns; colIndex++) {
        assertNotNull(outWriter._data[lineIndex][colIndex]);
        assertFalse(outWriter._data[lineIndex][colIndex].isEmpty());
      }
    }

    //Make sure internal quotes are parsed well
    assertEquals(12, outWriter._data[1].length);
    assertEquals("NA", outWriter._data[1][5]);
    assertEquals("NA", outWriter._data[1][10]);
    assertEquals("Petroff, Mr. Pastcho (\"\"Pentcho\"\")", outWriter._data[1][3]);
  }

}