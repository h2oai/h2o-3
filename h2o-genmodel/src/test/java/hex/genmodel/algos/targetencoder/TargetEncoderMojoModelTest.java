package hex.genmodel.algos.targetencoder;

import hex.genmodel.easy.RowData;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class TargetEncoderMojoModelTest {

  @Test
  public void computePriorMean(){
    EncodingMap encodingMap = new EncodingMap();

    int[] aTEComponents = {2, 5};
    encodingMap.put("A", aTEComponents);
    int[] bTEComponents = {3, 6};
    encodingMap.put("B", bTEComponents);
    int[] cTEComponents = {4, 7};
    encodingMap.put("C", cTEComponents);

    double priorMean = TargetEncoderMojoModel.computePriorMean(encodingMap);
    
    double expectedPriorMean = ((double) aTEComponents[0] + bTEComponents[0] + cTEComponents[0] ) / (aTEComponents[1] + bTEComponents[1] + cTEComponents[1]);
    assertEquals(expectedPriorMean, priorMean, 1e-5);
  }
  
  @Test
  public void computeLambda(){
    double lambda1 = TargetEncoderMojoModel.computeLambda(5, 5, 1);
    assertEquals(0.5, lambda1, 1e-5);

    double lambda2 = TargetEncoderMojoModel.computeLambda(1, 15, 1);
    assertEquals(0, lambda2, 1e-5);
    
    double lambda3 = TargetEncoderMojoModel.computeLambda(20, 5, 1);
    assertEquals(1, lambda3, 1e-5);
  }
  
  @Test
  public void computeBlendedEncoding(){
    Random rg = new Random();
    double labmda = rg.nextDouble();
    double posterior = rg.nextDouble();
    double prior = rg.nextDouble();
    double blendedValue = TargetEncoderMojoModel.computeBlendedEncoding(labmda, posterior, prior);
    assertTrue(blendedValue >= 0 && blendedValue <=1);

    double blendedValue2 = TargetEncoderMojoModel.computeBlendedEncoding(0.5, 1, 0);
    assertEquals(0.5, blendedValue2, 1e-5);
    double blendedValue3 = TargetEncoderMojoModel.computeBlendedEncoding(0.5, 1, 1);
    assertEquals(1, blendedValue3, 1e-5);
    double blendedValue4 = TargetEncoderMojoModel.computeBlendedEncoding(0.5, 0, 0);
    assertEquals(0, blendedValue4, 1e-5);
    double blendedValue5 = TargetEncoderMojoModel.computeBlendedEncoding(0.1, 0.8, 0.6);
    assertEquals(0.08 + 0.9*0.6, blendedValue5, 1e-5);
  }
  
  @Test
  public void transformWithBlending() {
    TargetEncoderMojoModel targetEncoderMojoModel = new TargetEncoderMojoModel(null, null, null);
    EncodingMaps encodingMap = new EncodingMaps();
    EncodingMap encodingMapForCat1 = new EncodingMap();
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
    targetEncoderMojoModel.transform(rowToPredictFor);
    assertEquals(0.45, (double) rowToPredictFor.get(predictorName + "_te"), 1e-5);

    // Case when number of training examples is higher than inflection point. Encoding should be closer to prior
    targetEncoderMojoModel._inflectionPoint = 12;
    RowData rowToPredictFor2 = new RowData();
    rowToPredictFor2.put(predictorName, "B");

    targetEncoderMojoModel.transform(rowToPredictFor2);
    assertEquals(0.5, (double) rowToPredictFor2.get(predictorName + "_te"), 1e-5);

  }

  @Test
  public void transformWithoutBlending() {
    TargetEncoderMojoModel targetEncoderMojoModel = new TargetEncoderMojoModel(null, null, null);
    EncodingMaps encodingMap = new EncodingMaps();
    EncodingMap encodingMapForCat1 = new EncodingMap();
    encodingMapForCat1.put("A", new int[]{2,5});
    encodingMapForCat1.put("B", new int[]{3,7});

    String predictorName = "categ_var1";
    encodingMap.put(predictorName, encodingMapForCat1);

    targetEncoderMojoModel._targetEncodingMap = encodingMap;
    targetEncoderMojoModel._withBlending = false;

    RowData rowToPredictFor = new RowData();
    rowToPredictFor.put(predictorName, "A");

    targetEncoderMojoModel.transform(rowToPredictFor);
    assertEquals(0.4, (double) rowToPredictFor.get(predictorName + "_te"), 1e-5);
    
    RowData rowToPredictFor2 = new RowData();
    rowToPredictFor2.put(predictorName, "B");

    targetEncoderMojoModel.transform(rowToPredictFor2);
    assertEquals(0.42857, (double) rowToPredictFor2.get(predictorName + "_te"), 1e-5);
  }
  
  @Test
  public void transformUnknownCategories() {
    TargetEncoderMojoModel targetEncoderMojoModel = new TargetEncoderMojoModel(null, null, null);
    EncodingMaps encodingMap = new EncodingMaps();
    EncodingMap encodingMapForCat1 = new EncodingMap();
    encodingMapForCat1.put("A", new int[]{2,5});
    encodingMapForCat1.put("B", new int[]{3,7});

    String predictorName = "categ_var1";
    encodingMap.put(predictorName, encodingMapForCat1);

    targetEncoderMojoModel._targetEncodingMap = encodingMap;

    RowData rowToPredictFor = new RowData();
    rowToPredictFor.put(predictorName, "C"); // "C" is unknown category

    targetEncoderMojoModel.transform(rowToPredictFor);
    assertEquals((2.0 + 3) / (5 + 7), (double) rowToPredictFor.get(predictorName + "_te"), 1e-5);
  }
}
