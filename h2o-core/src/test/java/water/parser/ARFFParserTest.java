package water.parser;

import org.junit.Test;
import water.fvec.Vec;

import static org.junit.Assert.*;

/**
 * Unit test for methods of ARFFParser, for integration test {@see ParserTestARFF}
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