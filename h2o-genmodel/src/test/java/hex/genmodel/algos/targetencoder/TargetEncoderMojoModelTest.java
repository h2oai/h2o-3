package hex.genmodel.algos.targetencoder;

import hex.genmodel.easy.RowData;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class TargetEncoderMojoModelTest {

  @Test
  public void computePriorMean(){
    TargetEncoderMojoModel targetEncoderMojoModel = new TargetEncoderMojoModel(null, null, null);

    Map<String, int[]> encodingMap = new HashMap<>();

    int[] aTEComponents = {2, 5};
    encodingMap.put("A", aTEComponents);
    int[] bTEComponents = {3, 6};
    encodingMap.put("B", bTEComponents);
    int[] cTEComponents = {4, 7};
    encodingMap.put("C", cTEComponents);

    double priorMean = targetEncoderMojoModel.computePriorMean(encodingMap);
    
    double expectedPriorMean = ((double) aTEComponents[0] + bTEComponents[0] + cTEComponents[0] ) / (aTEComponents[1] + bTEComponents[1] + cTEComponents[1]);
    assertEquals(expectedPriorMean, priorMean, 1e-5);
  }
  
  @Test
  public void transformWithBlending() {
    TargetEncoderMojoModel targetEncoderMojoModel = new TargetEncoderMojoModel(null, null, null);
    HashMap<String, Map<String, int[]>> encodingMap = new HashMap<>();
    Map<String, int[]> encodingMapForCat1 = new HashMap<>();
    encodingMapForCat1.put("A", new int[]{2,5});
    encodingMapForCat1.put("B", new int[]{3,6});
    encodingMapForCat1.put("C", new int[]{4,7});

    String predictorName = "categ_var1";
    encodingMap.put(predictorName, encodingMapForCat1);

    targetEncoderMojoModel._targetEncodingMap = encodingMap;
    targetEncoderMojoModel._withBlending = true;
    targetEncoderMojoModel._inflectionPoint = 5;
    targetEncoderMojoModel._smoothing = 1;

    RowData rowToPredictFor = new RowData();
    rowToPredictFor.put(predictorName, "A");
    
    // Case when number of training examples equal to inflection point. Encoding should be between prior and posterior
    targetEncoderMojoModel.transform0(rowToPredictFor);
    assertEquals(0.45, (double) rowToPredictFor.get(predictorName + "_te"), 1e-5);

    // Case when number of training examples is higher than inflection point. Encoding should be closer to prior
    targetEncoderMojoModel._inflectionPoint = 12;
    RowData rowToPredictFor2 = new RowData();
    rowToPredictFor2.put(predictorName, "B");

    targetEncoderMojoModel.transform0(rowToPredictFor2);
    assertEquals(0.5, (double) rowToPredictFor2.get(predictorName + "_te"), 1e-5);

  }

  @Test
  public void transformWithoutBlending() {
    TargetEncoderMojoModel targetEncoderMojoModel = new TargetEncoderMojoModel(null, null, null);
    HashMap<String, Map<String, int[]>> encodingMap = new HashMap<>();
    Map<String, int[]> encodingMapForCat1 = new HashMap<>();
    encodingMapForCat1.put("A", new int[]{2,5});
    encodingMapForCat1.put("B", new int[]{3,7});

    String predictorName = "categ_var1";
    encodingMap.put(predictorName, encodingMapForCat1);

    targetEncoderMojoModel._targetEncodingMap = encodingMap;
    targetEncoderMojoModel._withBlending = false;

    RowData rowToPredictFor = new RowData();
    rowToPredictFor.put(predictorName, "A");

    targetEncoderMojoModel.transform0(rowToPredictFor);
    assertEquals(0.4, (double) rowToPredictFor.get(predictorName + "_te"), 1e-5);
    
    RowData rowToPredictFor2 = new RowData();
    rowToPredictFor2.put(predictorName, "B");

    targetEncoderMojoModel.transform0(rowToPredictFor2);
    assertEquals(0.42857, (double) rowToPredictFor2.get(predictorName + "_te"), 1e-5);
  }
}
