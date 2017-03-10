package water.parser;

import org.junit.Test;
import water.fvec.Vec;

import static org.junit.Assert.*;

/**
 * Unit test for methods of ARFFParser, for integration tests {@see ParserTestARFF}
 */
public class ARFFParserTest {

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
