package ai.h2o.automl.targetencoding;

import org.apache.commons.lang.ArrayUtils;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import water.MRTask;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.Map;

public class TargetEncodingLeaveOneOutStrategyDistributedTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(2);
  }

  private Frame fr = null;

  @Test
  public void naValuesWithLOOStrategyTest() {
    String teColumnName = "ColA";
    String targetColumnName = "ColB";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames(teColumnName, targetColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", null, null, null))
            .withDataForCol(1, ar("2", "6", "6", "2", "6"))
            .withChunkLayout(3,2)
            .build();

    String[] teColumns = {teColumnName};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, null);

    printOutFrameAsTable(targetEncodingMap.get(teColumnName));
    Frame resultWithEncodings = tec.applyTargetEncoding(fr, targetColumnName, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, false,0.0, true, 1234);
    printOutFrameAsTable(resultWithEncodings);


    Vec expected = dvec(0.6, 0.6, 0.5, 1, 0.5);
    printOutFrameAsTable(resultWithEncodings);
    assertVecEquals(expected, resultWithEncodings.vec("ColA_te"), 1e-5);

    expected.remove();
    encodingMapCleanUp(targetEncodingMap);
    resultWithEncodings.delete();
    
  }

  @Test
  public void setDomainUpdateTest() {
    String teColumnName = "ColA";
    String targetColumnName = "ColB";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames(teColumnName, targetColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "", "", null)) // null and "" are different categories even though they look the same in printout
            .withDataForCol(1, ar("2", "6", "6", "2", "6"))
            .withChunkLayout(2,3) 
            .build();

    String[] teColumns = {teColumnName};
    TargetEncoder tec = new TargetEncoder(teColumns);
    Frame imputed = tec.imputeNAsForColumn(fr, teColumnName, "test_value");
    

    new MRTask() {
      @Override
      public void map(Chunk cs[]) {
        Chunk num = cs[0];
        System.out.println("Domain:" + ArrayUtils.toString(num.vec().domain()));
        if(num.vec().domain().length != 4) throw new IllegalStateException("Domain was not properly propagated throughout the cloud");
      }
    }.doAll(imputed);

  }

  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

  private void encodingMapCleanUp(Map<String, Frame> encodingMap) {
    for (Map.Entry<String, Frame> map : encodingMap.entrySet()) {
      map.getValue().delete();
    }
  }
}
