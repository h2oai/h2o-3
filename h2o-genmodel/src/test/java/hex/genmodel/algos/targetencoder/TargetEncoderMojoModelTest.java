package hex.genmodel.algos.targetencoder;

import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.RowToRawDataConverter;
import hex.genmodel.easy.error.VoidErrorConsumer;
import hex.genmodel.easy.exception.PredictException;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TargetEncoderMojoModelTest {

  @Test
  public void computePriorMean(){
    EncodingMap encodingMap = new EncodingMap();

    int[] aTEComponents = {2, 5};
    encodingMap.put(0, aTEComponents);
    int[] bTEComponents = {3, 6};
    encodingMap.put(1, bTEComponents);
    int[] cTEComponents = {4, 7};
    encodingMap.put(2, cTEComponents);

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
  public void transformWithBlending() throws PredictException {
    String[][] domains = new String[3][2];
    domains[0] = null;
    domains[1][0] = "A";
    domains[1][1] = "B";
    domains[2] = null;

    String predictorName = "categ_var1";
    String numerical_col1 = "numerical_col1";
    String numerical_col2 = "numerical_col2";

    String[] names = new String[]{numerical_col1, predictorName, numerical_col2};
    
    TargetEncoderMojoModel targetEncoderMojoModel = new TargetEncoderMojoModel(names, domains, null);
    targetEncoderMojoModel._nfeatures = names.length; // model.getNumCols is overriden in MojoModel and GenModel's version which is based on `names` is not used
    
    EncodingMaps encodingMap = new EncodingMaps();
    EncodingMap encodingMapForCat1 = new EncodingMap();
    int factorForLevelA = 0;
    int factorForLevelB = 1;
    int factorForLevelC = 2;
    encodingMapForCat1.put(factorForLevelA, new int[]{2,5}); 
    encodingMapForCat1.put(factorForLevelB, new int[]{3,6}); 
    encodingMapForCat1.put(factorForLevelC, new int[]{4,7}); // remove
    
    encodingMap.put(predictorName, encodingMapForCat1);

    targetEncoderMojoModel._targetEncodingMap = encodingMap;
    targetEncoderMojoModel._withBlending = true;
    targetEncoderMojoModel._inflectionPoint = 5;
    targetEncoderMojoModel._smoothing = 1;

    
    targetEncoderMojoModel._teColumnNameToIdx = new HashMap<>();
    targetEncoderMojoModel._teColumnNameToIdx.put(predictorName, 1);


    VoidErrorConsumer errorConsumer = new VoidErrorConsumer();
    HashMap<String, Integer> modelColumnNameToIndexMap = new HashMap<>();
    modelColumnNameToIndexMap.put(predictorName, 1);
    modelColumnNameToIndexMap.put(numerical_col1, 0);
    modelColumnNameToIndexMap.put(numerical_col2, 2);

    HashMap<Integer, HashMap<String, Integer>> domainMap = EasyPredictModelWrapper.constructDomainMap(targetEncoderMojoModel);
    RowToRawDataConverter rowToRawDataConverter = new RowToRawDataConverter(targetEncoderMojoModel, modelColumnNameToIndexMap, domainMap, errorConsumer, true, true);


    // Case when number of training examples equal to inflection point. Encoding should be between prior and posterior
    RowData rowToPredictFor = new RowData();
    rowToPredictFor.put(numerical_col1, 42.0);
    rowToPredictFor.put(predictorName, "A");
    rowToPredictFor.put(numerical_col2, 10.0);
    
    double[] rawData = nanArray(3);
    double[] testRawData = rowToRawDataConverter.convert(rowToPredictFor, rawData);
    double[] preds = new double[1];
    targetEncoderMojoModel.score0(testRawData, preds);
    assertEquals(0.45, preds[0], 1e-5);

    // Case when number of training examples is lower than inflection point. Encoding should be closer to prior
    targetEncoderMojoModel._inflectionPoint = 12;
    
    RowData rowToPredictFor2 = new RowData();

    rowToPredictFor2.put(numerical_col1, 42.0);
    rowToPredictFor2.put(predictorName, "B");
    rowToPredictFor2.put(numerical_col2, 10.0);

    double[] rawData2 = nanArray(3);
    double[] testRawData2 = rowToRawDataConverter.convert(rowToPredictFor2, rawData2);
    double[] preds2 = new double[1];

    targetEncoderMojoModel.score0(testRawData2, preds2);
    assertEquals(0.5, preds2[0], 1e-5);

  }

  @Test
  public void transformWithoutBlending() throws PredictException{
    String[][] domains = new String[3][2];
    domains[0] = null;
    domains[1][0] = "A";
    domains[1][1] = "B";
    domains[2] = null;

    String predictorName = "categ_var1";
    String numerical_col1 = "numerical_col1";
    String numerical_col2 = "numerical_col2";

    String[] names = new String[]{numerical_col1, predictorName, numerical_col2};

    TargetEncoderMojoModel targetEncoderMojoModel = new TargetEncoderMojoModel(names, domains, null);
    targetEncoderMojoModel._nfeatures = names.length; // model.getNumCols is overriden in MojoModel and GenModel's version which is based on `names` is not used

    EncodingMaps encodingMap = new EncodingMaps();
    EncodingMap encodingMapForCat1 = new EncodingMap();
    int factorForLevelA = 0;
    int factorForLevelB = 1;
    encodingMapForCat1.put(factorForLevelA, new int[]{2,5});
    encodingMapForCat1.put(factorForLevelB, new int[]{3,7});

    encodingMap.put(predictorName, encodingMapForCat1);

    targetEncoderMojoModel._targetEncodingMap = encodingMap;
    targetEncoderMojoModel._withBlending = false;

    targetEncoderMojoModel._teColumnNameToIdx = new HashMap<>();
    targetEncoderMojoModel._teColumnNameToIdx.put(predictorName, 1);


    VoidErrorConsumer errorConsumer = new VoidErrorConsumer();
    HashMap<String, Integer> modelColumnNameToIndexMap = new HashMap<>();
    modelColumnNameToIndexMap.put(predictorName, 1);
    modelColumnNameToIndexMap.put(numerical_col1, 0);
    modelColumnNameToIndexMap.put(numerical_col2, 2);

    HashMap<Integer, HashMap<String, Integer>> domainMap = EasyPredictModelWrapper.constructDomainMap(targetEncoderMojoModel);
    RowToRawDataConverter rowToRawDataConverter = new RowToRawDataConverter(targetEncoderMojoModel, modelColumnNameToIndexMap, domainMap, errorConsumer, true, true);

    RowData rowToPredictFor = new RowData();
    rowToPredictFor.put(numerical_col1, 42.0);
    rowToPredictFor.put(predictorName, "A");
    rowToPredictFor.put(numerical_col2, 10.0);

    double[] rawData = nanArray(3);
    double[] testRawData = rowToRawDataConverter.convert(rowToPredictFor, rawData);
    double[] preds = new double[1];
    
    targetEncoderMojoModel.score0(testRawData, preds);
    
    double expectedPosteriorProbabilityForLevelA = 0.4; // see `encodingMapForCat1` : num = 2, den = 5
    assertEquals(expectedPosteriorProbabilityForLevelA, preds[0], 1e-5);
  }
  
  @Test
  public void transformUnknownCategories() throws PredictException{
    String[][] domains = new String[3][2];
    domains[0] = null;
    domains[1][0] = "A";
    domains[1][1] = "B";
    domains[2] = null;

    String predictorName = "categ_var1";
    String numerical_col1 = "numerical_col1";
    String numerical_col2 = "numerical_col2";

    String[] names = new String[]{numerical_col1, predictorName, numerical_col2};

    TargetEncoderMojoModel targetEncoderMojoModel = new TargetEncoderMojoModel(names, domains, null);
    targetEncoderMojoModel._nfeatures = names.length; // model.getNumCols is overriden in MojoModel and GenModel's version which is based on `names` is not used

    EncodingMaps encodingMap = new EncodingMaps();
    EncodingMap encodingMapForCat1 = new EncodingMap();
    int factorForLevelA = 0;
    int factorForLevelB = 1;
    encodingMapForCat1.put(factorForLevelA, new int[]{2,5});
    encodingMapForCat1.put(factorForLevelB, new int[]{3,7});

    encodingMap.put(predictorName, encodingMapForCat1);

    targetEncoderMojoModel._targetEncodingMap = encodingMap;
    targetEncoderMojoModel._withBlending = false;

    targetEncoderMojoModel._teColumnNameToIdx = new HashMap<>();
    targetEncoderMojoModel._teColumnNameToIdx.put(predictorName, 1);


    VoidErrorConsumer errorConsumer = new VoidErrorConsumer();
    HashMap<String, Integer> modelColumnNameToIndexMap = new HashMap<>();
    modelColumnNameToIndexMap.put(predictorName, 1);
    modelColumnNameToIndexMap.put(numerical_col1, 0);
    modelColumnNameToIndexMap.put(numerical_col2, 2);

    HashMap<Integer, HashMap<String, Integer>> domainMap = EasyPredictModelWrapper.constructDomainMap(targetEncoderMojoModel);
    RowToRawDataConverter rowToRawDataConverter = new RowToRawDataConverter(targetEncoderMojoModel, modelColumnNameToIndexMap, domainMap, errorConsumer, true, true);

    RowData rowToPredictFor = new RowData();
    rowToPredictFor.put(numerical_col1, 42.0);
    rowToPredictFor.put(predictorName, "C");
    rowToPredictFor.put(numerical_col2, 10.0);

    double[] rawData = nanArray(3);
    double[] testRawData = rowToRawDataConverter.convert(rowToPredictFor, rawData);
    double[] preds = new double[1];

    targetEncoderMojoModel.score0(testRawData, preds);

    double expectedPriorProbabilityForLevelA = (2.0 + 3) / (5 + 7);
    assertEquals(expectedPriorProbabilityForLevelA, preds[0], 1e-5);
  }
  
  // We test that order of transformation/predictions is determined by index of teColumn in the input data.
  @Test
  public void sortEncodingMapByIndex() {
    TargetEncoderMojoModel targetEncoderMojoModel = new TargetEncoderMojoModel(null, null, null);
    EncodingMaps encodingMaps = new EncodingMaps();
    EncodingMap encodingMapForCat1 = new EncodingMap();
    int factorValueForA = 0;
    int factorValueForB = 1;
    encodingMapForCat1.put(factorValueForA, new int[]{2,5});
    encodingMapForCat1.put(factorValueForB, new int[]{3,7});

    String predictorName = "categ_var1";
    encodingMaps.put(predictorName, encodingMapForCat1);
    
    String predictorName2 = "categ_var2";
    encodingMaps.put(predictorName2, encodingMapForCat1);
    
    String predictorName3 = "categ_var3";
    encodingMaps.put(predictorName3, encodingMapForCat1);

    Map<String, Integer> teColumnNameToIdx = new HashMap<>();
    teColumnNameToIdx.put(predictorName, 42);
    teColumnNameToIdx.put(predictorName2, 100);
    teColumnNameToIdx.put(predictorName3, 7);

    targetEncoderMojoModel._teColumnNameToIdx = teColumnNameToIdx;

    LinkedHashMap<String, EncodingMap> sortedByColumnIndex = targetEncoderMojoModel.sortByColumnIndex(encodingMaps.encodingMap());

    ArrayList<String> sortedTeColumns = new ArrayList<>();
    for (Iterator<Map.Entry<String, EncodingMap>> it = sortedByColumnIndex.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, EncodingMap> entry = it.next();
      sortedTeColumns.add(entry.getKey());
    }
    
    assertArrayEquals(new String[]{predictorName3, predictorName, predictorName2}, sortedTeColumns.toArray(new String[0]));
  }

  private static double[] nanArray(int len) {
    double[] arr = new double[len];
    for (int i = 0; i < len; i++) {
      arr[i] = Double.NaN;
    }
    return arr;
  }
}
