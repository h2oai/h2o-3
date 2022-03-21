package hex.Infogram;

import hex.ModelBuilder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.ArrayList;
import java.util.List;

import static hex.DMatrix.transpose;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class InfogramPipingTest extends TestUtil {
  public static final double TOLERANCE = 1e-6;

  // Deep example 9
  @Test
  public void testIris() {
    try {
      Scope.enter();
      double[] deepRel = new double[]{ 0.009010006, 0.011170417, 0.755170945, 1.000000000};
      double[] deepCMI = new double[]{0.1038524, 0.7135458, 0.5745915, 1.0000000};
      Frame trainF = parseTestFile("smalldata/admissibleml_test/irisROriginal.csv");
      Scope.track(trainF);
      InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
      params._response_column = "Species";
      params._train = trainF._key;
      params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
      params._seed = 12345;

      InfogramModel infogramModel = new Infogram(params).trainModel().get();
      Scope.track_generic(infogramModel);
      assertEqualCMIRel(deepRel, deepCMI, infogramModel._output, TOLERANCE);
      Frame infogramFr = DKV.getGet(infogramModel._output._admissible_score_key);  // info gram info in an H2OFrame
      Scope.track(infogramFr);
      assertEquals(infogramFr.numRows(), deepCMI.length);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testIrisWarn() {
    try {
      Scope.enter();
      Frame trainF = parseTestFile("smalldata/admissibleml_test/irisROriginal.csv");
      Scope.track(trainF);
      InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
      params._response_column = "Species";
      params._train = trainF._key;
      params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
      params._seed = 12345;
      params._top_n_features = trainF.numCols()-1;
      params._safety_index_threshold = 0.2;
      params._relevance_index_threshold = 0.2;
      Infogram infogram = new Infogram(params);
      InfogramModel infogramModel = infogram.trainModel().get();
      Scope.track_generic(infogramModel);
      assertWarningMessages(infogram._messages, 2);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testPersonalLoanWarn() {
    try {
      Scope.enter();
      Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/Bank_Personal_Loan_Modelling.csv"));
      trainF.replace(9, trainF.vec("Personal Loan").toCategoricalVec()).remove();
      DKV.put(trainF);
      InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
      params._response_column = "Personal Loan";
      params._train = trainF._key;
      params._protected_columns = new String[]{"Age","ZIP Code"};
      params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
      params._ignored_columns = new String[]{"ID"};
      params._top_n_features = trainF.numCols()-3;
      params._seed = 12345;
      params._net_information_threshold = 0.2;
      params._total_information_threshold = 0.2;
      Infogram infogram = new Infogram(params);
      InfogramModel infogramModel = infogram.trainModel().get();
      Scope.track_generic(infogramModel);
      assertWarningMessages(infogram._messages, 2);
    } finally {
      Scope.exit();
    }
  }

  public static void assertWarningMessages(ModelBuilder.ValidationMessage[] messages, int expectedWarn) {
    List<String> warnMessage = new ArrayList<>();
    for (ModelBuilder.ValidationMessage oneMessage : messages) {
      if (oneMessage.log_level()==2) { // for warning
        warnMessage.add(oneMessage.toString());
      }
    }
    assertTrue(warnMessage.size()==expectedWarn);
  }

  // Deep example 5
  @Test
  public void testGermanData() {
    try {
      Scope.enter();
      double[] deepRel = new double[]{1.00000000, 0.58302027, 0.43431236, 0.66177924, 0.53677082, 0.25084764,
              0.34379833, 0.13251726, 0.11473028, 0.09548423, 0.20398740, 0.16432640, 0.06875276, 0.04870468,
              0.12573930, 0.01382682, 0.04496173, 0.01273963};
      double[] deepCMI = new double[]{0.84946975, 0.73020930, 0.58553936, 0.75780528, 1.00000000, 0.38461582,
              0.57575695, 0.30663930, 0.07604779, 0.19979514, 0.42293369, 0.20628365, 0.25316918, 0.15096705,
              0.24501686, 0.11296778, 0.13068605, 0.03841617};
      Frame trainF = parseTestFile("smalldata/admissibleml_test/german_credit.csv");
      InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
      params._response_column = "BAD";
      trainF.replace(trainF.numCols()-1, trainF.vec(params._response_column).toCategoricalVec()).remove();
      DKV.put(trainF);
      params._train = trainF._key;
      params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
      params._protected_columns = new String[]{"status_gender", "age"};
      params._top_n_features = 50;
      Scope.track(trainF);

      InfogramModel infogramModel = new Infogram(params).trainModel().get();
      Scope.track_generic(infogramModel);
      assertEqualCMIRel(deepRel, deepCMI, infogramModel._output, TOLERANCE);
      Frame infogramFr = DKV.getGet(infogramModel._output._admissible_score_key);  // info gram info in an H2OFrame
      Scope.track(infogramFr);
      assertEquals(infogramFr.numRows(), deepCMI.length);
    } finally {
      Scope.exit();
    }
  }

  // Deep example 6
  @Test
  public void testUCICredit() {
    try {
      Scope.enter();
      double[] deepCMI = new double[]{0.25225589, 0.04838205, 0.02515363, 1.00000000, 0.65011528, 0.49968050, 
              0.44469423, 0.41195756, 0.35604507, 0.14960576, 0.11973009, 0.10654662, 0.12172179, 0.12809776, 
              0.11255243, 0.28748429, 0.24735238, 0.23491307, 0.19843329, 0.17768404, 0.17737053};
      double[] deepRel = new double[]{0.07893684, 0.01800647, 0.01098464, 1.00000000, 0.17214091, 0.05758034, 
              0.03805165, 0.03097822, 0.03125514, 0.06620615, 0.02368234, 0.01071032, 0.01051331, 0.02224472, 
              0.01574407, 0.02323453, 0.01780084, 0.01759464, 0.01063546, 0.01165965, 0.01447185};
      Frame trainF = parseTestFile("smalldata/admissibleml_test/taiwan_credit_card_uci.csv");
      int ncol = trainF.numCols();
      trainF.replace(ncol-1, trainF.vec(ncol-1).toCategoricalVec()).remove();
      InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
      params._response_column = "default payment next month";
      Scope.track(trainF.remove(0));
      DKV.put(trainF);
      Scope.track(trainF);
      params._train = trainF._key;
      params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
      params._seed = 12345;
      params._protected_columns = new String[]{"SEX", "AGE"};
      InfogramModel infogramModel = new Infogram(params).trainModel().get();
      Scope.track_generic(infogramModel);
      assertEqualCMIRel(deepRel, deepCMI, infogramModel._output, TOLERANCE);
    } finally {
      Scope.exit();
    }
  }

  // Deep example 10
  @Test
  public void testProstateWide() {
    try {
      Scope.enter();
      double[] deepCMI = new double[]{1.000000000, 0.416069511, 0.685096529, 0.507368744, 0.000000000, 0.575576639,
              0.000000000, 0.000000000, 0.271674757, 0.248421424, 0.474729852, 0.000000000, 0.000000000, 0.000000000,
              0.018534010, 0.036368120, 0.000000000, 0.184383526, 0.316296264, 0.239501471, 0.000000000, 0.000000000,
              0.058800272, 0.000000000, 0.073573180, 0.396318927, 0.267592247, 0.000000000, 0.000000000, 0.000000000,
              0.000000000, 0.041711020, 0.000000000, 0.000000000, 0.000000000, 0.000000000, 0.000000000, 0.002055904,
              0.000000000, 0.000000000, 0.000000000, 0.090010963, 0.048595528, 0.080272375, 0.004002294, 0.000000000,
              0.061638596, 0.000000000, 0.087287940, 0.062288766};
      double[] deepRel = new double[]{1.000000e+00, 6.486737e-01, 4.947306e-01, 3.642898e-01, 1.932296e-01,
              1.748593e-01, 1.067082e-01, 9.285556e-02, 9.007357e-02, 6.817226e-02, 5.514745e-02, 5.039264e-02,
              4.741128e-02, 4.188043e-02, 4.024860e-02, 3.019085e-02, 2.663353e-02, 2.570307e-02, 2.379470e-02,
              2.220692e-02, 2.044017e-02, 1.524100e-02, 1.663565e-02, 1.111248e-02, 7.832062e-03, 6.588439e-03,
              5.934081e-03, 3.756785e-03, 1.789985e-03, 2.941161e-03, 4.941466e-05, 2.269027e-03, 2.224223e-03,
              1.902141e-03, 2.644488e-06, 8.339014e-04, 1.352506e-03, 1.371541e-03, 1.290422e-03, 7.044505e-04,
              1.499066e-03, 2.239000e-04, 6.416308e-04, 7.102487e-04, 1.212104e-03, 5.354030e-04, 5.900479e-04,
              6.537474e-04, 5.808857e-04, 3.335511e-04};
      Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/prostmat.csv"));
      Frame transposeF = new Frame(transpose(trainF));
      double[] y = new double[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
              0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,
              1,1,1,1,1,1,1};
      Vec responseVec = Vec.makeVec(y, Vec.newKey());
      Scope.track(responseVec);
      transposeF.add("y",responseVec);
      transposeF.replace(transposeF.numCols()-1, transposeF.vec("y").toCategoricalVec()).remove();
      Scope.track(transposeF);
      DKV.put(transposeF);
      InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
      params._response_column = "y";
      params._train = transposeF._key;
      params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
      params._top_n_features = 50;
      params._seed = 12345;

      InfogramModel infogramModel = new Infogram(params).trainModel().get();
      Scope.track_generic(infogramModel);
      assertEqualCMIRel( deepRel, deepCMI, infogramModel._output, TOLERANCE);
    } finally {
      Scope.exit();
    }
  }

  // Deep example 12
  @Test
  public void testCompasScores2years() {
    try {
      Scope.enter();
      double[] deepCMI = new double[]{0.008011609, 0.138541125, 0.013504090,0.016311791, 0.181746765, 0.046041515,
              0.017965106, 0.138541125, 0.107449216, 0.082824953, 0.050455340, 0.181746765, 0.050691145, 1.000000000,
              0.858833177};
      double[] deepRel = new double[]{0.0013904297, 0.0167956478, 0.0022520089, 0.0014052132, 0.0104887361,
              0.0041130581, 0.0012933420, 0.0000000000, 0.0018857971, 0.0040163313, 0.0002574921, 0.0000000000,
              0.0145265544, 1.0000000000, 0.2223278895};
      Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/compas_full.csv"));
      trainF.replace(trainF.numCols()-1, trainF.vec("two_year_recid").toCategoricalVec()).remove();
      DKV.put(trainF);
      InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
      params._response_column = "two_year_recid";
      params._train = trainF._key;
      params._protected_columns = new String[]{"sex","age","race"};
      params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
      params._ignored_columns = new String[]{"id"};
      params._top_n_features = 50;
      params._seed = 12345;

      InfogramModel infogramModel = new Infogram(params).trainModel().get();
      Scope.track_generic(infogramModel);
      assertEqualCMIRel(deepRel, deepCMI, infogramModel._output, TOLERANCE);
    } finally {
      Scope.exit();
    }
  }

  // Deep new example 1, Deep's answer is incorrect.  He built main model multiple times and with different 
  // predictor orders.  Disable compare for now until his code is fixed.
  @Ignore
  @Test
  public void testBreastCancer() {
    try {
      Scope.enter();
      String[] colNames = new String[]{"diagnosis", "radius_mean", "texture_mean", "perimeter_mean", "area_mean",
              "smoothness_mean", "compactness_mean", "concavity_mean", "concave_points_mean", "symmetry_mean",
              "fractal_dimension_mean", "radius_se", "texture_se", "perimeter_se", "area_se", "smoothness_se",
              "compactness_se", "concavity_se", "concave_points_se", "symmetry_se", "fractal_dimension_se",
              "radius_worst", "texture_worst", "perimeter_worst", "area_worst", "smoothness_worst", "compactness_worst",
              "concavity_worst", "concave_points_worst", "symmetry_worst", "fractal_dimension_worst"};
      double[] deepCMI = new double[]{0.00000000, 0.31823883, 0.52769230, 0.00000000, 0.00000000, 0.00000000,
              0.01183309, 0.67430653, 0.00000000, 0.00000000, 0.45443221, 0.00000000, 0.24561013, 0.87720587,
              0.31939378, 0.19370515, 0.00000000, 0.16463918, 0.00000000, 0.00000000, 0.44830772, 1.00000000,
              0.00000000, 0.00000000, 0.62478098, 0.00000000, 0.00000000, 0.00000000, 0.00000000, 0.64466111};
      double[] deepRel = new double[]{0.0040477989, 0.0974455315, 0.0086303713, 0.0041002103, 0.0037914745,
              0.0036801151, 0.0257819346, 0.2808010416, 0.0005372569, 0.0036280018, 0.0032444598, 0.0002943119,
              0.0026430897, 0.0262074332, 0.0033317064, 0.0068812603, 0.0006185385, 0.0082121491, 0.0014562177,
              0.0081786997, 1.0000000000, 0.0894895310, 0.6187801784, 0.3302352775, 0.0021346433, 0.0016077771,
              0.0260198502, 0.3404628948, 0.0041384517, 0.0019399743};
      Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/wdbc_changed.csv"));
      trainF.setNames(colNames);
      trainF.replace(0, trainF.vec("diagnosis").toCategoricalVec()).remove();
      DKV.put(trainF);
      InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
      params._response_column = "diagnosis";
      params._train = trainF._key;
      params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
      params._top_n_features = 50;
      params._seed = 12345;

      InfogramModel infogramModel = new Infogram(params).trainModel().get();
      Scope.track_generic(infogramModel);
      assertEqualCMIRel(deepRel, deepCMI, infogramModel._output, TOLERANCE);
    } finally {
      Scope.exit();
    }
  }

  // Deep new example 2
  @Test
  public void testPersonalLoan() {
    try {
      Scope.enter();
      double[] deepCMI = new double[]{0.018913757, 1.000000000, 0.047752382, 0.646021834, 0.087924437, 0.126791480,
              0.012771638, 0.203651610, 0.007879079, 0.014035872};
      double[] deepRel = new double[]{0.035661238, 0.796097276, 0.393246039, 0.144327761, 1.000000000, 0.002905239,
              0.002187174, 0.046872455, 0.004976263, 0.004307822};
      Frame trainF = Scope.track(parseTestFile("smalldata/admissibleml_test/Bank_Personal_Loan_Modelling.csv"));
      trainF.replace(9, trainF.vec("Personal Loan").toCategoricalVec()).remove();
      DKV.put(trainF);
      InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
      params._response_column = "Personal Loan";
      params._train = trainF._key;
      params._protected_columns = new String[]{"Age","ZIP Code"};
      params._algorithm = InfogramModel.InfogramParameters.Algorithm.gbm;
      params._ignored_columns = new String[]{"ID"};
      params._top_n_features = 50;
      params._seed = 12345;

      InfogramModel infogramModel = new Infogram(params).trainModel().get();
      Scope.track_generic(infogramModel);
      assertEqualCMIRel(deepRel, deepCMI, infogramModel._output, TOLERANCE);
    } finally {
      Scope.exit();
    }
  }

  public static void assertEqualCMIRel(double[] deepRel, double[] deepCMI, InfogramModel.InfogramModelOutput output,
                                       double tolerance) {
    java.util.Arrays.sort(deepRel);
    java.util.Arrays.sort(deepCMI);
    double[] modelRelevance = output._relevance;
    java.util.Arrays.sort(modelRelevance);
    double[] modelCMI = output._cmi;
    java.util.Arrays.sort(modelCMI);
    int numPred = deepRel.length;
    for (int index = 0; index < numPred; index++) {
        assert Math.abs(modelRelevance[index] - deepRel[index]) < tolerance : "model relevance " +
                modelRelevance[index] + " and deep relevance " + deepRel[index] + " differs more than " + tolerance;
        // compare CMI with deep result
        assert Math.abs(modelCMI[index] - deepCMI[index]) < tolerance : "model CMI " +
                modelCMI[index] + " and deep CMI " + deepCMI[index] + " differs more than " + tolerance;

    }
  }

  public static void assertEqualCMIRel(List<String> predictorNames, double[] deepRel, double[] deepCMI,
                                       InfogramModel.InfogramModelOutput output, double tolerance, List<Integer> excludeList) {
    int numPred = predictorNames.size();
    String[] predictorWNames = output._all_predictor_names;
    double[] modelRelevance = output._relevance;
    double[] modelCMI = output._cmi;
    for (int index = 0; index < numPred; index++) {
      if (!excludeList.contains(index)) {
        // compare relevance with deep result
        String predName = predictorWNames[index];
        int predIndex = predictorNames.indexOf(predName);
        assert Math.abs(modelRelevance[index] - deepRel[predIndex]) < tolerance : "model relevance " +
                modelRelevance[index] + " and deep relevance " + deepRel[predIndex] + " for predictor "
                + predName + " differs more than " + tolerance;
        // compare CMI with deep result
        assert Math.abs(modelCMI[index] - deepCMI[predIndex]) < tolerance : "model CMI " +
                modelCMI[index] + " and deep CMI " + deepCMI[predIndex] + " for predictor "
                + predName + " differs more than " + tolerance;
      }
    }
  }

  @Test
  public void testInfogramSupportAllAlgos() {
      try {
        Scope.enter();
        Frame trainF = parseTestFile("smalldata/admissibleml_test/irisROriginal.csv");
        Scope.track(trainF);
        InfogramModel.InfogramParameters params = new InfogramModel.InfogramParameters();
        params._response_column = "Species";
        params._train = trainF._key;
        params._seed = 12345;

        for (InfogramModel.InfogramParameters.Algorithm algo : InfogramModel.InfogramParameters.Algorithm.values()) {
          params._algorithm = algo;
          InfogramModel infogramModel = new Infogram(params).trainModel().get();
          assertNotNull(infogramModel);
          Scope.track_generic(infogramModel);
        }

      } finally {
        Scope.exit();
      }
    }
}

