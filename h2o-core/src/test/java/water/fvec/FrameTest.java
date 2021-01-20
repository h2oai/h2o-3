package water.fvec;

import org.apache.commons.io.IOUtils;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import water.*;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static water.TestUtil.*;

/**
 * Tests for Frame.java
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class FrameTest {
  
  @Rule
  public transient ExpectedException ee = ExpectedException.none();
  
  @Test
  public void testNonEmptyChunks() {
    try {
      Scope.enter();
      final Frame train1 = Scope.track(new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(1, 2, 3, 4, 0))
              .withDataForCol(1, ar("A", "B", "C", "A", "B"))
              .withChunkLayout(1, 0, 0, 2, 1, 0, 1)
              .build());
      assertEquals(4, train1.anyVec().nonEmptyChunks());
      final Frame train2 = Scope.track(new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("ColA", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(1, 2, 3, 4, 0))
              .withDataForCol(1, ar("A", "B", "C", "A", "B"))
              .withChunkLayout(1, 2, 1, 1)
              .build());
      assertEquals(4, train2.anyVec().nonEmptyChunks());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testRemoveColumn() {
    Scope.enter();
    Set<Vec> removedVecs = new HashSet<>();

    try {
      Frame testData = parse_test_file(Key.make("test_deep_select_1"), "smalldata/sparse/created_frame_binomial.svm.zip");
      Scope.track(testData);

      // dataset to split
      int initialSize = testData.numCols();
      removedVecs.add(testData.remove(-1));
      assertEquals(initialSize, testData.numCols());
      removedVecs.add(testData.remove(0));
      assertEquals(initialSize - 1, testData.numCols());
      assertEquals("C2", testData._names[0]);
      removedVecs.add(testData.remove(initialSize - 2));
      assertEquals(initialSize - 2, testData.numCols());
      assertEquals("C" + (initialSize - 1), testData._names[initialSize - 3]);
      removedVecs.add(testData.remove(42));
      assertEquals(initialSize - 3, testData.numCols());
      assertEquals("C43", testData._names[41]);
      assertEquals("C45", testData._names[42]);
    } finally {
      Scope.exit();
      for (Vec v : removedVecs) if (v != null) v.remove();
    }
  }

  // _names=C1,... - C10001
  @Ignore
  @Test public void testDeepSelectSparse() {
    Scope.enter();
    // dataset to split
    Frame testData = parse_test_file(Key.make("test_deep_select_1"), "smalldata/sparse/created_frame_binomial.svm.zip");
    // premade splits from R
    Frame subset1 = parse_test_file(Key.make("test_deep_select_2"), "smalldata/sparse/data_split_1.svm.zip");
    // subset2 commented out to save time
//    Frame subset2 = parse_test_file(Key.make("test_deep_select_3"),"smalldata/sparse/data_split_2.svm");
    // predicates (0: runif 1:runif < .5 2: runif >= .5
    Frame rnd = parse_test_file(Key.make("test_deep_select_4"), "smalldata/sparse/rnd_r.csv");
    Frame x = null;
    Frame y = null;
    try {
      x = testData.deepSlice(new Frame(rnd.vec(1)), null);
      TestUtil.assertBitIdentical(subset1, x);
    } finally {
      Scope.exit();
      testData.delete();
      rnd.delete();
      subset1.delete();
      if (x != null) x.delete();
      if (y != null) y.delete();
    }
  }

  /**
   * This test is testing deepSlice functionality and shows that we can use zero-based indexes for slicing
   * // TODO if confirmed go and correct comments for Frame.deepSlice() method
   */
  @Test
  public void testRowDeepSlice() {
    Scope.enter();
    try {
      long[] numericalCol = ar(1, 2, 3, 4);
      Frame input = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_STR, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "d"))
              .withDataForCol(1, numericalCol)
              .withChunkLayout(numericalCol.length)
              .build();

      // Single number row slice
      Frame sliced = input.deepSlice(new long[]{1}, null);
      assertEquals(1, sliced.numRows());
      assertEquals("b", sliced.vec(0).stringAt(0));
      assertEquals(2, sliced.vec(1).at(0), 1e-5);

      //checking that 0-based indexing is allowed as well
      // We are slicing here particular indexes of rows : 0 and 3
      Frame slicedRange = input.deepSlice(new long[]{0, 3}, null);
      assertEquals(2, slicedRange.numRows());
      assertStringVecEquals(svec("a", "d"), slicedRange.vec(0));
      assertVecEquals(vec(1,4), slicedRange.vec(1), 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testRowDeepSliceWithPredicateFrame() {
    Scope.enter();
    try {
      long[] numericalCol = ar(1, 2, 3, 4);
      Frame input = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_STR, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c", "d"))
              .withDataForCol(1, numericalCol)
              .withChunkLayout(numericalCol.length)
              .build();

      // Single number row slice
      Frame sliced = input.deepSlice(new Frame(vec(0, 1, 0, 0)), null);
      assertEquals(1, sliced.numRows());
      assertEquals("b", sliced.vec(0).stringAt(0));
      assertEquals(2, sliced.vec(1).at(0), 1e-5);

      //checking that 0-based indexing is allowed as well
      Frame slicedRange = input.deepSlice(new Frame(vec(1, 0, 0, 1)), null);
      assertEquals(2, slicedRange.numRows());
      assertStringVecEquals(svec("a", "d"), slicedRange.vec(0));
      assertVecEquals(vec(1,4), slicedRange.vec(1), 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test // deep select filters out all defined values of the chunk and the only left ones are NAs, eg.: c(1, NA, NA) -> c(NA, NA)
  public void testDeepSelectNAs() {
    Scope.enter();
    try {
      String[] data = new String[2 /*defined*/ + 17 /*undefined*/];
      data[0] = "A";
      data[data.length - 1] = "Z";
      double[] pred = new double[data.length];
      Arrays.fill(pred, 1.0);
      pred[0] = 0;
      pred[data.length - 1] = 0;
      Frame input = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "predicate")
              .withVecTypes(Vec.T_STR, Vec.T_NUM)
              .withDataForCol(0, data)
              .withDataForCol(1, pred)
              .withChunkLayout(data.length) // single chunk
              .build();
      Scope.track(input);
      Frame result = new Frame.DeepSelect().doAll(Vec.T_STR, input).outputFrame();
      Scope.track(result);
      assertEquals(data.length - 2, result.numRows());
      for (int i = 0; i < data.length - 2; i++)
        assertTrue("Value in row " + i + " is NA", result.vec(0).isNA(i));
    } finally {
      Scope.exit();
    }
  }
  
  private static class DoubleColTask extends MRTask {
    @Override
    public void map(Chunk c, NewChunk nc) {
      for (int i = 0; i < c._len; i++)
        nc.addNum(c.atd(i));
    }
  }

  @Test
  public void testFinalizePartialFrameRemovesTrailingChunks() {
    final String fName = "part_frame";
    final long[] layout = new long[]{0, 1, 0, 3, 2, 0, 0, 0};

    try {
      Scope.enter();
      Key<Frame> fKey = Key.make(fName);
      Frame f = new Frame(fKey);
      f.preparePartialFrame(new String[]{"C0"});
      Scope.track(f);
      f.update();

      for (int i = 0; i < layout.length; i++) {
        FrameTestUtil.createNC(fName, i, (int) layout[i], new byte[]{Vec.T_NUM});
      }
      f = DKV.get(fName).get();

      f.finalizePartialFrame(layout, new String[][] {null}, new byte[]{Vec.T_NUM});

      final long[] expectedESPC = new long[]{0, 0, 1, 1, 4, 6};
      assertArrayEquals(expectedESPC, f.anyVec().espc());

      Frame f2 = Scope.track(new DoubleColTask().doAll(Vec.T_NUM, f).outputFrame());

      // the ESPC is the same
      assertArrayEquals(expectedESPC, f2.anyVec().espc());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void deepCopyFrameTest() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT)
              .withDataForCol(0, ar("a", "b"))
              .withDataForCol(1, ar("c", "d"))
              .build();

      Frame newFrame = fr.deepCopy(Key.make().toString());

      fr.delete();
      assertStringVecEquals(newFrame.vec("ColB"), cvec("c", "d"));

    } finally {
      Scope.exit();
    }
  }
  
  
  @Test
  public void testToCategoricalColByIdx() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_STR)
              .withDataForCol(0, new String[]{"A", "B", "C"})
              .build();

      assertFalse(fr.vec(0).isCategorical());
      fr.toCategoricalCol(0);
      assertTrue(fr.vec(0).isCategorical());

      fr.delete();
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testToCategoricalColByName() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_STR)
              .withDataForCol(0, new String[]{"A", "B", "C"})
              .build();

      assertFalse(fr.vec("ColA").isCategorical());
      fr.toCategoricalCol("ColA");
      assertTrue(fr.vec("ColA").isCategorical());

      fr.delete();
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMissingVecError() {
    Assume.assumeTrue(Frame.class.desiredAssertionStatus());

    Vec missingVec = Vec.makeCon(Math.PI, 4);
    missingVec.remove();
    assertNull(DKV.get(missingVec._key));
    
    String msg1 = null;
    try {
      new Frame(null, new String[]{"testMissingVec"}, new Vec[]{missingVec});
    } catch (AssertionError ae) {
      msg1 = ae.getMessage();
    }
    assertEquals(" null vec: " + missingVec._key + "; name: testMissingVec", msg1);

    String msg2 = null;
    try {
      new Frame(null, null, new Vec[]{missingVec});
    } catch (AssertionError ae) {
      msg2 = ae.getMessage();
    }
    assertEquals(" null vec: " + missingVec._key + "; index: 0", msg2);
  } 
  
  @Test
  public void testPubDev6673() {
    checkToCSV(false, false);
    checkToCSV(true, false);
    checkToCSV(false, true);
    checkToCSV(true, true);
  }

  private static void checkToCSV(final boolean headers, final boolean hex_string) {
    final InputStream invoked = new ByteArrayInputStream(new byte[0]);

    Frame f = new Frame() {
      @Override
      public InputStream toCSV(CSVStreamParams parms) {
        assertEquals(headers, parms._headers);
        assertEquals(hex_string, parms._hexString);
        return invoked;
      }
    };

    Frame.CSVStreamParams params = new Frame.CSVStreamParams()
        .setHeaders(headers)
        .setHexString(hex_string);
    InputStream wasInvoked = f.toCSV(params);

    assertSame(invoked, wasInvoked); // just make sure the asserts were actually called
  }

  @Test
  public void testSubframe() {
    try {
      Scope.enter();
      String[] cols = new String[]{"col_1", "col_2"};
      Frame f = TestFrameCatalog.oneChunkFewRows();
      Frame sf = f.subframe(cols);
      Frame expected = new Frame(cols, f.vecs(cols));
      assertFrameEquals(expected, sf, 0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testSubframe_invalidCols() {
    try {
      Scope.enter();
      String[] cols = new String[]{"col_5", "col_6", "col_7", "col_8", "col_9", "Omitted_1", "Omitted_2"};
      Frame f = TestFrameCatalog.oneChunkFewRows();
      ee.expectMessage("Frame `" + f._key + "` doesn't contain columns: " +
              "'col_5', 'col_6', 'col_7', 'col_8', 'col_9' (and other 2).");
      f.subframe(cols);
    } finally {
      Scope.exit();
    }
  }
  

  @Test
  public void moveFirstTest() {
    Scope.enter();
    try {
      Frame fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "fold")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c"))
              .withDataForCol(1, ar("d", "e", "f"))
              .withDataForCol(2, ar(3,1,2))
              .build();

      fr.moveFirst(new int[]{1, 2});
      printOutFrameAsTable(fr, false, fr.numRows());
      assertCatVecEquals(cvec("d", "e", "f"), fr.vec(0));
      assertVecEquals(vec(3,1,2), fr.vec(1), 1e-5);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testToCSV_noHeader() throws IOException {
    Scope.enter();
    try {
      Frame fr = TestFrameCatalog.oneChunkFewRows();
      Frame.CSVStreamParams parms_no_header = new Frame.CSVStreamParams()
              .noHeader();
      assertFalse(parms_no_header._headers);
      try (Frame.CSVStream stream = (Frame.CSVStream) fr.toCSV(parms_no_header)) {
        String firstLine = IOUtils.lineIterator(stream, Charset.defaultCharset()).nextLine();
        assertEquals("1.2,-1,\"a\",\"y\"", firstLine);
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testToCSV_noQuotesHeader() throws IOException {
    Scope.enter();
    try {
      Frame fr = TestFrameCatalog.oneChunkFewRows();
      Frame.CSVStreamParams parms_no_quotes = new Frame.CSVStreamParams()
              .setQuoteColumnNames(false);
      assertFalse(parms_no_quotes._quoteColumnNames);
      try (Frame.CSVStream stream = (Frame.CSVStream) fr.toCSV(parms_no_quotes)) {
        String firstLine = IOUtils.lineIterator(stream, Charset.defaultCharset()).nextLine();
        assertEquals("col_0,col_1,col_2,col_3", firstLine);
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testWriteAll() { // shows that writeAll/readAll creates new keys for the Vecs of the imported frame
    Scope.enter();
    try {
      Frame fr = TestFrameCatalog.oneChunkFewRows();
      Key<Vec>[] origVecKeys = fr.keys().clone();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (AutoBuffer ab = new AutoBuffer(baos, true)) {
        fr.writeAll(ab);
      }
      byte[] frBytes = baos.toByteArray();
      assertNotNull(frBytes);
      fr.delete();
      for (Key<Vec> k : origVecKeys) {
        assertNull(DKV.get(k));
      }
      try (AutoBuffer ab = new AutoBuffer(new ByteArrayInputStream(frBytes))) {
        Frame reloaded = (Frame) Frame.readAll(ab);
        Frame frCopy = TestFrameCatalog.oneChunkFewRows();
        assertFrameEquals(frCopy, reloaded, 0);
        for (int i = 0; i < origVecKeys.length; i++) { // all Vecs were re-keyed
          assertNotEquals(origVecKeys[i], frCopy.vec(i)._key);
        }
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testToCSVEscapedQuotes() throws Exception {
    try {
      Scope.enter();
      final Frame frame = new TestFrameBuilder()
              .withColNames("Name", "Description", "Location")
              .withVecTypes(Vec.T_STR, Vec.T_STR, Vec.T_CAT) // One column is categorical intentionally
              .withDataForCol(0, ar("Panam Palmer", "Judy Alvarez"))
              .withDataForCol(1, ar("\"The Aldecaldos\" is her tribe", "Queen of \"braindances\""))
              .withDataForCol(2, ar("Outside of Night City (\"Badlands\")", "Inside the Night City (\"Lizzie's bar\")")) // Notice the single quote there to test it's not escaped
              .build();

      // The default CSVStreamParams are used intentionally, as all strings and enums should be quoted
      // by default
      try (final InputStream csvStream = frame.toCSV(new Frame.CSVStreamParams())) {
        final String csv = IOUtils.toString(csvStream);
        assertNotNull(csv);
        
        final String expectedOutput= "\"Name\",\"Description\",\"Location\"\n" +
                "\"Panam Palmer\",\"\"\"The Aldecaldos\"\" is her tribe\",\"Outside of Night City (\"\"Badlands\"\")\"\n" +
                "\"Judy Alvarez\",\"Queen of \"\"braindances\"\"\",\"Inside the Night City (\"\"Lizzie's bar\"\")\"\n";
        assertEquals(expectedOutput, csv);
      }
    } finally {
      Scope.exit();
    }
  }

}
