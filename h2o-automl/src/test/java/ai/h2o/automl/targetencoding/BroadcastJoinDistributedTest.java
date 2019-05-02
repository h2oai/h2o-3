package ai.h2o.automl.targetencoding;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.IcedHashMap;

import static ai.h2o.automl.targetencoding.BroadcastJoinForTargetEncoder.*;
import static ai.h2o.automl.targetencoding.TargetEncoder.DENOMINATOR_COL_NAME;
import static ai.h2o.automl.targetencoding.TargetEncoder.NUMERATOR_COL_NAME;
import static org.junit.Assert.*;
import static water.H2O.CLOUD;


public class BroadcastJoinDistributedTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(2);
  }

  private Frame fr = null;

  @Test
  public void FrameWithEncodingDataToHashMapMRTaskTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame2")
            .withColNames("ColA", NUMERATOR_COL_NAME, DENOMINATOR_COL_NAME)
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "c", "d", "f"))
            .withDataForCol(1, ar(22, 33, 42, 25, 50))
            .withDataForCol(2, ar(44, 66, 84, 57, 68))
            .withChunkLayout(1, 1, 1, 1, 1)
            .build();
    
    assertTrue( CLOUD.size() >= 2 );
    IcedHashMap<CompositeLookupKey, EncodingData> encodingDataMap = new FrameWithEncodingDataToHashMap(0, -1, 1, 2)
                    .doAll(fr)
                    .getEncodingDataMap();

    EncodingData actualDataA = encodingDataMap.get(new CompositeLookupKey("a", -1));
    assertNotNull( actualDataA); 
    assertEquals( 22, actualDataA.getNumerator()); 
    assertEquals( 44, actualDataA.getDenominator());
    
    EncodingData actualDataB = encodingDataMap.get(new CompositeLookupKey("b", -1));
    assertNotNull( actualDataB);
    assertEquals(33,actualDataB.getNumerator());
    assertEquals(66,actualDataB.getDenominator());
    
    
    EncodingData actualDataC = encodingDataMap.get(new CompositeLookupKey("c", -1));
    assertNotNull( actualDataC);
    assertEquals(42,actualDataC.getNumerator());
    assertEquals(84,actualDataC.getDenominator());

  }

  @Test
  public void joinWithoutFoldColumnTest() {

    Frame rightFr = null;
    Vec emptyNumerator = null;
    Vec emptyDenominator = null;
    try {

      fr = new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, ar("a", "c", "b"))
              .build();

      rightFr = new TestFrameBuilder()
              .withName("testFrame2")
              .withColNames("ColA", NUMERATOR_COL_NAME, DENOMINATOR_COL_NAME)
              .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ar("a", "b", "c"))
              .withDataForCol(1, ar(22, 33, 42))
              .withDataForCol(2, ar(44, 66, 84))
              .withChunkLayout(1,1,1)
              .build();

      emptyNumerator = Vec.makeZero(fr.numRows());
      fr.add(NUMERATOR_COL_NAME, emptyNumerator);
      emptyDenominator = Vec.makeZero(fr.numRows());
      fr.add(DENOMINATOR_COL_NAME, emptyDenominator);

      Frame joined = BroadcastJoinForTargetEncoder.join(fr, new int[]{0}, -1, rightFr, new int[]{0}, -1);

      Scope.enter();
      assertStringVecEquals(cvec("a", "c", "b"), joined.vec("ColA"));
      assertVecEquals(vec(22, 42, 33), joined.vec(NUMERATOR_COL_NAME), 1e-5);
      assertVecEquals(vec(44, 84, 66), joined.vec(DENOMINATOR_COL_NAME), 1e-5);
      Scope.exit();
      printOutFrameAsTable(fr, false, fr.numRows());
    } finally {
      if(rightFr != null) rightFr.delete();
    }
  }
  
  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

}
