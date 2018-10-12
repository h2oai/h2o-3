package water.parser;

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
  public void testParseEscapedDoubleQuotes(){
    ParseSetup parseSetup = new ParseSetup();
    parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
    parseSetup._check_header = ParseSetup.NO_HEADER;
    parseSetup._separator = ',';
    parseSetup._column_types = new byte[]{Vec.T_STR};
    parseSetup._column_names = new String[]{"Name"};
    parseSetup._number_columns = 1;
    parseSetup._single_quotes = false;
    CsvParser csvParser = new CsvParser(parseSetup, null);

    final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("\"\"\"abcd\"\"\""), 0);
    final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
    final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

    assertEquals(1, outWriter.lineNum());
    assertEquals("\"abcd\"", outWriter._data[1][0]);
    assertFalse(outWriter.hasErrors());
  }

  @Test
  public void testParseEscapedSingleQuotes(){
    ParseSetup parseSetup = new ParseSetup();
    parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
    parseSetup._check_header = ParseSetup.NO_HEADER;
    parseSetup._separator = ',';
    parseSetup._column_types = new byte[]{Vec.T_STR};
    parseSetup._column_names = new String[]{"Name"};
    parseSetup._number_columns = 1;
    parseSetup._single_quotes = true;
    CsvParser csvParser = new CsvParser(parseSetup, null);

    final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("'''abcd'''"), 0);
    final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
    final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

    assertEquals(1, outWriter.lineNum());
    assertEquals("'abcd'", outWriter._data[1][0]);
    assertFalse(outWriter.hasErrors());
  }

  @Test
  public void testParseDoubleEscapedSingleQuotes(){
    ParseSetup parseSetup = new ParseSetup();
    parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
    parseSetup._check_header = ParseSetup.NO_HEADER;
    parseSetup._separator = ',';
    parseSetup._column_types = new byte[]{Vec.T_STR};
    parseSetup._column_names = new String[]{"Name"};
    parseSetup._number_columns = 1;
    parseSetup._single_quotes = true;
    CsvParser csvParser = new CsvParser(parseSetup, null);

    final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("'''''abcd'''''"), 0);
    final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
    final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

    assertEquals(1, outWriter.lineNum());
    assertEquals("''abcd''", outWriter._data[1][0]);
    assertFalse(outWriter.hasErrors());
  }

  @Test
  public void testParseEscapedMixedQuotes(){
    ParseSetup parseSetup = new ParseSetup();
    parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
    parseSetup._check_header = ParseSetup.NO_HEADER;
    parseSetup._separator = ',';
    parseSetup._column_types = new byte[]{Vec.T_STR};
    parseSetup._column_names = new String[]{"Name"};
    parseSetup._number_columns = 1;
    parseSetup._single_quotes = false;
    CsvParser csvParser = new CsvParser(parseSetup, null);

    final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("\"'abcd'\""), 0);
    final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
    final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

    assertEquals(1, outWriter.lineNum());
    assertEquals("'abcd'", outWriter._data[1][0]);
    assertFalse(outWriter.hasErrors());
  }

  @Test
  public void testParseDoubleEscapedDoubleQuotes(){
    ParseSetup parseSetup = new ParseSetup();
    parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
    parseSetup._check_header = ParseSetup.NO_HEADER;
    parseSetup._separator = ',';
    parseSetup._column_types = new byte[]{Vec.T_STR};
    parseSetup._column_names = new String[]{"Name"};
    parseSetup._number_columns = 1;
    parseSetup._single_quotes = false;
    CsvParser csvParser = new CsvParser(parseSetup, null);

    final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("\"\"\"\"\"abcd\"\"\"\"\""), 0);
    final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
    final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

    assertEquals(1, outWriter.lineNum());
    assertEquals("\"\"abcd\"\"", outWriter._data[1][0]);
    assertFalse(outWriter.hasErrors());
  }

  @Test
  public void testDelimiterInsideQuotes(){
    ParseSetup parseSetup = new ParseSetup();
    parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
    parseSetup._check_header = ParseSetup.NO_HEADER;
    parseSetup._separator = ',';
    parseSetup._column_types = new byte[]{Vec.T_STR};
    parseSetup._column_names = new String[]{"Name"};
    parseSetup._number_columns = 1;
    parseSetup._single_quotes = false;
    CsvParser csvParser = new CsvParser(parseSetup, null);

    final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("\",\""), 0);
    final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
    final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

    assertEquals(1, outWriter.lineNum());
    assertEquals(",", outWriter._data[1][0]);
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
            "\"NO\",\"f1987\",\"f10\",\"f18\",\"f7\",\"PS\",\"SAN\",\"SFO\",448"), 0); // first two lines of airlines training dataset
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
  public void tesParseMultipleQuotes_withDelimiterInside(){
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
    assertEquals("Petroff, Mr. Pastcho (\"Pentcho\")", outWriter._data[1][3]);
  }

  @Test
  public void tesParseMultipleQuotes_withDelimiterInside_multiline(){
    ParseSetup parseSetup = new ParseSetup();
    parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
    parseSetup._check_header = ParseSetup.NO_HEADER;
    parseSetup._separator = ',';
    parseSetup._column_types = new byte[]{Vec.T_STR};
    parseSetup._column_names = new String[]{"PassengerId","Survived","Pclass","Name","Sex","Age","SibSp","Parch","Ticket","Fare","Cabin","Embarked"};
    parseSetup._number_columns = 12;
    CsvParser csvParser = new CsvParser(parseSetup, null);

    final String parsedString = "1,0,3,\"Braund, Mr. Owen Harris\",male,22,1,0,A/5 21171,7.25,,S\r\n"
            + "2,1,1,\"Cumings, Mrs. John Bradley (Florence Briggs Thayer)\",female,38,1,0,PC 17599,71.2833,C85,C";
    final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf(parsedString), 0); // first two lines of airlines training dataset
    final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
    final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

    assertEquals(2, outWriter.lineNum());
    assertEquals(0, outWriter._invalidLines);
    assertFalse(outWriter.hasErrors());

    final StringTokenizer stringTokenizer = new StringTokenizer(parsedString, ",");

    for (int lineIndex = 1; lineIndex < 3; lineIndex++) {
      for (int colIndex = 0; colIndex < parseSetup._number_columns; colIndex++) {
        assertNotNull(outWriter._data[lineIndex][colIndex]);
        assertFalse(outWriter._data[lineIndex][colIndex].isEmpty());
      }
    }

    //Make sure internal quotes are parsed well
    assertEquals(12, outWriter._data[1].length);
    assertEquals("A/5 21171", outWriter._data[1][8]);
    assertEquals("NA", outWriter._data[1][10]);
    assertEquals("Braund, Mr. Owen Harris", outWriter._data[1][3]);

    //Make sure internal quotes are parsed well
    assertEquals(12, outWriter._data[1].length);
    assertEquals("PC 17599", outWriter._data[2][8]);
    assertEquals("C85", outWriter._data[2][10]);
    assertEquals("Cumings, Mrs. John Bradley (Florence Briggs Thayer)", outWriter._data[2][3]);
  }

}