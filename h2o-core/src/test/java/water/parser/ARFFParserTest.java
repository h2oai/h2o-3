package water.parser;

import org.junit.Before;
import org.junit.Test;
import water.TestBase;
import water.TestUtil;
import water.fvec.NFSFileVec;
import water.fvec.Vec;

import static org.junit.Assert.*;

/**
 * Unit test for methods of ARFFParser, for integration tests {@see ParserTestARFF}
 */
public class ARFFParserTest extends TestUtil {

  @Before
  public void setUp() throws Exception {
    stall_till_cloudsize(1);
  }

  @Test
  public void testProcessArffHeader() throws Exception {
    final String[] headers = headerData();
    final int numCols = headers.length;

    String[] labels = new String[numCols];
    String[][] domains = new String[numCols][];
    byte[] ctypes = new byte[numCols];

    ARFFParser.processArffHeader(numCols, headers, labels, domains, ctypes);

    assertArrayEquals(new String[]{
            "TSH",
            "TSH",
            "on antithyroid medication",
            "on antithyroid medication",
            "query \thypothyroid",
            " query  hypothyroid "
    }, labels);
    assertArrayEquals(new byte[]{Vec.T_NUM, Vec.T_TIME, Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_CAT}, ctypes);
    for (int i = 2; i < headers.length; i++)
      assertArrayEquals(new String[]{"f", "t"}, domains[i]);
  }

  @Test
  public void testProcessArffHeader_invalidTag() {
    runWithException("@attr BLAH NUMERIC", "Expected line to start with @ATTRIBUTE.");
  }

  @Test
  public void testProcessArffHeader_unknownType() {
    runWithException("@attribute BLAH BOOLEAN", "Unexpected line, type not recognized. Attribute specification: BOOLEAN");
  }

  @Test
  public void testProcessArffHeader_invalidCategorical() {
    runWithException("@attribute BLAH {}", "Unexpected line, type not recognized. Attribute specification: {}");
  }

  @Test
  public void testProcessArffHeader_unsupportedType() {
    runWithException("@attribute BLAH RELATIONAL", "Relational ARFF format is not supported.");
  }
  
  @Test
  public void nonLineMarkers_use_default(){
    ParseSetup parseSetup = new ParseSetup();
    parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
    parseSetup._check_header = ParseSetup.NO_HEADER;
    parseSetup._separator = ',';
    parseSetup._column_types = new byte[]{Vec.T_NUM,Vec.T_NUM,Vec.T_NUM,Vec.T_NUM, Vec.T_CAT};
    parseSetup._column_names = new String[]{"Name"};
    parseSetup._number_columns = 5;
    parseSetup._single_quotes = false;
    parseSetup._nonDataLineMarkers = new byte[]{'%', '@'};
    final ARFFParser parser = new ARFFParser(parseSetup, null);

    final NFSFileVec data = makeNfsFileVec("./smalldata/arff-examples/dataWeka/iris.arff");
    final Parser.ByteAryData byteAryData = new Parser.ByteAryData(data.getFirstBytes(), 0);
    final ParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
    final PreviewParseWriter outWriter = (PreviewParseWriter) parser.parseChunk(0, byteAryData, parseWriter);
    assertEquals(5, outWriter._ncols);
    assertEquals(150, outWriter._nlines);
  }

  @Test
  public void nonLineMarkers_modified(){
    ParseSetup parseSetup = new ParseSetup();
    parseSetup._parse_type = DefaultParserProviders.CSV_INFO;
    parseSetup._check_header = ParseSetup.NO_HEADER;
    parseSetup._separator = ',';
    parseSetup._column_types = new byte[]{Vec.T_NUM,Vec.T_NUM,Vec.T_NUM,Vec.T_NUM, Vec.T_CAT};
    parseSetup._column_names = new String[]{"Name"};
    parseSetup._number_columns = 5;
    parseSetup._single_quotes = false;
    parseSetup._nonDataLineMarkers = new byte[]{'@'}; // Instead of using default settings, this should be used
    final ARFFParser parser = new ARFFParser(parseSetup, null);

    final NFSFileVec data = makeNfsFileVec("./smalldata/arff-examples/dataWeka/iris.arff");
    final Parser.ByteAryData byteAryData = new Parser.ByteAryData(data.getFirstBytes(), 0);
    final ParseWriter parseWriter = new PreviewParseWriter(parseSetup._number_columns);
    final PreviewParseWriter outWriter = (PreviewParseWriter) parser.parseChunk(0, byteAryData, parseWriter);
    assertEquals(5, outWriter._ncols);
    assertEquals(153, outWriter._nlines); // Three lines more, as the last three lines start with '%'
  }

  private void runWithException(String attrSpec, String exceptedMessage) {
    try {
      ARFFParser.processArffHeader(1, new String[]{attrSpec}, new String[1], new String[1][], new byte[1]);
      fail("Parser was expected to fail on '" + attrSpec + "'.");
    } catch (Exception e) {
      assertEquals(exceptedMessage, e.getMessage());
    }
  }

  private static String[] headerData() {
    return new String[] {
            "@attribute TSH numeric",
            "@attribute    TSH    DaTe",
            "@attribute\ton antithyroid medication\t{f,t}",
            "@attribute\t\ton antithyroid medication\t\t{f,  t}",
            "@attribute 'query \thypothyroid' {f,t}",
            "@attribute    ' query  hypothyroid '    {f, t  }"
    };
  }

}
