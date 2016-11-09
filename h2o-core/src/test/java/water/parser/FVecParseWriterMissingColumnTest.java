package water.parser;

import org.junit.Before;
import org.junit.Test;
import water.fvec.AppendableVec;

import static org.junit.Assert.*;

/**
 * Tests behavior of FVecParseWriter when parsed line has more values than we have columns
 */
public class FVecParseWriterMissingColumnTest {

  private FVecParseWriter _w;

  @Before
  public void setup() {
    _w = new FVecParseWriter(null, 42, null, null, -1, new AppendableVec[0]);
    _w._col = 17;
    _w._nCols = _w._col + 1;
  }

  @Test
  public void testBasic() {
    _w.addNumCol(18, 133.12);
    _w.addNumCol(19, 2, 3);
    _w.addStrCol(20, new BufferedString("test"));
    _w.addInvalidCol(21);

    _w.newLine();

    checkError(22, "133.12, 2000.0, test, null");
  }

  @Test
  public void testOufOfOrderErrors() {
    _w.addStrCol(20, new BufferedString("test"));
    _w.addNumCol(19, Double.NaN);
    _w.addNumCol(18, 133.12);
    _w.addInvalidCol(21);
    _w.addNumCol(22, 2, 3);

    _w.newLine();

    checkError(23, null);
  }

  @Test
  public void testErrorValuesCapped() {
    for (byte i = 0; i < 20; i++)
      _w.addStrCol(18 + i, new BufferedString(new byte[]{(byte) (i + '0')}, 0, 1));

    _w.newLine();

    checkError(38, "0, 1, 2, 3, 4, 5, 6, 7, 8, 9");
  }

  @Test
  public void testErrorValuesTruncated() {
    for (byte i = 0; i < 20; i++)
      _w.addNumCol(18 + i, 100 + i);

    _w.newLine();

    checkError(38, "100.0, 101.0, 102.0, 103.0, 104.0, 105.0, 106.0, 1...(truncated)");
  }

  private void checkError(int cols, String vals) {
    assertNotNull(_w._errs);
    assertEquals(1, _w._errs.length);
    String expected = "Invalid line, found more columns than expected (found: " + cols + ", expected: 18)" +
            (vals != null ? "; values = {" + vals + "}" : "");
    assertEquals(expected, _w._errs[0]._err);
  }

}