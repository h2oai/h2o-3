package ai.h2o.automl.targetencoding;

import org.junit.*;
import water.MRTask;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static water.H2O.CLOUD;

public class TargetEncodingDistributedTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  private Frame fr = null;

  @Test
  public void imputeNAsForColumnTest() {

    Assume.assumeTrue(CLOUD.size() >= 2);
    
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

    String nullStr = null;
    fr.vec(0).set(2, nullStr);

    String[] teColumns = {""};
    TargetEncoder tec = new TargetEncoder(teColumns);

    assertTrue(fr.vec("ColA").isCategorical());
    assertEquals(2, fr.vec("ColA").cardinality());

    Frame res = tec.imputeNAsForColumn(fr, "ColA", "ColA_NA");

    new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        for (int i = 0; i < cs[0].len(); i++) {
          long levelValue = cs[0].at8(i);

          System.out.println("Level value: " + levelValue);
          System.out.println("Domain: " + Arrays.toString(cs[0].vec().domain()));
          int length = cs[0].vec().domain().length;
          System.out.println("Domain length: " + length);
          cs[0].vec().factor(2);
        }
      }
    }.doAll(res);
    
    // assumption is that domain is being properly distributed over nodes 
    // and there will be no exception while attempting to access new domain's value in `cs[0].vec().factor(2);`
    
    res.delete();
  }
  
  @Test
  public void emptyStringsAndNAsAreTreatedAsDifferentCategoriesTest() {

    Assume.assumeTrue(CLOUD.size() >= 2);
    
    String teColumnName = "ColA";
    String targetColumnName = "ColB";
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames(teColumnName, targetColumnName)
            .withVecTypes(Vec.T_CAT, Vec.T_CAT)
            .withDataForCol(0, ar("a", "b", "", "", null)) // null and "" are different categories even though they look the same in printout
            .withDataForCol(1, ar("2", "6", "6", "2", "6"))
            .withChunkLayout(3, 2)
            .build();

    String[] teColumns = {teColumnName};
    TargetEncoder tec = new TargetEncoder(teColumns);

    Map<String, Frame> targetEncodingMap = tec.prepareEncodingMap(fr, targetColumnName, null);

    Frame resultWithEncoding = tec.applyTargetEncoding(fr, targetColumnName, targetEncodingMap, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, false, 0.0, false, 1234);

    printOutFrameAsTable(resultWithEncoding);

    encodingMapCleanUp(targetEncodingMap);
    resultWithEncoding.delete();
  }


  private void encodingMapCleanUp(Map<String, Frame> encodingMap) {
    for (Map.Entry<String, Frame> map : encodingMap.entrySet()) {
      map.getValue().delete();
    }
  }
  
  @After
  public void afterEach() {
    if (fr != null) fr.delete();
  }

}
