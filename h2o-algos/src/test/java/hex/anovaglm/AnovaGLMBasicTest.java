package hex.anovaglm;

import hex.DataInfo;
import hex.glm.GLMModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.*;

import static hex.anovaglm.ANOVAGLMUtils.*;
import static hex.glm.GLMModel.GLMParameters.Family.*;
import static hex.glm.GLMModel.GLMParameters.Link.identity;
import static hex.glm.GLMModel.GLMParameters.Link.log;
import static org.junit.Assert.assertTrue;
import static water.TestUtil.parseTestFile;
import static water.util.ArrayUtils.flat;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class AnovaGLMBasicTest {

  /**
   * Test to check that training frame has been transformed correctly with two categorical input columns.  In addition,
   * I am checking its value from R:
   * 
   * Analysis of Deviance Table (Type III tests)
   *
   * Response: conformity
   *                          LR Chisq Df Pr(>Chisq)    
   * fcategory                  1.7178  2  0.4236344    
   * partner.status            11.4250  1  0.0007246 ***
   * fcategory:partner.status   8.3692  2  0.0152279 *  
   */
  @Test
  public void testFrameTransformGaussian() {
    try {
      Scope.enter();
      Frame correctFrame = parseTestFile("smalldata/anovaGlm/MooreTransformed.csv");
      String[] correctNames = new String[]{"fcategory1", "fcategory2", "partner.status1", "fcategory1:partner.status1",
              "fcategory2:partner.status1"};
      Frame train = parseTestFile("smalldata/anovaGlm/Moore.csv");Scope.track(correctFrame);
      Scope.track(train);

      ANOVAGLMModel.ANOVAGLMParameters params = new ANOVAGLMModel.ANOVAGLMParameters();
      params._family = gaussian;
      params._response_column = "conformity";
      params._train = train._key;
      params._solver = GLMModel.GLMParameters.Solver.IRLSM;
      params._ignored_columns = new String[]{"fscore"};
      params._save_transformed_framekeys = true;
      ANOVAGLMModel anovaG = new ANOVAGLM(params).trainModel().get();
      Scope.track_generic(anovaG);
      Frame transformedFrame = DKV.getGet(anovaG._output._transformedColumnKey);
      Scope.track(transformedFrame);
      String[] tNames = new String[]{"fcategory_high", "fcategory_low", "partner.status_high", 
              "fcategory_high:partner.status_high", "fcategory_low:partner.status_high"};
      // check and make sure dataset transformation is correct
      TestUtil.assertIdenticalUpToRelTolerance(Scope.track(correctFrame.subframe(correctNames)), 
              Scope.track(transformedFrame.subframe(tNames)), 0);
      // check model summary with correct SS
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "fcategory", "F") *
                      getModelSummaryIntField(anovaG, "fcategory", "DF")-1.7178) < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "partner.status", "F") *
              getModelSummaryIntField(anovaG, "partner.status", "DF") -11.4250) < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "fcategory:partner.status", "F") *
              getModelSummaryIntField(anovaG, "fcategory:partner.status", "DF")-8.3692) < 1e-2);
      System.out.println("Completed test testFrameTransformGaussian");
      
    } finally {
      Scope.exit();
    }
  }
  
  public static double getModelSummaryDoubleField(ANOVAGLMModel anovaG, String rowHeader, String colHeader) {
    int colIndex = Arrays.asList(anovaG._output._model_summary.getColHeaders()).indexOf(colHeader);
    int rowIndex = Arrays.asList(anovaG._output._model_summary.getRowHeaders()).indexOf(rowHeader);
    return (Double) anovaG._output._model_summary.get(rowIndex, colIndex);
  }
  
  public static int getModelSummaryIntField(ANOVAGLMModel anovaG, String rowHeader, String colHeader) {
    int colIndex = Arrays.asList(anovaG._output._model_summary.getColHeaders()).indexOf(colHeader);
    int rowIndex = Arrays.asList(anovaG._output._model_summary.getRowHeaders()).indexOf(rowHeader);
    return (int) anovaG._output._model_summary.get(rowIndex, colIndex);
  }

  /**
   * Test to make sure that weight and offset columns are preserved after input transformation
   */
  @Test
  public void testWeightOffset() {
    try {
      Scope.enter();
      Frame train = parseTestFile("smalldata/extdata/prostate.csv");
      train.replace(1, train.vec(1).toCategoricalVec()).remove();
      train.replace(3, train.vec(3).toCategoricalVec()).remove();
      DKV.put(train);
      Scope.track(train);

      ANOVAGLMModel.ANOVAGLMParameters params = new ANOVAGLMModel.ANOVAGLMParameters();
      params._family = gaussian;
      params._response_column = "VOL";
      params._train = train._key;
      params._solver = GLMModel.GLMParameters.Solver.IRLSM;
      params._weights_column = "AGE";
      params._offset_column = "GLEASON";
      params._ignored_columns = new String[]{"ID", "DPROS", "DCAPS", "PSA"};
      params._save_transformed_framekeys = true;

      ANOVAGLMModel anovaG = new ANOVAGLM(params).trainModel().get();
      Scope.track_generic(anovaG);
      Frame transformedFrame = DKV.getGet(anovaG._output._transformedColumnKey);
      Scope.track(transformedFrame);
      String[] compareCols = new String[]{params._weights_column, params._offset_column, params._response_column};
      TestUtil.assertIdenticalUpToRelTolerance(Scope.track(train.subframe(compareCols)), 
              Scope.track(transformedFrame.subframe(compareCols)), 0);
      System.out.println("Completed test testWeightOffset");
    } finally {
      Scope.exit();
    }
  }

  /***
   * The test is written to make sure dataset transformation with higher numbers of columns other than two works.
   */
  @Test
  public void testHighEnumColumns() {
    try {
      Scope.enter();
      Frame train  = parseTestFile("smalldata/anovaGlm/highEnumTest.csv");//C1,C2,C4:numeric, C3,C5,C6,C7,C8:enum
      Scope.track(train);
      Frame rTransformF = parseTestFile("smalldata/anovaGlm/highEnumRTransform.csv");
      Scope.track(rTransformF);
      
      ANOVAGLMModel.ANOVAGLMParameters params = new ANOVAGLMModel.ANOVAGLMParameters();
      params._family = gaussian;
      params._link = identity;
      params._response_column = "C5";
      params._highest_interaction_term = 4;
      params._train = train._key;
      params._solver = GLMModel.GLMParameters.Solver.IRLSM;
      params._save_transformed_framekeys = true;
      
      DataInfo dinfo = new DataInfo(train, null, 1, true,
              DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, false, true,
              new DataInfo.MeanImputer(), false, false, false, false, null);
      
      String[] _predNamesIndividual = new String[]{"C1","C2","C3","C4"};
      int _numberOfPredCombo = calculatePredComboNumber(4, 4);
      String[][] _predictComboNames = generatePredictorCombos(_predNamesIndividual, 4);  // generate all predictor and interactions
      String[][] _transformedColNames = new String[_numberOfPredCombo][];
      int[] _predictorColumnStart = new int[_numberOfPredCombo];
      int[] _degreeOfFreedom = new int[_numberOfPredCombo];
      generatePredictorNames(_predictComboNames, _transformedColNames, _predictorColumnStart, _degreeOfFreedom, dinfo);
      String[] _allTransformedColNames = flat(_transformedColNames);
      List<String> expandedColNames = new ArrayList<>(Arrays.asList(_allTransformedColNames));
      expandedColNames.add("response");
      GenerateTransformColumns gtc = new GenerateTransformColumns(_transformedColNames, params, dinfo,
              _predNamesIndividual.length, _predictComboNames);
      gtc.doAll(expandedColNames.size(), Vec.T_NUM, dinfo._adaptedFrame);
      Frame completeTransformedFrame = Scope.track(gtc.outputFrame(Key.make(), 
              expandedColNames.toArray(new String[0]), null));
      
      String[] noResponse = Arrays.copyOfRange(completeTransformedFrame.names(), 0, 
              completeTransformedFrame.names().length-1);
      String[] noIntercept = mapRcolNames(noResponse);
      TestUtil.assertIdenticalUpToRelTolerance(Scope.track(completeTransformedFrame.subframe(noResponse)), 
              Scope.track(rTransformF.subframe(noIntercept)), 0);
      System.out.println("Completed test testHighEnumColumns");
    } finally {
      Scope.exit();
    }
  }
  
  public static String[] mapRcolNames(String[] h2oNames) {
    int strLen = h2oNames.length;
    String[] rNames = new String[strLen];
    Map<String, String> h2oToR = new HashMap<>();
    h2oToR.put("C1_c0.l0", "C11");
    h2oToR.put("C1_c0.l1", "C12");
    h2oToR.put("C1_c0.l2", "C13");
    h2oToR.put("C2_c1.l0", "C21");
    h2oToR.put("C2_c1.l1", "C22");
    h2oToR.put("C2_c1.l2", "C23");
    h2oToR.put("C3_c2.l0", "C31");
    h2oToR.put("C3_c2.l1", "C32");
    h2oToR.put("C3_c2.l2", "C33");
    h2oToR.put("C4_c3.l0", "C41");
    h2oToR.put("C4_c3.l1", "C42");
    h2oToR.put("C4_c3.l2", "C43");
    
    int count = 0;
    for (String h2oName : h2oNames) {
      String[] splitStr = h2oName.split(":");
      if (splitStr.length == 1)
        rNames[count] = h2oToR.get(splitStr[0]);
      else if (splitStr.length == 2)
        rNames[count] = h2oToR.get(splitStr[0])+":"+h2oToR.get(splitStr[1]);
      else if (splitStr.length == 3)
        rNames[count] = h2oToR.get(splitStr[0])+":"+h2oToR.get(splitStr[1])+":"+h2oToR.get(splitStr[2]);
      else  // length 4
        rNames[count] = h2oToR.get(splitStr[0])+":"+h2oToR.get(splitStr[1])+":"+h2oToR.get(splitStr[2])+":"+
                h2oToR.get(splitStr[3]);
      count++;
    }
    return rNames;
  }

  /***
   * This test is used to compare R and H2O results.
   * C1 SS = 9360;
   * C2 SS = 14248
   * C1:C2 SS = 92800
   */
  @Test
  public void testPoisson() {
    try {
      Scope.enter();
      Frame train  = parseTestFile("smalldata/anovaGlm/poissonAnova.csv");
      Scope.track(train);
      ANOVAGLMModel.ANOVAGLMParameters params = new ANOVAGLMModel.ANOVAGLMParameters();
      params._family = poisson;
      params._link = log;
      params._response_column = "response";
      params._train = train._key;
      params._solver = GLMModel.GLMParameters.Solver.IRLSM;
      ANOVAGLMModel anovaG = new ANOVAGLM(params).trainModel().get();
      Scope.track_generic(anovaG);
      assertTrue(Math.abs(Math.round(getModelSummaryDoubleField(anovaG, "C1", "SS"))-9360)
              == 0);
      assertTrue(Math.abs(Math.round(getModelSummaryDoubleField(anovaG, "C2", "SS"))-14248)
              == 0);
      assertTrue(Math.abs(Math.round(getModelSummaryDoubleField(anovaG, "C1:C2", "SS"))
              -92800) == 0);
      System.out.println("Completed test testPoisson");
    } finally {
      Scope.exit();
    }
  }
  
  @Test 
  public void testANOVATableFrame() {
    try {
      Scope.enter();
      Frame train  = parseTestFile("smalldata/anovaGlm/poissonAnova.csv");
      Scope.track(train);
      ANOVAGLMModel.ANOVAGLMParameters params = new ANOVAGLMModel.ANOVAGLMParameters();
      params._family = poisson;
      params._link = log;
      params._response_column = "response";
      params._train = train._key;
      params._solver = GLMModel.GLMParameters.Solver.IRLSM;
      ANOVAGLMModel anovaG = new ANOVAGLM(params).trainModel().get();
      Frame anovaTable = anovaG.anovaTableFrame();
      Scope.track(anovaTable);
      Scope.track_generic(anovaG);
      // compare and make sure anova table frame and model summary contains the same contents, testing only numerics
      String[] rowHeaders = new String[]{"C1", "C2", "C1:C2"};
      String[] colHeaders = anovaTable.names();
      for (int rIndex=0; rIndex < rowHeaders.length; rIndex++) {
        for (int cIndex=0; cIndex < colHeaders.length; cIndex++) {
          if (colHeaders[cIndex].equals("DF"))
            assertTrue(getModelSummaryIntField(anovaG, rowHeaders[rIndex], colHeaders[cIndex])==anovaTable.vec(cIndex).at(rIndex));
          else if (anovaTable.vec(cIndex).isNumeric())
          assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, rowHeaders[rIndex], colHeaders[cIndex])-
                  anovaTable.vec(cIndex).at(rIndex))<1e-6);
        }
      }
    } finally {
      Scope.exit();
    }
  }

  /**
   * This test compare h2o result with R result for binomial family:
   * Analysis of Deviance Table (Type III tests)
   *
   * Response: CAPSULE
   *                LR Chisq Df Pr(>Chisq)    
   * RACE            224.449  1  < 2.2e-16 ***
   * AGE             103.565  1  < 2.2e-16 ***
   * DCAPS            27.427  1  1.632e-07 ***
   * RACE:AGE        179.645  1  < 2.2e-16 ***
   * RACE:DCAPS       23.554  1  1.214e-06 ***
   * AGE:DCAPS         0.992  1    0.31925    
   * RACE:AGE:DCAPS    8.612  1    0.00334 ** 
   */
  @Test
  public void testBinomial() {
    try {
      Scope.enter();
      Frame train  = parseTestFile("smalldata/prostate/prostate_complete.csv.zip");
      Scope.track(train);
      ANOVAGLMModel.ANOVAGLMParameters params = new ANOVAGLMModel.ANOVAGLMParameters();
      params._family = binomial;
      params._response_column = "CAPSULE";
      params._ignored_columns = new String[]{"C1", "ID", "PSA", "VOL", "DPROS", "GLEASON"};
      params._train = train._key;
      params._lambda = new double[]{0.0};
      ANOVAGLMModel anovaG = new ANOVAGLM(params).trainModel().get();
      Scope.track_generic(anovaG);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "AGE", "SS")-103.565) < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "RACE", "SS")-224.449) < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "DCAPS", "SS")-27.427) < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "AGE:RACE", "SS")-179.645)
              < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "AGE:DCAPS", "SS")-0.992)
              < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "RACE:DCAPS", "SS")-23.554)
              < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "AGE:RACE:DCAPS", "SS")
              -8.612) < 1e-2);
      System.out.println("Completed test testBinomial");
    } finally {
      Scope.exit();
    }
  }

  /**
   * Testing Tweedie family results with R:
   Analysis of Deviance Table (Type III tests)

   Response: CAPSULE
   LR Chisq Df Pr(>Chisq)    
   RACE                  75.80  1  < 2.2e-16 ***
   AGE                   58.53  1  2.002e-14 ***
   DCAPS                160.48  1  < 2.2e-16 ***
   VOL                 1492.63  1  < 2.2e-16 ***
   RACE:AGE              83.56  1  < 2.2e-16 ***
   RACE:DCAPS           281.29  1  < 2.2e-16 ***
   AGE:DCAPS            250.40  1  < 2.2e-16 ***
   RACE:VOL            1875.09  1  < 2.2e-16 ***
   AGE:VOL             1445.69  1  < 2.2e-16 ***
   DCAPS:VOL           1448.79  1  < 2.2e-16 ***
   RACE:AGE:DCAPS       321.98  1  < 2.2e-16 ***
   RACE:AGE:VOL        1820.74  1  < 2.2e-16 ***
   RACE:DCAPS:VOL      1793.20  1  < 2.2e-16 ***
   AGE:DCAPS:VOL       1442.18  1  < 2.2e-16 ***
   RACE:AGE:DCAPS:VOL  1770.32  1  < 2.2e-16 ***
   */
  @Test
  public void testTweedie() {
    try {
      Scope.enter();
      Frame train  = parseTestFile("smalldata/prostate/prostate_complete.csv.zip");
      Scope.track(train);
      ANOVAGLMModel.ANOVAGLMParameters params = new ANOVAGLMModel.ANOVAGLMParameters();
      params._family = tweedie;
      params._link = GLMModel.GLMParameters.Link.tweedie;
      params._response_column = "CAPSULE";
      params._ignored_columns = new String[]{"C1", "ID", "PSA", "DPROS", "GLEASON"};
      params._train = train._key;
      params._alpha = new double[]{0.5};
      params._lambda = new double[]{0.0};
      ANOVAGLMModel anovaG = new ANOVAGLM(params).trainModel().get();
      Scope.track_generic(anovaG);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "AGE", "F")-58.53) < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "RACE", "F")-75.80) < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "DCAPS", "F")-160.48) < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "VOL", "F")-1492.63) < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "AGE:RACE", "F")-83.56)
              < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "AGE:DCAPS", "F")-250.40) 
              < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "AGE:VOL", "F")-1445.69)
              < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "RACE:DCAPS", "F")-281.29)
              < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "RACE:VOL", "F")-1875.09)
              < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "DCAPS:VOL", "F")-1448.79)
              < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "AGE:RACE:DCAPS", "F")-321.98)
              < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "AGE:RACE:VOL", "F")-1820.74)
              < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "RACE:DCAPS:VOL", "F")-
              1793.20) < 1e-2);
      assertTrue(Math.abs(getModelSummaryDoubleField(anovaG, "AGE:RACE:DCAPS:VOL", "F")-
              1770.32) < 1e-2);
      System.out.println("Completed test testTweedie");
    } finally {
      Scope.exit();
    }
  }

  /**
   * Test the generation of six numerical columns.
   */
  
  @Test
  public void testPredCombos() {
    String[] predColumns = new String[]{"C1", "C2", "C3", "C4", "C5", "C6"};
    List<String[]> predCombo = new ArrayList<>();
    manualCalPredCombo(predColumns, predCombo);
    String[][] predComboNamesManual = predCombo.toArray(new String[0][0]);
    String[][] calPredCombo = generatePredictorCombos(predColumns, predColumns.length);
    assertEqualDoubleStringArrays(predComboNamesManual, calPredCombo);
    System.out.println("Completed test testPredCombos");
  }

  /**
   * Test the generation of predictor names after data transformation for two columns
   */
  @Test
  public void testPredColNames2() {
    try {
      Scope.enter();
      Frame testFrame = Scope.track(parseTestFile("smalldata/anovaGlm/gaussian8Cols.csv"));
      DataInfo dinfo = new DataInfo(testFrame, null, 1, true,
              DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, false, true, 
              new DataInfo.MeanImputer(), false, false, false, false, null);
      System.out.println("Checking interaction between two numerical columns");
      String[] predNamesIndividual = new String[]{"C1", "C2"};
      String[][] manualPredCombo = new String[][] {{"C1"}, {"C2"}, {"C1:C2"}};
      assertCorrectPredColNamesGen(predNamesIndividual, dinfo, 4, manualPredCombo, 2);

      System.out.println("Checking interaction between two enum columns");
      predNamesIndividual = new String[]{"C3", "C5"};
      String[] domain1 = dinfo._adaptedFrame.vec(predNamesIndividual[0]).domain();
      String[] domain2 = dinfo._adaptedFrame.vec(predNamesIndividual[1]).domain();
      String[][] manualComboNames = new String[3][];
      manualComboNames[0] = new String[domain1.length-1];
      for (int index = 0; index < domain1.length-1; index++) 
        manualComboNames[0][index] = predNamesIndividual[0]+"_"+domain1[index];
      manualComboNames[1] = new String[domain2.length-1];
      for (int index = 0; index < domain1.length-1; index++)
        manualComboNames[1][index] = predNamesIndividual[1]+"_"+domain2[index];
      int count = 0;
      manualComboNames[2] = new String[(domain1.length-1)*(domain2.length-1)];
      for (int index = 0; index < domain1.length-1; index++) {
        for (int index2 = 0; index2 < domain2.length-1; index2++) {
          manualComboNames[2][count++] = manualComboNames[0][index]+":"+manualComboNames[1][index2];
        }
      }
      assertCorrectPredColNamesGen(predNamesIndividual, dinfo, 4, manualComboNames, 2);
      System.out.println("Completed test testPredColNames2");
    } finally {
      Scope.exit();
    }
  }

  /**
   * Test the correct generation of predictor names for multiple columns.  I want to make sure that the predictors,
   * interaction of all columns are correctly generated.
   */
  @Test
  public void testPredColNames3() {
    try {
      Scope.enter();
      Frame testFrame = Scope.track(parseTestFile("smalldata/anovaGlm/gaussian8Cols.csv"));
      DataInfo dinfo = new DataInfo(testFrame, null, 1, true,
              DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, false, true,
              new DataInfo.MeanImputer(), false, false, false, false, null);
      System.out.println("Checking interaction between three numerical columns");
      String[] predNamesIndividual = new String[]{"C1", "C2", "C4"};
      String[][] manualPredCombo = new String[][] {{"C1"}, {"C2"}, {"C4"}, {"C1:C2"}, {"C1:C4"}, {"C2:C4"}, 
              {"C1:C2:C4"}};
      int numOfModel = 8;
      assertCorrectPredColNamesGen(predNamesIndividual, dinfo, numOfModel, manualPredCombo, 3);

      System.out.println("Checking interaction between three enum columns");
      predNamesIndividual = new String[]{"C3", "C5", "C6"};
      String[] domain1 = dinfo._adaptedFrame.vec(predNamesIndividual[0]).domain();
      String[] domain2 = dinfo._adaptedFrame.vec(predNamesIndividual[1]).domain();
      String[] domain3 = dinfo._adaptedFrame.vec(predNamesIndividual[2]).domain();
      String[][] manualComboNames = new String[7][];
      manualComboNames[0] = new String[domain1.length-1];
      manualComboNames[1] = new String[domain2.length-1];
      manualComboNames[2] = new String[domain3.length-1];
      manualComboNames[3] = new String[manualComboNames[0].length*manualComboNames[1].length];
      manualComboNames[4] = new String[manualComboNames[0].length*manualComboNames[2].length];
      manualComboNames[5] = new String[manualComboNames[1].length*manualComboNames[2].length];
      manualComboNames[6] = new String[manualComboNames[0].length*manualComboNames[1].length*manualComboNames[2].length];
      for (int index = 0; index < domain1.length-1; index++)
        manualComboNames[0][index] = predNamesIndividual[0]+"_"+domain1[index];
      for (int index = 0; index < domain2.length-1; index++)
        manualComboNames[1][index] = predNamesIndividual[1]+"_"+domain2[index];
      for (int index = 0; index < domain3.length-1; index++)
        manualComboNames[2][index] = predNamesIndividual[2]+"_"+domain3[index];
      int count = 0;
      for (int index = 0; index < domain1.length-1; index++) {
        for (int index2 = 0; index2 < domain2.length-1; index2++) {
          manualComboNames[3][count++] = manualComboNames[0][index]+":"+manualComboNames[1][index2];
        }
      }
      count = 0;
      for (int index = 0; index < domain1.length-1; index++) {
        for (int index2 = 0; index2 < domain3.length-1; index2++) {
          manualComboNames[4][count++] = manualComboNames[0][index]+":"+manualComboNames[2][index2];
        }
      }
      count = 0;
      for (int index = 0; index < domain2.length-1; index++) {
        for (int index2 = 0; index2 < domain3.length-1; index2++) {
          manualComboNames[5][count++] = manualComboNames[1][index]+":"+manualComboNames[2][index2];
        }
      }
      count = 0;
      for (int index = 0; index < domain1.length-1; index++) {
        for (int index2 = 0; index2 < domain2.length-1; index2++) {
          for (int index3 = 0; index3 < domain3.length-1; index3++)
          manualComboNames[6][count++] = manualComboNames[0][index]+":"+manualComboNames[1][index2]+":"+
                  manualComboNames[2][index3];
        }
      }
      assertCorrectPredColNamesGen(predNamesIndividual, dinfo, numOfModel, manualComboNames, 3);
      System.out.println("Completed test testPredColNames3");
    } finally {
      Scope.exit();
    }
  }
  
  public static void assertCorrectPredColNamesGen(String[] predNamesInd, DataInfo dinfo, int numOfModel, 
                                                  String[][] manualPredCombo, int maxPredInt) {
    String[][] transformedColNames = new String[numOfModel-1][];
    String[][] predictComboNames = generatePredictorCombos(predNamesInd, maxPredInt);  // generate all predictor and interactions
    generatePredictorNames(predictComboNames, transformedColNames, new int[numOfModel], new int[numOfModel], dinfo);
    assertEqualDoubleStringArrays(transformedColNames, manualPredCombo);
  }

  
  public static void assertEqualDoubleStringArrays(String[][] predComboManual, String[][] calComboPred) {
    int numArray = predComboManual.length;
    assertTrue(numArray == calComboPred.length);  // combo length has equal length
    for (int index=0; index < numArray; index++) {
      int eleLen = predComboManual[index].length;
      assertTrue(eleLen == calComboPred[index].length); // string has equal length
      for (int index2 = 0; index2 < eleLen; index2++) 
        assertTrue(predComboManual[index][index2].equals(calComboPred[index][index2]));
    }
  }
 
  public static int manualCalPredCombo(String[] predColumns, List<String[]> predCombo) {
    int predNum = predColumns.length;
    for (int index = 0; index < predNum; index++)
      predCombo.add(new String[]{predColumns[index]});
    int totCombo = 6 + 15 + 20 + 15 + 6 + 1;  // calculated manually
    // manually adding combo of 2 columns
    int[] predInd = new int[2];
    for (int index1 = 0; index1 < predNum; index1++) {
      predInd[0] = index1;
      for (int index2 = index1 + 1; index2 < predNum; index2++) {
        predInd[1] = index2;
        predCombo.add(new String[]{predColumns[predInd[0]], predColumns[predInd[1]]});
      }
    }
    // manually adding combo of 3 columns
    predInd = new int[3];
    for (int index1 = 0; index1 < predNum; index1++) {
      predInd[0] = index1;
      for (int index2 = index1 + 1; index2 < predNum; index2++) {
        predInd[1] = index2;
        for (int index3 = index2 + 1; index3 < predNum; index3++) {
          predInd[2] = index3;
          predCombo.add(new String[]{predColumns[predInd[0]], predColumns[predInd[1]], predColumns[predInd[2]]});
        }
      }
    }
    // manually adding combo of 4 columns
    predInd = new int[4];
    for (int index1 = 0; index1 < predNum; index1++) {
      predInd[0] = index1;
      for (int index2 = index1 + 1; index2 < predNum; index2++) {
        predInd[1] = index2;
        for (int index3 = index2 + 1; index3 < predNum; index3++) {
          predInd[2] = index3;
          for (int index4 = index3 + 1; index4 < predNum; index4++) {
            predInd[3] = index4;
            predCombo.add(new String[]{predColumns[predInd[0]], predColumns[predInd[1]], predColumns[predInd[2]],
                    predColumns[predInd[3]]});
          }
        }
      }
    }
    // manually adding combo of 5 columns
    predInd = new int[5];
    for (int index1 = 0; index1 < predNum; index1++) {
      predInd[0] = index1;
      for (int index2 = index1 + 1; index2 < predNum; index2++) {
        predInd[1] = index2;
        for (int index3 = index2 + 1; index3 < predNum; index3++) {
          predInd[2] = index3;
          for (int index4 = index3 + 1; index4 < predNum; index4++) {
            predInd[3] = index4;
            for (int index5 = index4+1; index5 < predNum; index5++) {
              predInd[4] = index5;
              predCombo.add(new String[]{predColumns[predInd[0]], predColumns[predInd[1]], predColumns[predInd[2]],
                      predColumns[predInd[3]], predColumns[predInd[4]]});
            }
          }
        }
      }
    }
    // manually adding combo of 6 columns
    predCombo.add(predColumns);
    return totCombo;
  }
}
