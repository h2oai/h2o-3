package hex;

import ai.h2o.automl.targetencoding.BlendingParams;
import ai.h2o.automl.targetencoding.TargetEncoder;
import ai.h2o.automl.targetencoding.TargetEncoderFrameHelper;
import hex.genmodel.MojoModel;
import hex.genmodel.TojoTransformer;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

/**
 * Checking that we can store our transformer into zip and then load without corruption of the encoding map
 */
public class FeatureTransformerWriterTest extends TestUtil implements ModelStubs {

  @BeforeClass public static void stall() { stall_till_cloudsize(1); }
  
  Frame trainFrame = parse_test_file("./smalldata/gbm_test/titanic.csv");
  
  private Map<String, Frame> getTEMap() {
    
    String responseColumnName = "survived";
    asFactor(trainFrame, responseColumnName);
    
    BlendingParams params = new BlendingParams(3, 1);
    String[] teColumns = {"home.dest", "embarked"};
    TargetEncoder targetEncoder = new TargetEncoder(teColumns, params);
    Map<String, Frame> testEncodingMap = targetEncoder.prepareEncodingMap(trainFrame, responseColumnName, null);
    return testEncodingMap;
  }

  @Test public void writeAndLoadTransformer() throws Exception{
    TestModel.TestParam p = new TestModel.TestParam();
    TestModel.TestOutput o = new TestModel.TestOutput();
    o.setNames(trainFrame.names());
    o._domains = trainFrame.domains();
    
    TargetEncoderTmpRepresentative te = new TargetEncoderTmpRepresentative();
    Map<String, Frame> teMap = getTEMap();
    te.setTargetEncodingMap(teMap);

    String fileName = "test_pojo_te.zip";

    Scope.enter();
    try {
      FileOutputStream modelOutput = new FileOutputStream(fileName);
      te.getWriter().writeTo(modelOutput);
      modelOutput.close();

      assertTrue(new File(fileName).exists());
      
      TojoTransformer tt = TojoTransformer.loadTransformer(fileName);

      Frame encodingMapForHomeDest = teMap.get("home.dest");
      printOutFrameAsTable(encodingMapForHomeDest);

      Frame head = encodingMapForHomeDest.deepSlice(new long[]{0,1,2,3,4,5}, null);
      printOutFrameAsTable(head);

      Map<String, int[]> loadedEncodingMapForHomeDest = tt.encodingMap.get("home.dest");
      
      assertArrayEquals(new int[] {(int)head.vec(1).at8(0), (int)head.vec(2).at8(0)},  loadedEncodingMapForHomeDest.get("?Havana  Cuba"));
      assertArrayEquals(new int[] {(int)head.vec(1).at8(1), (int)head.vec(2).at8(1)},  loadedEncodingMapForHomeDest.get("Aberdeen / Portland  OR"));
      assertArrayEquals(new int[] {(int)head.vec(1).at8(2), (int)head.vec(2).at8(2)},  loadedEncodingMapForHomeDest.get("Albany  NY"));
      assertArrayEquals(new int[] {(int)head.vec(1).at8(3), (int)head.vec(2).at8(3)},  loadedEncodingMapForHomeDest.get("Altdorf  Switzerland"));
      assertArrayEquals(new int[] {(int)head.vec(1).at8(4), (int)head.vec(2).at8(4)},  loadedEncodingMapForHomeDest.get("Amenia  ND"));
      assertArrayEquals(new int[] {(int)head.vec(1).at8(5), (int)head.vec(2).at8(5)},  loadedEncodingMapForHomeDest.get("Antwerp  Belgium / Stanton  OH"));
 
    } finally {
      File file = new File(fileName);
      if (file.exists()) {
        file.delete();
      }
      trainFrame.delete();
      TargetEncoderFrameHelper.encodingMapCleanUp(teMap);
      Scope.exit();
    }
  }

}
