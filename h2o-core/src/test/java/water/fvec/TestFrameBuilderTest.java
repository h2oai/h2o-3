package water.fvec;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;

public class TestFrameBuilderTest extends TestUtil {
  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testEmpty(){
    Frame fr = new TestFrameBuilder().build();

    assert fr.vecs().length == 0;
    assert fr.numCols() == 0;
    assert fr.numRows() == 0;
    fr.remove();
  }

  @Test
  public void testName(){
    Frame fr = new TestFrameBuilder()
            .withName("FrameName")
            .build();

    assert fr._key.toString().equals("FrameName");
    fr.remove();
  }

  @Test
  public void testVecTypes(){
    Frame fr = new TestFrameBuilder()
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_TIME, Vec.T_STR)
            .build();

    assert fr.vec(0).get_type() == Vec.T_CAT;
    assert fr.vec(1).get_type() == Vec.T_NUM;
    assert fr.vec(2).get_type() == Vec.T_TIME;
    assert fr.vec(3).get_type() == Vec.T_STR;
    fr.remove();
  }


  @Test(expected = IllegalArgumentException.class)
  public void testWrongVecNameSize(){
    new TestFrameBuilder()
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_TIME, Vec.T_STR)
            .withColNames("A", "B")
            .build();
  }

  @Test
  public void testDefaultChunks(){
    Frame fr = new TestFrameBuilder()
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_TIME, Vec.T_STR)
            .withColNames("A", "B", "C", "D")
            .build();

    assert fr.anyVec().nChunks() == 1;
    fr.remove();

  }

  // this tests expects more data ( via chunk layout) than is actuall available
  @Test(expected = IllegalArgumentException.class)
  public void testSetChunksToMany(){
    Frame fr = new TestFrameBuilder()
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_TIME, Vec.T_STR)
            .withColNames("A", "B", "C", "D")
            .withChunkLayout(2, 2, 2, 1)
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
            .withDataForCol(1, ar("A", "B", "C", "E", "F", "I", "J"))
            .withChunkLayout(2, 2, 2, 1)
            .build();

    assert fr.anyVec().nChunks() == 4;
    org.junit.Assert.assertArrayEquals(fr.anyVec().espc(), new long[]{0, 2, 4, 6, 7});
    fr.remove();
  }


  @Test(expected = IllegalArgumentException.class)
  public void testDataDifferentSize(){
    final Frame fr = new TestFrameBuilder()
            .withName("frameName")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_NUM, Vec.T_STR)
            .withDataForCol(0, ard(Double.NaN, 1, 2, 3, 4, 5.6, 7)) // 7 elements
            .withDataForCol(1, ar("A", "B", "C", "E", "F", "I", "J", "K")) // 8 elements
            .withChunkLayout(2, 2, 2, 1)
            .build();

    fr.remove();
  }

  @Test
  public void testCategorical(){
    final Frame fr = new TestFrameBuilder()
            .withName("frameName")
            .withColNames("ColA")
            .withVecTypes(Vec.T_CAT)
            .withDataForCol(0, ar("A", "B", "C", "A")) // 2 A, 1 B, 1 C
            .build();

    Assert.assertArrayEquals(fr.vec(0).domain(), ar("A", "B", "C"));
    assert fr.vec(0).cardinality() == 3;
    fr.remove();
  }
}
