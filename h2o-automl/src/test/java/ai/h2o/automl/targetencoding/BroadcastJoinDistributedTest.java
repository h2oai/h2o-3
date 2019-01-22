package ai.h2o.automl.targetencoding;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.IcedHashMap;

import static ai.h2o.automl.targetencoding.BroadcastJoinForTargetEncoder.*;
import static org.junit.Assert.assertEquals;
import static water.H2O.CLOUD;


public class BroadcastJoinDistributedTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(2);
  }

  private Frame fr = null;

  @Test
  public void FrameWithEncodingDataToHashMapMRTastTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame2")
            .withColNames("ColA", TargetEncoder.NUMERATOR_COL_NAME, TargetEncoder.DENOMINATOR_COL_NAME)
            .withVecTypes(Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ar("a", "b", "c"))
            .withDataForCol(1, ar(22, 33, 42))
            .withDataForCol(2, ar(44, 66, 84))
            .build();

    
    assertEquals(2, CLOUD.size() );
    IcedHashMap<CompositeLookupKey, EncodingData> encodingDataMap = new FrameWithEncodingDataToHashMap(0, -1, 1, 2)
                    .doAll(fr)
                    .getEncodingDataMap();

    EncodingData actualDataA = encodingDataMap.get(new CompositeLookupKey("a", -1));
    assertEquals( 22, actualDataA.getNumerator()); 
    assertEquals( 44, actualDataA.getDenominator());
    EncodingData actualDataB = encodingDataMap.get(new CompositeLookupKey("b", -1));
    assertEquals(33,actualDataB.getNumerator());
    assertEquals(66,actualDataB.getDenominator());
    EncodingData actualDataC = encodingDataMap.get(new CompositeLookupKey("c", -1));
    assertEquals(42,actualDataC.getNumerator());
    assertEquals(84,actualDataC.getDenominator());

  }
  
  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

}
