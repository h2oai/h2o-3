package water.parser;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.Scope;
import water.TestUtil;
import water.fvec.FileVec;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.RandomUtils;
import water.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.util.*;

import static org.junit.Assert.*;

@Ignore("Base class for CSV tests, no actual tests present")
public class CsvParserTest extends TestUtil {

  @Before
  public void setUp() {
    TestUtil.stall_till_cloudsize(1);
  }

  public static final class CsvSpecialCasesHandlingtest extends CsvParserTest {

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
    public void testParseEscapedDoubleQuotes() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.NO_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_STR};
      parseSetup._column_names = new String[]{"Name"};
      parseSetup._number_columns = 1;
      parseSetup._single_quotes = false;
      parseSetup._nonDataLineMarkers = new byte[0];
      CsvParser csvParser = new CsvParser(parseSetup, null);

      final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("\"\"\"abcd\"\"\""), 0);
      final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
      final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

      assertEquals(1, outWriter.lineNum());
      assertEquals("\"abcd\"", outWriter._data[1][0]);
      assertFalse(outWriter.hasErrors());
    }

    @Test
    public void testParseEscapedSingleQuotes() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.NO_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_STR};
      parseSetup._column_names = new String[]{"Name"};
      parseSetup._number_columns = 1;
      parseSetup._single_quotes = true;
      parseSetup._nonDataLineMarkers = new byte[0];
      CsvParser csvParser = new CsvParser(parseSetup, null);

      final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("'''abcd'''"), 0);
      final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
      final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

      assertEquals(1, outWriter.lineNum());
      assertEquals("'abcd'", outWriter._data[1][0]);
      assertFalse(outWriter.hasErrors());
    }

    @Test
    public void testParseEscapeCharDoubleQuotes() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.NO_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_STR};
      parseSetup._column_names = new String[]{"Name"};
      parseSetup._number_columns = 1;
      parseSetup._single_quotes = false;
      parseSetup._nonDataLineMarkers = new byte[0];
      CsvParser csvParser = new CsvParser(parseSetup, null);

      final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("\"\\\"ab\\\\cd\\\"\""), 0);
      final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
      final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

      assertEquals(1, outWriter.lineNum());
      assertEquals("\"ab\\cd\"", outWriter._data[1][0]);
      assertFalse(outWriter.hasErrors());
    }

    @Test
    public void testParseEscapeCharSingleQuotes() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.NO_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_STR};
      parseSetup._column_names = new String[]{"Name"};
      parseSetup._number_columns = 1;
      parseSetup._single_quotes = true;
      parseSetup._nonDataLineMarkers = new byte[0];
      CsvParser csvParser = new CsvParser(parseSetup, null);

      final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("'\\'ab\\\\cd\\''"), 0);
      final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
      final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

      assertEquals(1, outWriter.lineNum());
      assertEquals("'ab\\cd'", outWriter._data[1][0]);
      assertFalse(outWriter.hasErrors());
    }

    @Test
    public void testParseDoubleEscapedSingleQuotes() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.NO_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_STR};
      parseSetup._column_names = new String[]{"Name"};
      parseSetup._number_columns = 1;
      parseSetup._single_quotes = true;
      parseSetup._nonDataLineMarkers = new byte[0];
      CsvParser csvParser = new CsvParser(parseSetup, null);

      final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("'''''abcd'''''"), 0);
      final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
      final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

      assertEquals(1, outWriter.lineNum());
      assertEquals("''abcd''", outWriter._data[1][0]);
      assertFalse(outWriter.hasErrors());
    }

    @Test
    public void testParseEscapedMixedQuotes() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.NO_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_STR};
      parseSetup._column_names = new String[]{"Name"};
      parseSetup._number_columns = 1;
      parseSetup._single_quotes = false;
      parseSetup._nonDataLineMarkers = new byte[0];
      CsvParser csvParser = new CsvParser(parseSetup, null);

      final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("\"'abcd'\""), 0);
      final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
      final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

      assertEquals(1, outWriter.lineNum());
      assertEquals("'abcd'", outWriter._data[1][0]);
      assertFalse(outWriter.hasErrors());
    }

    @Test
    public void testParseDoubleEscapedDoubleQuotes() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.NO_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_STR};
      parseSetup._column_names = new String[]{"Name"};
      parseSetup._number_columns = 1;
      parseSetup._single_quotes = false;
      parseSetup._nonDataLineMarkers = new byte[0];
      CsvParser csvParser = new CsvParser(parseSetup, null);

      final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("\"\"\"\"\"abcd\"\"\"\"\""), 0);
      final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
      final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

      assertEquals(1, outWriter.lineNum());
      assertEquals("\"\"abcd\"\"", outWriter._data[1][0]);
      assertFalse(outWriter.hasErrors());
    }

    @Test
    public void testDelimiterInsideQuotes() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.NO_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_STR};
      parseSetup._column_names = new String[]{"Name"};
      parseSetup._number_columns = 1;
      parseSetup._single_quotes = false;
      parseSetup._nonDataLineMarkers = new byte[0];
      CsvParser csvParser = new CsvParser(parseSetup, null);

      final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("\",\""), 0);
      final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
      final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

      assertEquals(1, outWriter.lineNum());
      assertEquals(",", outWriter._data[1][0]);
      assertFalse(outWriter.hasErrors());
    }

    @Test
    public void testRecognizeEOLWithoutQuote() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.NO_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_STR};
      parseSetup._column_names = new String[]{"IsDepDelayed", "fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "UniqueCarrier", "Origin", "Dest", "Distance"};
      parseSetup._number_columns = 9;
      parseSetup._nonDataLineMarkers = new byte[0];
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
    public void tesParseMultipleQuotes_withDelimiterInside() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.NO_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_STR};
      parseSetup._column_names = new String[]{"PassengerId", "Survived", "Pclass", "Name", "Sex", "Age", "SibSp", "Parch", "Ticket", "Fare", "Cabin", "Embarked"};
      parseSetup._number_columns = 12;
      parseSetup._nonDataLineMarkers = new byte[0];
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
    public void tesParseMultipleQuotes_withDelimiterInside_multiline() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.NO_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_STR};
      parseSetup._column_names = new String[]{"PassengerId", "Survived", "Pclass", "Name", "Sex", "Age", "SibSp", "Parch", "Ticket", "Fare", "Cabin", "Embarked"};
      parseSetup._number_columns = 12;
      parseSetup._nonDataLineMarkers = new byte[0];
      CsvParser csvParser = new CsvParser(parseSetup, null);

      final String parsedString = "1,0,3,\"Braund, Mr. Owen Harris\",male,22,1,0,A/5 21171,7.25,,S\r\n"
              + "2,1,1,\"Cumings, Mrs. John Bradley (Florence Briggs Thayer)\",female,38,1,0,PC 17599,71.2833,C85,C";
      final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf(parsedString), 0); // first two lines of airlines training dataset
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

    @Test
    public void nonLineMarkers_use_default() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.HAS_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_NUM, Vec.T_NUM, Vec.T_NUM};
      parseSetup._column_names = new String[]{"Name"};
      parseSetup._number_columns = 3;
      parseSetup._single_quotes = false;
      parseSetup._nonDataLineMarkers = new byte[]{'#'};
      CsvParser parser = new CsvParser(parseSetup, null);

      final String parsedString = "C1,C2,C3\n"
              + "1,2,3\n"
              + "# 3,4,5\n"
              + "6,7,8\n";
      final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf(parsedString), 0);
      final ParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
      final PreviewParseWriter outWriter = (PreviewParseWriter) parser.parseChunk(0, byteAryData, parseWriter);
      assertEquals(3, outWriter._ncols);
      assertEquals(2, outWriter._nlines); // First line is headers, third line is skipped
    }

    @Test
    public void nonLineMarkers_modified() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.HAS_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_STR, Vec.T_NUM, Vec.T_NUM};
      parseSetup._column_names = new String[]{"Name"};
      parseSetup._number_columns = 3;
      parseSetup._single_quotes = false;
      parseSetup._nonDataLineMarkers = new byte[0]; // Non data line markers are set to null, thus defaults should be used
      CsvParser parser = new CsvParser(parseSetup, null);

      final String parsedString = "C1,C2,C3\n"
              + "1,2,3\n"
              + "#3,4,5\n"
              + "6,7,8\n";
      final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf(parsedString), 0);
      final ParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
      final PreviewParseWriter outWriter = (PreviewParseWriter) parser.parseChunk(0, byteAryData, parseWriter);
      assertEquals(3, outWriter._ncols);
      assertEquals(3, outWriter._nlines); // First line is headers, third line is skipped
    }

    /**
     * PUBDEV-6408 - CSV - ArrayIndexOutOfBounds when quotes are parsed
     */
    @Test
    public void testParseDoubleUnendedDoubleQuotes() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.NO_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_STR, Vec.T_STR};
      parseSetup._column_names = new String[]{"C1"};
      parseSetup._number_columns = 1;
      parseSetup._single_quotes = false;
      parseSetup._nonDataLineMarkers = new byte[0];
      CsvParser csvParser = new CsvParser(parseSetup, null);


      final Parser.StreamData data = new Parser.StreamData(new ByteArrayInputStream("\"abcde,".getBytes()), 6);
      data.setChunkDataStart(1, 2);
      final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
      final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, data, parseWriter);

      assertEquals(1, outWriter.lineNum());
      assertEquals("abcde,", outWriter._data[1][0]);
      assertFalse(outWriter.hasErrors());
    }

    /**
     * Mixed single quote presence - some tokens are enclosed in single quotes, some are not.
     * Single quotes explicitly enabled.
     * The single-quoted token has a delimiter inside.
     */
    @Test
    public void testParseSingleQuotesMixed() {
      ParseSetup parseSetup = new ParseSetup();
      parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
      parseSetup._check_header = ParseSetup.NO_HEADER;
      parseSetup._separator = ',';
      parseSetup._column_types = new byte[]{Vec.T_STR};
      parseSetup._column_names = new String[]{"Name"};
      parseSetup._number_columns = 2;
      parseSetup._single_quotes = true;
      parseSetup._nonDataLineMarkers = new byte[0];
      CsvParser csvParser = new CsvParser(parseSetup, null);

      final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf("C1, C2\n'quo,ted',unquoted"), 0);
      final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
      final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);

      assertEquals(2, outWriter.lineNum());
      assertEquals("quo,ted", outWriter._data[2][0]);
      assertEquals("unquoted", outWriter._data[2][1]);
      assertFalse(outWriter.hasErrors());
    }
  }


  @RunWith(Parameterized.class)
  public static final class CsvParserIntegrationTest extends CsvParserTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
      
      final ArrayList<Object[]> params = new ArrayList<>(10);
      params.add(new Object[]{-1});
      params.add(new Object[]{FileVec.DFLT_CHUNK_SIZE});
      params.add(new Object[]{524288});
      params.add(new Object[]{1024});
      
      // The rest of chunk sizes is generated randomly to test different chunk boundaries
      Random random = new RandomUtils.PCGRNG(System.currentTimeMillis(), 1);
      final int upperBound = 1024 * 30 - 512;
      for (int i = 0; i < 6; i++) {
        // Half a kilobyte is the minimal size, 30 kilobytes maximal
        params.add(new Object[]{random.nextInt(upperBound) + 512});
      }
      
      return params;
    }

    @Parameterized.Parameter
    public int chunkSize;

    private ParseSetupTransformer setupTransformer;

    @Before
    public void setUp() {
      super.setUp();

      setupTransformer = new ParseSetupTransformer() {
        @Override
        public ParseSetup transformSetup(ParseSetup guessedSetup) {
          if(chunkSize != -1) { // Leave the guessed amount as is in case of -1
            guessedSetup._chunk_size = chunkSize;
          }
          return guessedSetup;
        }
      };
    }


    @Test
    public void testAirlinesTrain() {
      try {
        Scope.enter();
        final Frame frame = parseTestFile("./smalldata/testng/airlines_train.csv", setupTransformer);
        Scope.track(frame);
        Log.info(frame.toString());
        assertEquals(24421, frame.numRows()); // 24,423 rows in total. Last row is empty, first one is header
        assertEquals(9, frame.numCols());
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void testAirQuality() {
      try {
        Scope.enter();
        final Frame frame = parseTestFile("./smalldata/testng/airquality_train1.csv", setupTransformer);
        Scope.track(frame);
        Log.info(frame.toString());
        assertEquals(77, frame.numRows()); // 79 rows in total. Last row is empty, first one is header
        assertEquals(6, frame.numCols());
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void testCars() {
      try {
        Scope.enter();
        final Frame frame = parseTestFile("./smalldata/testng/cars_train.csv", setupTransformer);
        Scope.track(frame);
        Log.info(frame.toString());
        assertEquals(331, frame.numRows()); // 333 rows in total. Last row is empty, first one is header
        assertEquals(8, frame.numCols());
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void testHiggs5k() {
      try {
        Scope.enter();
        final Frame frame = parseTestFile("./smalldata/testng/higgs_train_5k.csv", setupTransformer);
        Scope.track(frame);
        Log.info(frame.toString());
        assertEquals(5000, frame.numRows()); //5002 rows in total. Last row is empty, first one is header
        assertEquals(29, frame.numCols());
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void testHousing() {
      try {
        Scope.enter();
        final Frame frame = parseTestFile("./smalldata/testng/housing_train.csv", setupTransformer);
        Scope.track(frame);
        Log.info(frame.toString());
        assertEquals(413, frame.numRows()); // 415 rows in total. Last row is empty, first one is header
        assertEquals(14, frame.numCols());
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void testInsurance() {
      try {
        Scope.enter();
        final Frame frame = parseTestFile("./smalldata/testng/insurance_gamma_dense_train.csv", setupTransformer);
        Scope.track(frame);
        Log.info(frame.toString());
        assertEquals(45, frame.numRows()); // 47 rows in total. Last row is empty, first one is header
        assertEquals(6, frame.numCols());
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void testIris() {
      try {
        Scope.enter();
        final Frame frame = parseTestFile("./smalldata/testng/iris.csv", setupTransformer);
        Scope.track(frame);
        Log.info(frame.toString());
        assertEquals(150, frame.numRows()); // 152 rows in total. Last row is empty, first one is header
        assertEquals(5, frame.numCols());
      } finally {
        Scope.exit();
      }
    }
    
    @Test
    public void testAgaricus() {
      try {
        Scope.enter();
        final Frame frame = parseTestFile("./smalldata/xgboost/demo/data/agaricus.txt.train", setupTransformer);
        Scope.track(frame);
        Log.info(frame.toString());
        assertEquals(6513, frame.numRows()); // 6514 rows in total. Last row is empty, no header.
        assertEquals(127, frame.numCols());
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void testFeatmap() {
      try {
        Scope.enter();
        final Frame frame = parseTestFile("./smalldata/xgboost/demo/data/featmap.txt", setupTransformer);
        Scope.track(frame);
        Log.info(frame.toString());
        assertEquals(126, frame.numRows()); // 127 lines in total, no header, last row empty
        assertEquals(3, frame.numCols());
      } finally {
        Scope.exit();
      }
    } 
    
    @Test
    public void testDiabetes() {
      try {
        Scope.enter();
        final Frame frame = parseTestFile("smalldata/diabetes/diabetes_train.csv", setupTransformer);
        Scope.track(frame);
        Log.info(frame.toString());
        assertEquals(50001, frame.numRows()); // 50,003 rows in total. Last row is empty, first one is header.
        assertEquals(50, frame.numCols());
      } finally {
        Scope.exit();
      }
    }

  }


  @RunWith(Parameterized.class)
  public static final class CSVParserSyntheticTest extends CsvParserTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {

      final ArrayList<Object[]> params = new ArrayList<>(2);
      params.add(new Object[]{-1}); // Guessed chunk size
      params.add(new Object[]{FileVec.DFLT_CHUNK_SIZE});
      
      return params;
    }

    @Parameterized.Parameter
    public int chunkSize;

    private ParseSetupTransformer setupTransformer;

    @Before
    public void setUp() {
      super.setUp();

      setupTransformer = new ParseSetupTransformer() {
        @Override
        public ParseSetup transformSetup(ParseSetup guessedSetup) {
          if (chunkSize != -1) { // Leave the guessed amount as is in case of -1
            guessedSetup._chunk_size = chunkSize;
          }
          return guessedSetup;
        }
      };
    }

    @Test
    public void testMultilineQuotes() {
      try {
        Scope.enter();
        final Frame frame = parseTestFile("smalldata/csv-test/quoted_multiline.csv", setupTransformer);
        Scope.track(frame);
        Log.info(frame.toString());
        assertEquals(44, frame.numRows());
        assertEquals(24, frame.numCols());
      } finally {
        Scope.exit();
      }
    }
    
    @Test
    public void testQuotedNoHeader() {
      try {
        Scope.enter();
        final Frame frame = parseTestFile("smalldata/csv-test/quoted_no_header.csv", setupTransformer);
        Log.info(frame.toString());
        Scope.track(frame);
        assertEquals(6, frame.numRows());
        assertEquals(6, frame.numCols());
        
        // Add a second special test, as the dataset is very small (328 bytes only)
        final ParseSetupTransformer smallChunkSizeTransformer = new ParseSetupTransformer() {
          @Override
          public ParseSetup transformSetup(ParseSetup guessedSetup) {
            guessedSetup._chunk_size = 128;
            return guessedSetup;
          }
        };
        final Frame smallChunkSizeResultFrame = parseTestFile("smalldata/csv-test/quoted_no_header.csv", smallChunkSizeTransformer);
        Scope.track(smallChunkSizeResultFrame);
        Log.info(smallChunkSizeResultFrame.toString());
        assertEquals(6, smallChunkSizeResultFrame.numRows());
        assertEquals(6, smallChunkSizeResultFrame.numCols());


      } finally {
        Scope.exit();
      }
    }

  }

  
  @Test
  public void testPubdev7149() {

    ParseSetup parseSetup = new ParseSetup();
    parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
    parseSetup._check_header = ParseSetup.NO_HEADER;
    parseSetup._separator = ',';
    parseSetup._column_types = new byte[]{Vec.T_STR};
    parseSetup._column_names = new String[]{"C1"};
    parseSetup._number_columns = 1;
    CsvParser csvParser = new CsvParser(parseSetup, null);

    final String parsedString = "\"text\"\n" +
            "\"$\"";
    final Parser.ByteAryData byteAryData = new Parser.ByteAryData(StringUtils.bytesOf(parsedString), 0);
    final PreviewParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
    final PreviewParseWriter outWriter = (PreviewParseWriter) csvParser.parseChunk(0, byteAryData, parseWriter);


    assertEquals(2, outWriter.lineNum());
    assertEquals(0, outWriter._invalidLines);
    assertFalse(outWriter.hasErrors());

    for (int lineIndex = 1; lineIndex < 2; lineIndex++) {
      for (int colIndex = 0; colIndex < parseSetup._number_columns; colIndex++) {
        assertNotNull(outWriter._data[lineIndex][colIndex]);
        assertFalse(outWriter._data[lineIndex][colIndex].isEmpty());
      }
    }

    //Make sure internal quotes are parsed well
    assertEquals(1, outWriter._data[1].length);
    assertEquals(1, outWriter._data[2].length);
    assertEquals("text", outWriter._data[1][0]);
    assertEquals("$", outWriter._data[2][0]);
  }

}
