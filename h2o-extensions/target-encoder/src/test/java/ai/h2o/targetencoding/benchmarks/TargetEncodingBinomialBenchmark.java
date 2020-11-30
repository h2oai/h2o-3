package ai.h2o.targetencoding.benchmarks;

import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderModel;
import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import hex.*;
import hex.genmodel.utils.DistributionFamily;
import hex.splitframe.ShuffleSplitFrame;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static ai.h2o.targetencoding.TargetEncoderHelper.addKFoldColumn;
import static water.util.ArrayUtils.append;

@Ignore("Ignoring benchmark tests")
@RunWith(Parameterized.class)
public class TargetEncodingBinomialBenchmark extends TestUtil {
  
  @Parameterized.Parameters
  public static Object[] data() {
    return new Object[] { "airlines", "titanic"};
  }
  
  @Parameterized.Parameter
  public String dataset;

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }
  
  private String target;
  private String foldColumn;
  private String[] teColumns;
  private String[] ignoredColumns;
  
  private Map<String, Frame> prepareDataset(String name, String keySuffix, boolean useFolding, int seed) {
    Map<String, Frame> datasets = new HashMap<>();
    Frame fr, test;
    switch (name) {
      case "airlines":
        fr = parse_test_file(Key.make("airlines_train"), "smalldata/airlines/AirlinesTrain.csv.zip");
        test = parse_test_file(Key.make("airlines_test"), "smalldata/airlines/AirlinesTest.csv.zip");
        target = "IsDepDelayed";
        foldColumn = "fold";
        teColumns = new String[]{"Origin", "Dest"};
        ignoredColumns = new String[] {"IsDepDelayed_REC"};
        break;
      case "titanic":
        fr = parse_test_file(Key.make("airlines_train"), "smalldata/titanic/titanic_expanded.csv");
        test = null;
        target = "survived";
        foldColumn = "fold";
        teColumns = new String[]{"cabin", "home.dest", "embarked"};
        ignoredColumns = new String[] {"name", "ticket", "boat", "body"};
        break;
      default:
        throw new IllegalArgumentException("name should be one of ['airlines', 'titanic']");
    }
    boolean testInSplits = test == null;
    
    double[] splits = useFolding
            ? testInSplits ? new double[] {0.8, 0.1} : new double[] {0.9} 
            : testInSplits ? new double[] {0.5, 0.1, 0.1} : new double[]{0.6, 0.1};
    String[] names = useFolding 
            ? testInSplits ? new String[]{"train", "valid", "test"} : new String[] {"train", "valid"} 
            : testInSplits ? new String[]{"train", "valid", "test", "holdout"} : new String[] {"train", "valid", "holdout"};
    Key[] keys = Stream.of(names).map(n -> Key.make(n+keySuffix)).toArray(Key[]::new);
    Frame[] frSplits = ShuffleSplitFrame.shuffleSplitFrame(fr, keys, splits, seed);
    Frame train = frSplits[0];
    Frame valid = frSplits[1];
    test = testInSplits ? frSplits[2] : test;
    Frame holdout = useFolding 
            ? null 
            : testInSplits ? frSplits[3] : frSplits[2];

    datasets.put("train", train);
    datasets.put("valid", valid);
    datasets.put("test", test);
    datasets.put("holdout", holdout);
    return datasets;
  }

  @Test
  public void with_KFold_strategy() {
    System.out.println("Using KFold strategy on "+dataset+" dataset.");
    try {
      Scope.enter();
      int seed = 42;
      Map<String, Frame> datasets = prepareDataset(dataset, "_kfold", true, seed);
      Frame train = datasets.get("train");
      Frame valid = datasets.get("valid");
      Frame test = datasets.get("test");
      Scope.track(train, valid, test);

      addKFoldColumn(train, foldColumn, 5, seed);
      
      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train= train._key;
      teParams._response_column = target;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
      teParams._fold_column = foldColumn;
      teParams._ignored_columns = ignoredColumns(train, append(teColumns, foldColumn, target));
      teParams._seed = seed;
      
      TargetEncoder te = new TargetEncoder(teParams);
      long start = System.currentTimeMillis();
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      long trainingStep = System.currentTimeMillis();
      
      Frame trainEncoded = teModel.score(train); Scope.track(trainEncoded);
      Frame validEncoded = teModel.score(valid); Scope.track(validEncoded);
      Frame testEncoded = teModel.score(test); Scope.track(testEncoded);
      long encodingStep = System.currentTimeMillis();

      printOutColumnsMetadata(testEncoded);

      System.out.println("TE training took (ms): " + (trainingStep - start));
      System.out.println("TE encoding took (ms): " + (encodingStep - trainingStep));
      System.out.println("TE total took (ms): " + (encodingStep - start));

      GBMModel.GBMParameters parms = prepareGBMParameters(seed);
      parms._response_column = "IsDepDelayed";
      parms._train = trainEncoded._key;
      parms._valid = validEncoded._key;
      parms._ignored_columns = append(append(ignoredColumns, teColumns), foldColumn);

      GBM gbm = new GBM(parms);
      long startGBMStep = System.currentTimeMillis();
      GBMModel gbmModel = gbm.trainModel().get();
      Scope.track_generic(gbmModel);
      long endGBMStep = System.currentTimeMillis();
      System.out.println("GBM training (kfoldTE) took: " + (endGBMStep - startGBMStep));

      double auc_with_te = auc_score(gbmModel, testEncoded);
      double auc_without_te = trainDefaultGBM(target, seed);

      System.out.println("AUC with encoding:" + auc_with_te);
      System.out.println("AUC without encoding:" + auc_without_te);

      Assert.assertTrue(auc_without_te < auc_with_te);
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void with_LeaveOneOut_strategy() {
    System.out.println("Using LeaveOneOut strategy on "+dataset+" dataset.");
    try {
      Scope.enter();
      int seed = 42;
      Map<String, Frame> datasets = prepareDataset(dataset, "_kfold", true, seed);
      Frame train = datasets.get("train");
      Frame valid = datasets.get("valid");
      Frame test = datasets.get("test");
      Scope.track(train, valid, test);

      addKFoldColumn(train, foldColumn, 5, seed);

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train= train._key;
      teParams._response_column = target;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.LeaveOneOut;
      teParams._fold_column = foldColumn;
      teParams._ignored_columns = ignoredColumns(train, append(teColumns, foldColumn, target));
      teParams._seed = seed;

      TargetEncoder te = new TargetEncoder(teParams);
      long start = System.currentTimeMillis();
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      long trainingStep = System.currentTimeMillis();

      Frame trainEncoded = teModel.score(train); Scope.track(trainEncoded);
      Frame validEncoded = teModel.score(valid); Scope.track(validEncoded);
      Frame testEncoded = teModel.score(test); Scope.track(testEncoded);
      long encodingStep = System.currentTimeMillis();

      printOutColumnsMetadata(testEncoded);

      System.out.println("TE training took (ms): " + (trainingStep - start));
      System.out.println("TE encoding took (ms): " + (encodingStep - trainingStep));
      System.out.println("TE total took (ms): " + (encodingStep - start));

      GBMModel.GBMParameters parms = prepareGBMParameters(seed);
      parms._response_column = "IsDepDelayed";
      parms._train = trainEncoded._key;
      parms._valid = validEncoded._key;
      parms._ignored_columns = append(append(ignoredColumns, teColumns), foldColumn);

      GBM gbm = new GBM(parms);
      long startGBMStep = System.currentTimeMillis();
      GBMModel gbmModel = gbm.trainModel().get();
      Scope.track_generic(gbmModel);
      long endGBMStep = System.currentTimeMillis();
      System.out.println("GBM training (LOOTE) took: " + (endGBMStep - startGBMStep));

      double auc_with_te = auc_score(gbmModel, testEncoded);
      double auc_without_te = trainDefaultGBM(target, seed);

      System.out.println("AUC with encoding:" + auc_with_te);
      System.out.println("AUC without encoding:" + auc_without_te);

      Assert.assertTrue(auc_without_te < auc_with_te);
    } finally {
      Scope.exit();
    }

  }

  @Test
  public void with_None_strategy_and_holdout_dataset() {
    System.out.println("Using None strategy with holdout on "+dataset+" dataset.");
    try {
      Scope.enter();
      int seed = 42;
      Map<String, Frame> datasets = prepareDataset(dataset, "_none", false, seed);
      Frame train = datasets.get("train");
      Frame teHoldout = datasets.get("holdout");
      Frame valid = datasets.get("valid");
      Frame test = datasets.get("test");
      Scope.track(train, valid, test);

      TargetEncoderParameters teParams = new TargetEncoderParameters();
      teParams._train= teHoldout._key;
      teParams._response_column = target;
      teParams._data_leakage_handling = DataLeakageHandlingStrategy.None;
      teParams._ignored_columns = ignoredColumns(train, append(teColumns, target));
      teParams._seed = seed;

      TargetEncoder te = new TargetEncoder(teParams);
      long start = System.currentTimeMillis();
      TargetEncoderModel teModel = te.trainModel().get();
      Scope.track_generic(teModel);
      long trainingStep = System.currentTimeMillis();

      Frame trainEncoded = teModel.score(train); Scope.track(trainEncoded);
      Frame validEncoded = teModel.score(valid); Scope.track(validEncoded);
      Frame testEncoded = teModel.score(test); Scope.track(testEncoded);
      long encodingStep = System.currentTimeMillis();

      printOutColumnsMetadata(testEncoded);
      
      System.out.println("TE training took (ms): " + (trainingStep - start));
      System.out.println("TE encoding took (ms): " + (encodingStep - trainingStep));
      System.out.println("TE total took (ms): " + (encodingStep - start));
      
      checkNumRows(train, trainEncoded);
      checkNumRows(valid, validEncoded);
      checkNumRows(test, testEncoded);

      long startTime = System.currentTimeMillis();

      GBMModel.GBMParameters parms = prepareGBMParameters(seed);
      parms._response_column = target;
      parms._train = trainEncoded._key;
      parms._valid = validEncoded._key;
      parms._ignored_columns = append(ignoredColumns, teColumns);

      GBM gbm = new GBM(parms);
      long startGBMStep = System.currentTimeMillis();
      GBMModel gbmModel = gbm.trainModel().get();
      Scope.track_generic(gbmModel);
      long endGBMStep = System.currentTimeMillis();
      System.out.println("GBM training (TE on holdout) took: " + (endGBMStep - startGBMStep));

      double auc_with_te = auc_score(gbmModel, testEncoded);
      double auc_without_te = trainDefaultGBM(target, seed);

      System.out.println("AUC with encoding:" + auc_with_te);
      System.out.println("AUC without encoding:" + auc_without_te);

      Assert.assertTrue(auc_without_te < auc_with_te);
    } finally {
      Scope.exit();
    }
  }

  private double trainDefaultGBM(String target, int seed) {
    try {
      Scope.enter();
      Map<String, Frame> datasets = prepareDataset(dataset, "_default", false, seed);
      Frame train = datasets.get("train");
      Frame valid = datasets.get("valid");
      Frame test = datasets.get("test");

      GBMModel.GBMParameters parms = prepareGBMParameters(seed);
      parms._response_column = target;
      parms._train = train._key;
      parms._valid = valid._key;
      parms._ignored_columns = ignoredColumns;
      
      GBM gbm = new GBM(parms);
      GBMModel gbmModel = gbm.trainModel().get();
      Scope.track_generic(gbmModel);
      return auc_score(gbmModel, test);
    } finally {
      Scope.exit();
    }
  }
  
  private GBMModel.GBMParameters prepareGBMParameters(int seed) {
    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    parms._score_tree_interval = 10;
    parms._ntrees = 1000;
    parms._max_depth = 5;
    parms._distribution = DistributionFamily.AUTO;
    parms._stopping_tolerance = 0.001;
    parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
    parms._stopping_rounds = 5;
    parms._seed = seed;
    
    return parms;
  }

  private double auc_score(Model model, Frame test) {
    model.score(test).delete();
    ModelMetrics metrics = ModelMetrics.getFromDKV(model, test);
    Scope.track_generic(metrics);
    return metrics.auc_obj()._auc;
  }

  private void checkNumRows(Frame before, Frame after) {
    long droppedCount = before.numRows()- after.numRows();
    if (droppedCount != 0) {
      Log.warn(String.format("Number of rows has dropped by %d after manipulations with frame ( %s , %s ).", droppedCount, before._key, after._key));
    }
  }
}
