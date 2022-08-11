package water.fvec;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.parser.BufferedString;
import water.util.ArrayUtils;
import water.util.FrameUtils;
import water.util.VecUtils;

import java.util.UUID;

import static org.junit.Assert.*;

public class TestFrameBuilderTest extends TestUtil {
  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }
  private static double DELTA = 0.00001;

  @Test
  public void testEmpty(){
    Frame fr = new TestFrameBuilder().build();

    assertEquals(fr.vecs().length, 0);
    assertEquals(fr.numRows(), 0);
    assertEquals(fr.numCols(), 0);
    assertNull(fr.anyVec()); // because we don't have any vectors
    fr.remove();
  }

  @Test
  public void testName(){
    Frame fr = new TestFrameBuilder()
            .withName("FrameName")
            .build();

    assertEquals(fr._key.toString(), "FrameName");
    assertEquals(fr.vecs().length, 0);
    assertEquals(fr.numRows(), 0);
    assertEquals(fr.numCols(), 0);
    assertNull(fr.anyVec()); // because we don't have any vectors
    fr.remove();
  }

  @Test
  public void testVecTypes(){
    Frame fr = new TestFrameBuilder()
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_TIME, Vec.T_STR)
            .build();

    assertArrayEquals(fr.names(), ar("col_0", "col_1", "col_2", "col_3"));
    assertEquals(fr.vecs().length, 4);
    assertEquals(fr.numRows(), 0);
    assertEquals(fr.numCols(), 4);
    assertEquals(fr.vec(0).get_type(), Vec.T_CAT);
    assertEquals(fr.vec(1).get_type(), Vec.T_NUM);
    assertEquals(fr.vec(2).get_type(), Vec.T_TIME);
    assertEquals(fr.vec(3).get_type(), Vec.T_STR);
    fr.remove();
  }


  /**
   * This test throws exception because size of specified vectors and size of specified names differ
   */
  @Test(expected = IllegalArgumentException.class)
  public void testWrongVecNameSize(){
    Frame fr = new TestFrameBuilder()
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_TIME, Vec.T_STR)
            .withColNames("A", "B")
            .build();
    fr.remove();
  }

  @Test
  public void testColNames(){
    Frame fr = new TestFrameBuilder()
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_TIME, Vec.T_STR)
            .withColNames("A", "B", "C", "D")
            .build();

    assertEquals(fr.vecs().length, 4);
    assertEquals(fr.numRows(), 0);
    assertEquals(fr.numCols(), 4);
    assertArrayEquals(fr.names(), ar("A", "B", "C", "D"));
    fr.remove();
  }
  @Test
  public void testDefaultChunks(){
    Frame fr = new TestFrameBuilder()
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_TIME, Vec.T_STR)
            .withColNames("A", "B", "C", "D")
            .build();

    assertArrayEquals(fr.anyVec().espc(), ar(0, 0)); // no data
    assertEquals(fr.anyVec().nChunks(), 1); // 1 empty chunk
    fr.remove();
  }

  /**
   *  This test throws exception because it expects more data ( via chunk layout) than is actually available
   */
  @Test(expected = IllegalArgumentException.class)
  public void testSetChunksToMany(){
    Frame fr = new TestFrameBuilder()
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_TIME, Vec.T_STR)
            .withColNames("A", "B", "C", "D")
            .withChunkLayout(2, 2, 2, 1) // we are requesting 7 rows to be able to create 4 chunks, but we have 0 rows
            .build();

    fr.remove();
  }

  /**
   *  This test throws exception because it gets more data than the chunks can contain (Total size of chunks is less 
   *  than the size of provided data) and it would result with frame with missing data in last rows
   */
  @Test(expected = IllegalArgumentException.class)
  public void testSetChunksFewerThanProvidedData(){
    Frame fr = new TestFrameBuilder()
            .withVecTypes(Vec.T_CAT, Vec.T_NUM)
            .withColNames("A", "B")
            .withDataForCol(0, ar("A", "B", "B", null, "F", "I"))
            .withDataForCol(1, ard(Double.NaN, 1, 2, 3, 4, 5.6))
            .withChunkLayout(1, 1, 2, 1) // we are requesting chunk capacity for 5 rows but provide 6 rows
            .build();

    fr.remove();
  }

  @Test
  public void testSetChunks(){
    final Frame fr = new TestFrameBuilder()
            .withName("frameName")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_NUM, Vec.T_STR)
            .withDataForCol(0, ard(Double.NaN, 1, 2, 3, 4, 5.6, 7))
            .withDataForCol(1, ar("A", "B", "C", null, "F", "I", "J"))
            .withChunkLayout(2, 2, 2, 1)
            .build();

    assertEquals(fr.anyVec().nChunks(), 4);
    assertArrayEquals(fr.anyVec().espc(), new long[]{0, 2, 4, 6, 7});
    // check data in the frame
    assertEquals(fr.vec(0).at(0), Double.NaN, DELTA);
    assertEquals(fr.vec(0).at(5), 5.6, DELTA);
    assertEquals(fr.vec(0).at(6), 7, DELTA);

    BufferedString strBuf = new BufferedString();
    assertEquals(fr.vec(1).atStr(strBuf,0).toString(), "A");
    assertNull(fr.vec(1).atStr(strBuf,3));
    assertEquals(fr.vec(1).atStr(strBuf,6).toString(), "J");

    fr.remove();
  }


  /**
   *  This test throws exception because the data has different length
   */
  @Test(expected = IllegalArgumentException.class)
  public void testDataDifferentSize(){
    final Frame fr = new TestFrameBuilder()
            .withVecTypes(Vec.T_NUM, Vec.T_STR)
            .withDataForCol(0, ard(Double.NaN, 1)) // 2 elements
            .withDataForCol(1, ar("A", "B", "C")) // 3 elements
            .build();

    fr.remove();
  }

  @Test
  public void withRandomIntDataForColTest(){
    long seed = 44L;
    int size = 1000;
    int min = 1;
    int max = 5;

    Frame fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA")
            .withVecTypes(Vec.T_NUM)
            .withRandomIntDataForCol(0, size, min, max, seed)
            .build();


    printOutFrameAsTable(fr, false, size);
    Vec generatedVec = fr.vec(0);
    for(int i = 0; i < size; i++) {
      assertTrue(generatedVec.at(i) <= max && generatedVec.at(i) >= min);
    }

    fr.delete();
  }

  @Test
  public void withRandomDoubleDataForColTest(){
    long seed = 44L;
    int size = 1000;
    int min = 1;
    int max = 5;

    Frame fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA")
            .withVecTypes(Vec.T_NUM)
            .withRandomDoubleDataForCol(0, size, min, max, seed)
            .build();


    printOutFrameAsTable(fr, false, size);
    Vec generatedVec = fr.vec(0);
    for(int i = 0; i < size; i++) {
      assertTrue(generatedVec.at(i) <= max && generatedVec.at(i) >= min);
    }

    fr.delete();
  }

  @Test
  public void numRowsIsWorkingForRandomlyGeneratedColumnsTest(){
    long seed = 44L;
    Frame fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA")
            .withVecTypes(Vec.T_NUM)
            .withRandomDoubleDataForCol(0, 1000, 1, 5, seed)
            .build();

    long numberOfRowsGenerated = fr.numRows();
    assertEquals(1000, numberOfRowsGenerated);

    fr.delete();
  }

  @Test
  public void withRandomBinaryDataForColTest(){
    long seed = 44L;
    Frame fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA")
            .withVecTypes(Vec.T_CAT)
            .withRandomBinaryDataForCol(0, 1000, seed)
            .build();

    assertEquals(2, fr.vec("ColA").cardinality());

    fr.delete();
  }

  @Test
  public void testBadVec(){
    try {
      Scope.enter();

      Frame frame = new TestFrameBuilder()
              .withVecTypes(Vec.T_BAD)
              .withDataForCol(0, ard(Float.NaN, Float.NaN, Float.NaN)) // All NaN column
              .withName("fr")
              .build();
      
      assertNotNull(frame);
      assertEquals(1, frame.numCols());

      final Vec badVec = frame.vec(0);
      assertEquals(Vec.T_BAD, badVec._type);
      assertEquals(3, badVec.length());
      assertTrue(badVec.isBad());

      for (int i = 0; i < badVec.length(); i++) {
        assertEquals(Float.NaN, badVec.at(i), 0D);
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testSequenceIntDataForCol() {
    Scope.enter();
    try {
      Frame f = new TestFrameBuilder()
              .withVecTypes(Vec.T_NUM)
              .withSequenceIntDataForCol(0, 0, 10)
              .build();
      assertEquals("Unexpected number of rows", 10, f.numRows());
      assertArrayEquals("It is not a valid sequence column",
              new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
              FrameUtils.asInts(f.vec(0)));
    } finally {
      Scope.exit();
    }
  }

  @Test(expected = AssertionError.class)
  public void testSequenceIntDataForColBadInput() {
    Scope.enter();
    try {
      new TestFrameBuilder()
              .withVecTypes(Vec.T_NUM)
              .withSequenceIntDataForCol(0, 0, 0)
              .build();
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testUUID(){
    Scope.enter();
    try {
      UUID expectedUUID = UUID.randomUUID();
      Frame f = new TestFrameBuilder()
              .withVecTypes(Vec.T_UUID)
              .withDataForCol(0, ar(expectedUUID.toString(), null))
              .build();
      assertEquals(2, f.numRows());
      assertArrayEquals(new byte[]{Vec.T_UUID}, f.types());
      assertEquals(expectedUUID.getLeastSignificantBits(), f.vec(0).at16l(0));
      assertEquals(expectedUUID.getMostSignificantBits(), f.vec(0).at16h(0));
      assertTrue(f.vec(0).isNA(1));
    } finally {
      Scope.exit();
    }
  }


}
