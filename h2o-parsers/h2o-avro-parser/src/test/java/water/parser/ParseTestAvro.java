package water.parser;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Test suite for Avro parser.
 */
public class ParseTestAvro extends TestUtil {

  private static double EPSILON = 1e-9;

  @BeforeClass
  static public void setup() { TestUtil.stall_till_cloudsize(5); }

  @Test
  public void testSkippedColumns() {
    try { // specify skipped columns, not allowed!
      Frame f1 = parseTestFile("smalldata/parser/avro/sequence100k.avro", new int[]{0,1});
      fail("Parser should have thrown an exception but did not!");
    } catch(Exception ex) { // this should fail
      System.out.println("Done, Avro parsers should not specify skipped_columns");
    }
  }

  @Test
  public void testParseSimple() {
    // Tests for basic files which are in smalldata
    FrameAssertion[] assertions = new FrameAssertion[] {
        // sequence100k.avro
        new FrameAssertion("smalldata/parser/avro/sequence100k.avro", TestUtil.ari(1, 100000)) {
          @Override public void check(Frame f) {
            Vec values = f.vec(0);
            for (int i = 0; i < f.numRows(); i++) {
              assertEquals(i, values.at8(i));
            }
          }
        },
        // episodes.avro
        new FrameAssertion("smalldata/parser/avro/episodes.avro", TestUtil.ari(3, 8)) {}
    };

    for (int i = 0; i < assertions.length; ++i) {
      assertFrameAssertion(assertions[i]);
    }
  }

  @Test public void testParsePrimitiveTypes() {
    FrameAssertion[] assertions = new FrameAssertion[]{
        new GenFrameAssertion("supportedPrimTypes.avro", TestUtil.ari(8, 100)) {

          @Override protected File prepareFile() throws IOException { return AvroFileGenerator.generatePrimitiveTypes(file, nrows()); }

          @Override
          public void check(Frame f) {
            assertArrayEquals("Column names need to match!", ar("CString", "CBytes", "CInt", "CLong", "CFloat", "CDouble", "CBoolean", "CNull"), f.names());
            assertArrayEquals("Column types need to match!", ar(Vec.T_STR, Vec.T_STR, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_BAD), f.types());

            int nrows = nrows();
            BufferedString bs = new BufferedString();
            for (int row = 0; row < nrows; row++) {
              assertEquals("Value in column CString", String.valueOf(row), f.vec(0).atStr(bs, row).toString());
              assertEquals("Value in column CBytes", String.valueOf(row), f.vec(1).atStr(bs, row).toString());
              assertEquals("Value in column CInt", row, f.vec(2).at8(row));
              assertEquals("Value in column CLong", row, f.vec(3).at8(row));
              assertEquals("Value in column CFloat", row, f.vec(4).at(row), EPSILON);
              assertEquals("Value in column CDouble", row, f.vec(5).at(row), EPSILON);
              assertEquals("Value in column CBoolean", (row & 1) == 1, (((int) f.vec(5).at(row)) & 1) == 1);
              assertTrue("Value in column CNull", f.vec(7).isNA(row));
            }
          }
        }
    };

    for (int i = 0; i < assertions.length; ++i) {
      assertFrameAssertion(assertions[i]);
    }
  }

  @Test public void testParseUnionTypes() {
    FrameAssertion[] assertions = new FrameAssertion[]{
        new GenFrameAssertion("unionTypes.avro", TestUtil.ari(7, 101)) {

          @Override protected File prepareFile() throws IOException { return AvroFileGenerator.generateUnionTypes(file, nrows()); }

          @Override
          public void check(Frame f) {
            assertArrayEquals("Column names need to match!", ar("CUString", "CUBytes", "CUInt", "CULong", "CUFloat", "CUDouble", "CUBoolean"), f.names());
            assertArrayEquals("Column types need to match!", ar(Vec.T_STR, Vec.T_STR, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM), f.types());
            int nrows = nrows();
            BufferedString bs = new BufferedString();
            // NA in the first row
            for (int col = 0; col < ncols(); col++) {
              assertTrue("NA should be in first row and col " + col, f.vec(col).isNA(0));
            }
            for (int row = 1; row < nrows; row++) {
              assertEquals("Value in column CString", String.valueOf(row), f.vec(0).atStr(bs, row).toString());
              assertEquals("Value in column CBytes", String.valueOf(row), f.vec(1).atStr(bs, row).toString());
              assertEquals("Value in column CInt", row, f.vec(2).at8(row));
              assertEquals("Value in column CLong", row, f.vec(3).at8(row));
              assertEquals("Value in column CFloat", row, f.vec(4).at(row), EPSILON);
              assertEquals("Value in column CDouble", row, f.vec(5).at(row), EPSILON);
              assertEquals("Value in column CBoolean", (row & 1) == 1, (((int) f.vec(5).at(row)) & 1) == 1);
            }
          }
        }
    };

    for (int i = 0; i < assertions.length; ++i) {
      assertFrameAssertion(assertions[i]);
    }
  }

  @Test public void testParseEnumTypes() {
    FrameAssertion[] assertions = new FrameAssertion[]{
        new GenFrameAssertion("enumTypes.avro", TestUtil.ari(2, 100)) {
          String[][] categories = AvroFileGenerator.generateSymbols(ar("CAT_A_", "CAT_B_"), ari(7, 13)); // Generated categories
          @Override protected File prepareFile() throws IOException {
            return AvroFileGenerator.generateEnumTypes(file, nrows(), categories);
          }

          @Override
          public void check(Frame f) {
            assertArrayEquals("Column names need to match!", ar("CEnum", "CUEnum"), f.names());
            assertArrayEquals("Column types need to match!", ar(Vec.T_CAT, Vec.T_CAT), f.types());
            assertArrayEquals("Category names need to match in CEnum!", categories[0], f.vec("CEnum").domain());
            assertArrayEquals("Category names need to match in CUEnum!", categories[1], f.vec("CUEnum").domain());

            int numOfCategories1 = categories[0].length;
            int numOfCategories2 = categories[1].length;
            int nrows = nrows();
            for (int row = 0; row < nrows; row++) {
              assertEquals("Value in column CEnum", row % numOfCategories1, (int) f.vec("CEnum").at(row));
              if (row % (numOfCategories2+1) == 0) assertTrue("NA should be in row " + row + " and col CUEnum", f.vec("CUEnum").isNA(row));
              else assertEquals("Value in column CUEnum", row % numOfCategories2, (int) f.vec("CUEnum").at(row));
            }
          }
        }
    };

    for (int i = 0; i < assertions.length; ++i) {
      assertFrameAssertion(assertions[i]);
    }
  }

}

