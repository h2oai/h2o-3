package hex.deepwater;

import deepwater.backends.BackendModel;
import deepwater.backends.BackendParams;
import deepwater.backends.BackendTrain;
import deepwater.backends.RuntimeOptions;
import deepwater.datasets.ImageDataSet;
import hex.Model;
import hex.ModelMetricsBinomial;
import hex.ModelMetricsMultinomial;
import hex.splitframe.ShuffleSplitFrame;
import org.junit.*;
import water.*;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class DeepWaterAbstractIntegrationTest extends TestUtil {

  protected BackendTrain backend;
  @BeforeClass static public void _preconditionDeepWater() { // NOTE: the `_` force execution of this check after setup
    Assume.assumeTrue("DeepWater wasn't built", System.getProperty("deepwater.enabled", "false").equals("true"));
  }

  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  @Test
  public void memoryLeakTest() {
    DeepWaterModel m = null;
    Frame tr = null;
    int counter=3;
    while(counter-- > 0) {
      try {
        DeepWaterParameters p = new DeepWaterParameters();
        p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
        p._response_column = "C2";
        p._network = DeepWaterParameters.Network.vgg;
        p._learning_rate = 1e-4;
        p._mini_batch_size = 8;
        p._train_samples_per_iteration = 8;
        p._epochs = 1e-3;
        m = new DeepWater(p).trainModel().get();
        Log.info(m);
      } finally {
        if (m!=null) m.delete();
        if (tr!=null) tr.remove();
      }
    }
  }

  void trainSamplesPerIteration(int samples, int expected) {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._learning_rate = 1e-3;
      p._epochs = 3;
      p._train_samples_per_iteration = samples;
      m = new DeepWater(p).trainModel().get();
      Assert.assertEquals(expected,m.iterations);
    } finally {
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  @Test public void trainSamplesPerIteration0() { trainSamplesPerIteration(0,3); }
  @Test public void trainSamplesPerIteration_auto() { trainSamplesPerIteration(-2,1); }
  @Test public void trainSamplesPerIteration_neg1() { trainSamplesPerIteration(-1,3); }
  @Test public void trainSamplesPerIteration_32() { trainSamplesPerIteration(32,26); }
  @Test public void trainSamplesPerIteration_1000() { trainSamplesPerIteration(1000,1); }

  @Test
  public void overWriteWithBestModel() {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._epochs = 50;
      p._learning_rate = 0.01;
      p._momentum_start = 0.5;
      p._momentum_stable = 0.5;
      p._stopping_rounds = 0;
      p._image_shape = new int[]{28,28};
      p._network = DeepWaterParameters.Network.lenet;
      p._problem_type = DeepWaterParameters.ProblemType.image_classification;
      // score a lot
      p._train_samples_per_iteration = p._mini_batch_size;
      p._score_duty_cycle = 1;
      p._score_interval = 0;
      p._overwrite_with_best_model = true;

      m = new DeepWater(p).trainModel().get();
      Log.info(m);
      Assert.assertTrue(((ModelMetricsMultinomial)m._output._training_metrics).logloss()<2);
    } finally {
      if (m!=null) m.remove();
      if (tr!=null) tr.remove();
    }
  }

  void checkConvergence(int channels, DeepWaterParameters.Network network, int epochs) {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._network = network;
      p._learning_rate = 1e-3;
      p._epochs = epochs;
      p._channels = channels;
      p._problem_type = DeepWaterParameters.ProblemType.image_classification;

      m = new DeepWater(p).trainModel().get();
      Log.info(m);
      Assert.assertTrue(m._output._training_metrics.cm().accuracy()>0.9);
    } finally {
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  @Test public void convergenceInceptionColor() { checkConvergence(3, DeepWaterParameters.Network.inception_bn, 30); }
  @Test public void convergenceInceptionGrayScale() { checkConvergence(1, DeepWaterParameters.Network.inception_bn, 30); }

  @Ignore
  @Test public void convergenceGoogleNetColor() { checkConvergence(3, DeepWaterParameters.Network.googlenet, 50); }
  @Ignore
  @Test public void convergenceGoogleNetGrayScale() { checkConvergence(1, DeepWaterParameters.Network.googlenet, 50); }

  @Test public void convergenceLenetColor() { checkConvergence(3, DeepWaterParameters.Network.lenet, 100); }
  @Test public void convergenceLenetGrayScale() { checkConvergence(1, DeepWaterParameters.Network.lenet, 50); }

  @Ignore
  @Test public void convergenceVGGColor() { checkConvergence(3, DeepWaterParameters.Network.vgg, 50); }
  @Ignore
  @Test public void convergenceVGGGrayScale() { checkConvergence(1, DeepWaterParameters.Network.vgg, 50); }

  @Ignore
  @Test public void convergenceResnetColor() { checkConvergence(3, DeepWaterParameters.Network.resnet, 50); }
  @Ignore
  @Test public void convergenceResnetGrayScale() { checkConvergence(1, DeepWaterParameters.Network.resnet, 50); }

  @Ignore // FIXME - bad network definition?
  @Test public void convergenceAlexnetColor() { checkConvergence(3, DeepWaterParameters.Network.alexnet, 50); }
  @Ignore // FIXME - bad network definition?
  @Test public void convergenceAlexnetGrayScale() { checkConvergence(1, DeepWaterParameters.Network.alexnet, 50); }

  //FIXME
  @Ignore
  @Test
  public void reproInitialDistribution() {
    final int REPS=3;
    double[] values=new double[REPS];
    for (int i=0;i<REPS;++i) {
      DeepWaterModel m = null;
      Frame tr = null;
      try {
        DeepWaterParameters p = new DeepWaterParameters();
        p._train = (tr = parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
        p._response_column = "C2";
        p._learning_rate = 0; //no updates to original weights
        p._seed = 1234;
        p._epochs = 1; //for some reason, can't use 0 epochs
        p._channels = 1;
        p._train_samples_per_iteration = 0;
        m = new DeepWater(p).trainModel().get();
        Log.info(m);
        values[i]=((ModelMetricsMultinomial)m._output._training_metrics).logloss();
      } finally {
        if (m != null) m.delete();
        if (tr != null) tr.remove();
      }
    }
    for (int i=1;i<REPS;++i) Assert.assertEquals(values[0],values[i],1e-5*values[0]);
  }

  @Test
  public void reproInitialDistributionNegativeTest() {
    final int REPS=3;
    double[] values=new double[REPS];
    for (int i=0;i<REPS;++i) {
      DeepWaterModel m = null;
      Frame tr = null;
      try {
        DeepWaterParameters p = new DeepWaterParameters();
        p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
        p._response_column = "C2";
        p._learning_rate = 0; //no updates to original weights
        p._seed = i;
        p._epochs = 1; //for some reason, can't use 0 epochs
        p._channels = 1;
        p._train_samples_per_iteration = 0;
        m = new DeepWater(p).trainModel().get();
        Log.info(m);
        values[i] = ((ModelMetricsMultinomial)m._output._training_metrics).logloss();
      } finally {
        if (m!=null) m.delete();
        if (tr!=null) tr.remove();
      }
    }
    for (int i=1;i<REPS;++i) Assert.assertNotEquals(values[0],values[i],1e-5*values[0]);
  }

  // Pure convenience wrapper
  @Ignore @Test public void settingModelInfoAll() {
    for (DeepWaterParameters.Network network : DeepWaterParameters.Network.values()) {
      if (network== DeepWaterParameters.Network.user) continue;
      if (network== DeepWaterParameters.Network.auto) continue;
      settingModelInfo(network);
    }
  }

  @Test public void settingModelInfoAlexnet() { settingModelInfo(DeepWaterParameters.Network.alexnet); }
  @Test public void settingModelInfoLenet() { settingModelInfo(DeepWaterParameters.Network.lenet); }
  @Test public void settingModelInfoVGG() { settingModelInfo(DeepWaterParameters.Network.vgg); }
  @Test public void settingModelInfoInception() { settingModelInfo(DeepWaterParameters.Network.inception_bn); }
  @Test public void settingModelInfoResnet() { settingModelInfo(DeepWaterParameters.Network.resnet); }

  void settingModelInfo(DeepWaterParameters.Network network) {
    DeepWaterModel m1 = null;
    DeepWaterModel m2 = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._network = network;
      p._mini_batch_size = 2;
      p._epochs = 0.01;
//      p._learning_rate = 0; //needed pass the test for inception/resnet
      p._seed = 1234;
      p._score_training_samples = 0;
      p._train_samples_per_iteration = p._mini_batch_size;
      p._problem_type = DeepWaterParameters.ProblemType.image_classification;

      // first model
      Job j1 = new DeepWater(p).trainModel();
      m1 = (DeepWaterModel)j1.get();
      int h1 = Arrays.hashCode(m1.model_info()._modelparams);
      m1.doScoring(tr,null,j1._key,m1.iterations,true);
      double l1 = m1.loss();

      // second model (different seed)
      p._seed = 4321;
      Job j2 = new DeepWater(p).trainModel();
      m2 = (DeepWaterModel)j2.get();
//      m2.doScoring(tr,null,j2._key,m2.iterations,true);
//      double l2 = m2.loss();
      int h2 = Arrays.hashCode(m2.model_info()._modelparams);

      // turn the second model into the first model
      m2.removeNativeState();
      DeepWaterModelInfo mi = IcedUtils.deepCopy(m1.model_info());
      m2.set_model_info(mi);
      m2.doScoring(tr,null,j2._key,m2.iterations,true);
      double l3 = m2.loss();
      int h3 = Arrays.hashCode(m2.model_info()._modelparams);

      Log.info("Checking assertions for network: " + network);
      Assert.assertNotEquals(h1, h2);
      Assert.assertEquals(h1, h3);
      Assert.assertEquals(l1, l3, 1e-5*l1);
    } finally {
      if (m1!=null) m1.delete();
      if (m2!=null) m2.delete();
      if (tr!=null) tr.remove();
    }
  }

  //FIXME
  @Ignore
  @Test
  public void reproTraining() {
    final int REPS=3;
    double[] values=new double[REPS];
    for (int i=0;i<REPS;++i) {
      DeepWaterModel m = null;
      Frame tr = null;
      try {
        DeepWaterParameters p = new DeepWaterParameters();
        p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
        p._response_column = "C2";
        p._learning_rate = 1e-4;
        p._seed = 1234;
        p._epochs = 1;
        p._channels = 1;
        p._train_samples_per_iteration = 0;
        m = new DeepWater(p).trainModel().get();
        Log.info(m);
        values[i] = ((ModelMetricsMultinomial)m._output._training_metrics).logloss();
      } finally {
        if (m!=null) m.delete();
        if (tr!=null) tr.remove();
      }
    }
    for (int i=1;i<REPS;++i) Assert.assertEquals(values[0],values[i],1e-5*values[0]);
  }

  // Pure convenience wrapper
  @Ignore @Test public void deepWaterLoadSaveTestAll() {
    for (DeepWaterParameters.Network network : DeepWaterParameters.Network.values()) {
      if (network== DeepWaterParameters.Network.auto) continue;
      if (network== DeepWaterParameters.Network.user) continue;
      deepWaterLoadSaveTest(network);
    }
  }
  @Test public void deepWaterLoadSaveTestAlexnet() { deepWaterLoadSaveTest(DeepWaterParameters.Network.alexnet); }
  @Test public void deepWaterLoadSaveTestLenet() { deepWaterLoadSaveTest(DeepWaterParameters.Network.lenet); }
  @Test public void deepWaterLoadSaveTestVGG() { deepWaterLoadSaveTest(DeepWaterParameters.Network.vgg); }
  @Test public void deepWaterLoadSaveTestInception() { deepWaterLoadSaveTest(DeepWaterParameters.Network.inception_bn); }
  @Test public void deepWaterLoadSaveTestResnet() { deepWaterLoadSaveTest(DeepWaterParameters.Network.resnet); }

  void deepWaterLoadSaveTest(DeepWaterParameters.Network network) {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._network = network;
      p._mini_batch_size = 2;
      p._epochs = 0.01;
      p._seed = 1234;
      p._score_training_samples = 0;
      p._train_samples_per_iteration = p._mini_batch_size;
      p._problem_type = DeepWaterParameters.ProblemType.image_classification;
      m = new DeepWater(p).trainModel().get();
      Log.info(m);

      Assert.assertTrue(m.model_info()._backend ==null);

      int hashCodeNetwork = java.util.Arrays.hashCode(m.model_info()._network);
      int hashCodeParams = java.util.Arrays.hashCode(m.model_info()._modelparams);
      Log.info("Hash code for original network: " + hashCodeNetwork);
      Log.info("Hash code for original parameters: " + hashCodeParams);

      // move stuff back and forth
      m.removeNativeState();
      m.model_info().javaToNative();
      m.model_info().nativeToJava();
      int hashCodeNetwork2 = java.util.Arrays.hashCode(m.model_info()._network);
      int hashCodeParams2 = java.util.Arrays.hashCode(m.model_info()._modelparams);
      Log.info("Hash code for restored network: " + hashCodeNetwork2);
      Log.info("Hash code for restored parameters: " + hashCodeParams2);
      Assert.assertEquals(hashCodeNetwork, hashCodeNetwork2);
      Assert.assertEquals(hashCodeParams, hashCodeParams2);
    } finally {
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  @Test
  public void deepWaterCV() {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._network = DeepWaterParameters.Network.lenet;
      p._nfolds = 3;
      p._epochs = 2;
      m = new DeepWater(p).trainModel().get();
      Log.info(m);
    } finally {
      if (m!=null) m.deleteCrossValidationModels();
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  // Pure convenience wrapper
  @Ignore @Test public void restoreStateAll() {
    for (DeepWaterParameters.Network network : DeepWaterParameters.Network.values()) {
      if (network== DeepWaterParameters.Network.user) continue;
      if (network== DeepWaterParameters.Network.auto) continue;
      restoreState(network);
    }
  }

  @Test public void restoreStateAlexnet() { restoreState(DeepWaterParameters.Network.alexnet); }
  @Test public void restoreStateLenet() { restoreState(DeepWaterParameters.Network.lenet); }
  @Test public void restoreStateVGG() { restoreState(DeepWaterParameters.Network.vgg); }
  @Test public void restoreStateInception() { restoreState(DeepWaterParameters.Network.inception_bn); }
  @Test public void restoreStateResnet() { restoreState(DeepWaterParameters.Network.resnet); }

  public void restoreState(DeepWaterParameters.Network network) {
    DeepWaterModel m1 = null;
    DeepWaterModel m2 = null;
    Frame tr = null;
    Frame pred = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._network = network;
      p._response_column = "C2";
      p._mini_batch_size = 2;
      p._train_samples_per_iteration = p._mini_batch_size;
      p._learning_rate = 0e-3;
      p._seed = 12345;
      p._epochs = 0.01;
      p._quiet_mode = true;
      p._problem_type = DeepWaterParameters.ProblemType.image_classification;
      m1 = new DeepWater(p).trainModel().get();

      Log.info("Scoring the original model.");
      pred = m1.score(tr);
      pred.remove(0).remove();
      ModelMetricsMultinomial mm1 = ModelMetricsMultinomial.make(pred, tr.vec(p._response_column));
      Log.info("Original LL: " + ((ModelMetricsMultinomial) m1._output._training_metrics).logloss());
      Log.info("Scored   LL: " + mm1.logloss());
      pred.remove();

      Log.info("Keeping the raw byte[] of the model.");
      byte[] raw = new AutoBuffer().put(m1).buf();

      Log.info("Removing the model from the DKV.");
      m1.remove();

      Log.info("Restoring the model from the raw byte[].");
      m2 = new AutoBuffer(raw).get();

      Log.info("Scoring the restored model.");
      pred = m2.score(tr);
      pred.remove(0).remove();
      ModelMetricsMultinomial mm2 = ModelMetricsMultinomial.make(pred, tr.vec(p._response_column));
      Log.info("Restored LL: " + mm2.logloss());

      Assert.assertEquals(((ModelMetricsMultinomial) m1._output._training_metrics).logloss(), mm1.logloss(), 1e-5*mm1.logloss()); //make sure scoring is self-consistent
      Assert.assertEquals(mm1.logloss(), mm2.logloss(), 1e-5*mm1.logloss());

    } finally {
      if (m1 !=null) m1.delete();
      if (m2!=null) m2.delete();
      if (tr!=null) tr.remove();
      if (pred!=null) pred.remove();
    }
  }

  @Test
  public void trainLoop() throws InterruptedException {
    int batch_size = 64;
    BackendModel m = buildLENET();

    float[] data = new float[28*28*1*batch_size];
    float[] labels = new float[batch_size];
    int count=0;
    while(count++<1000) {
      Log.info("Iteration: " + count);
      backend.train(m, data, labels);
    }
  }

  private BackendModel buildLENET() {
    int batch_size = 64;
    int classes = 10;

    ImageDataSet dataset = new ImageDataSet(29, 28, 1);
    RuntimeOptions opts = new RuntimeOptions();
    opts.setUseGPU(true);
    opts.setSeed(1234);
    opts.setDeviceID(0);
    BackendParams bparm = new BackendParams();
    bparm.set("mini_batch_size", batch_size);
    return backend.buildNet(dataset, opts, bparm, classes, "lenet");
  }

  @Test
  public void saveLoop() throws IOException {
    BackendModel m = buildLENET();
    File f = File.createTempFile("saveLoop", ".tmp");

    for(int count=0; count < 3; count++) {
      Log.info("Iteration: " + count);
      backend.saveParam(m, f.getAbsolutePath());
    }
  }

  @Test
  public void predictLoop() {
    BackendModel m = buildLENET();
    int batch_size = 64;
    float[] data = new float[28*28*1*batch_size];
    int count=0;
    while(count++<3) {
      Log.info("Iteration: " + count);
      backend.predict(m, data);
    }
  }

  @Test
  public void trainPredictLoop() {
    int batch_size = 64;
    BackendModel m = buildLENET();

    float[] data = new float[28*28*1*batch_size];
    float[] labels = new float[batch_size];
    int count=0;
    while(count++<1000) {
      Log.info("Iteration: " + count);
      backend.train(m, data,labels);
      float[] p = backend.predict(m, data);
    }
  }

  @Test
  public void scoreLoop() {
    DeepWaterParameters p = new DeepWaterParameters();
    Frame tr;
    p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
    p._network = DeepWaterParameters.Network.lenet;
    p._response_column = "C2";
    p._mini_batch_size = 4;
    p._train_samples_per_iteration = p._mini_batch_size;
    p._learning_rate = 0e-3;
    p._seed = 12345;
    p._epochs = 0.01;
    p._quiet_mode = true;
    DeepWater j= new DeepWater(p);
    DeepWaterModel m = j.trainModel().get();

    int count=0;
    while(count++<100) {
      Log.info("Iteration: " + count);
      // turn the second model into the first model
      m.doScoring(tr,null,j._job._key,m.iterations,true);
    }
    tr.remove();
    m.remove();
  }

//  @Test public void imageToPixels() throws IOException {
//    final File imgFile = find_test_file("smalldata/deepwater/imagenet/test2.jpg");
//    final float[] dest = new float[28*28*3];
//    int count=0;
//    Futures fs = new Futures();
//    while(count++<10000)
//      fs.add(H2O.submitTask(
//          new H2O.H2OCountedCompleter() {
//            @Override
//            public void compute2() {
//              try {
//                util.img2pixels(imgFile.toString(), 28, 28, 3, dest, 0, null);
//              } catch (IOException e) {
//                e.printStackTrace();
//              }
//              tryComplete();
//            }
//          }));
//    fs.blockForPending();
//  }

  @Test
  public void prostate() {
    Frame tr = null;
    DeepWaterModel m = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr = parse_test_file("smalldata/prostate/prostate.csv"))._key;
      p._response_column = "CAPSULE";
      p._ignored_columns = new String[]{"ID"};
      for (String col : new String[]{"RACE", "DPROS", "DCAPS", "CAPSULE", "GLEASON"}) {
        Vec v = tr.remove(col);
        tr.add(col, v.toCategoricalVec());
        v.remove();
      }
      DKV.put(tr);
      p._seed = 1234;
      p._epochs = 500;
      DeepWater j = new DeepWater(p);
      m = j.trainModel().get();
      Assert.assertTrue((m._output._training_metrics).auc_obj()._auc > 0.90);
    } finally {
      if (tr!=null) tr.remove();
      if (m!=null) m.remove();
    }
  }

  @Test
  public void MNIST() {
    Frame tr = null;
    Frame va = null;
    DeepWaterModel m = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      File file = find_test_file("bigdata/laptop/mnist/train.csv.gz");
      File valid = find_test_file("bigdata/laptop/mnist/test.csv.gz");
      if (file != null) {
        p._response_column = "C785";
        NFSFileVec trainfv = NFSFileVec.make(file);
        tr = ParseDataset.parse(Key.make(), trainfv._key);
        NFSFileVec validfv = NFSFileVec.make(valid);
        va = ParseDataset.parse(Key.make(), validfv._key);
        for (String col : new String[]{p._response_column}) {
          Vec v = tr.remove(col); tr.add(col, v.toCategoricalVec()); v.remove();
          v = va.remove(col); va.add(col, v.toCategoricalVec()); v.remove();
        }
        DKV.put(tr);
        DKV.put(va);

        p._train = tr._key;
        p._valid = va._key;
        p._hidden = new int[]{500,500};
        DeepWater j = new DeepWater(p);
        m = j.trainModel().get();
        Assert.assertTrue(((ModelMetricsMultinomial)(m._output._validation_metrics)).mean_per_class_error() < 0.05);
      }
    } finally {
      if (tr!=null) tr.remove();
      if (va!=null) va.remove();
      if (m!=null) m.remove();
    }
  }

  @Test
  public void MNISTSparse() {
    Frame tr = null;
    Frame va = null;
    DeepWaterModel m = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      File file = find_test_file("bigdata/laptop/mnist/train.csv.gz");
      File valid = find_test_file("bigdata/laptop/mnist/test.csv.gz");
      if (file != null) {
        p._response_column = "C785";
        NFSFileVec trainfv = NFSFileVec.make(file);
        tr = ParseDataset.parse(Key.make(), trainfv._key);
        NFSFileVec validfv = NFSFileVec.make(valid);
        va = ParseDataset.parse(Key.make(), validfv._key);
        for (String col : new String[]{p._response_column}) {
          Vec v = tr.remove(col); tr.add(col, v.toCategoricalVec()); v.remove();
          v = va.remove(col); va.add(col, v.toCategoricalVec()); v.remove();
        }
        DKV.put(tr);
        DKV.put(va);

        p._train = tr._key;
        p._valid = va._key;
        p._hidden = new int[]{500,500};
        p._sparse = true;
        DeepWater j = new DeepWater(p);
        m = j.trainModel().get();
        Assert.assertTrue(((ModelMetricsMultinomial)(m._output._validation_metrics)).mean_per_class_error() < 0.05);
      }
    } finally {
      if (tr!=null) tr.remove();
      if (va!=null) va.remove();
      if (m!=null) m.remove();
    }
  }

  @Test
  public void MNISTHinton() {
    Frame tr = null;
    Frame va = null;
    DeepWaterModel m = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      File file = find_test_file("bigdata/laptop/mnist/train.csv.gz");
      File valid = find_test_file("bigdata/laptop/mnist/test.csv.gz");
      if (file != null) {
        p._response_column = "C785";
        NFSFileVec trainfv = NFSFileVec.make(file);
        tr = ParseDataset.parse(Key.make(), trainfv._key);
        NFSFileVec validfv = NFSFileVec.make(valid);
        va = ParseDataset.parse(Key.make(), validfv._key);
        for (String col : new String[]{p._response_column}) {
          Vec v = tr.remove(col); tr.add(col, v.toCategoricalVec()); v.remove();
          v = va.remove(col); va.add(col, v.toCategoricalVec()); v.remove();
        }
        DKV.put(tr);
        DKV.put(va);

        p._hidden = new int[]{1024,1024,2048};
        p._input_dropout_ratio = 0.1;
        p._hidden_dropout_ratios = new double[]{0.5,0.5,0.5};
        p._stopping_rounds = 0;
        p._learning_rate = 1e-3;
        p._mini_batch_size = 32;
        p._epochs = 20;
        p._train = tr._key;
        p._valid = va._key;
        DeepWater j = new DeepWater(p);
        m = j.trainModel().get();
        Assert.assertTrue(((ModelMetricsMultinomial)(m._output._validation_metrics)).mean_per_class_error() < 0.05);
      }
    } finally {
      if (tr!=null) tr.remove();
      if (va!=null) va.remove();
      if (m!=null) m.remove();
    }
  }

  @Test
  public void Airlines() {
    Frame tr = null;
    DeepWaterModel m = null;
    Frame[] splits = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      File file = find_test_file("smalldata/airlines/allyears2k_headers.zip");
      if (file != null) {
        p._response_column = "IsDepDelayed";
        p._ignored_columns = new String[]{"DepTime","ArrTime","Cancelled","CancellationCode","Diverted","CarrierDelay","WeatherDelay","NASDelay","SecurityDelay","LateAircraftDelay","IsArrDelayed"};
        NFSFileVec trainfv = NFSFileVec.make(file);
        tr = ParseDataset.parse(Key.make(), trainfv._key);
        for (String col : new String[]{p._response_column, "UniqueCarrier", "Origin", "Dest"}) {
          Vec v = tr.remove(col); tr.add(col, v.toCategoricalVec()); v.remove();
        }
        DKV.put(tr);

        double[] ratios = ard(0.5, 0.5);
        Key[] keys = aro(Key.make("test.hex"), Key.make("train.hex"));
        splits = ShuffleSplitFrame.shuffleSplitFrame(tr, keys, ratios, 42);

        p._train = keys[0];
        p._valid = keys[1];
        DeepWater j = new DeepWater(p);
        m = j.trainModel().get();
        Assert.assertTrue(((ModelMetricsBinomial)(m._output._validation_metrics)).auc() > 0.65);
      }
    } finally {
      if (tr!=null) tr.remove();
      if (m!=null) m.remove();
      if (splits!=null) for(Frame s: splits) s.remove();
    }
  }

  private void MOJOTest(Model.Parameters.CategoricalEncodingScheme categoricalEncodingScheme, boolean enumCols, boolean standardize) {
    Frame tr = null;
    DeepWaterModel m = null;
    Frame preds = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();

      p._train = (tr = parse_test_file("smalldata/prostate/prostate.csv"))._key;
      p._response_column = "CAPSULE";
      p._ignored_columns = new String[]{"ID"};
      for (String col : new String[]{p._response_column}) {
        Vec v = tr.remove(col);
        tr.add(col, v.toCategoricalVec());
        v.remove();
      }
      if (enumCols) {
        for (String col : new String[]{"RACE", "DPROS", "DCAPS", "GLEASON"}) {
          Vec v = tr.remove(col);
          tr.add(col, v.toCategoricalVec());
          v.remove();
        }
      }
      DKV.put(tr);
      p._seed = 125;
      p._epochs = 1;
      p._categorical_encoding = categoricalEncodingScheme;
      p._score_training_samples = 100;
      p._score_validation_samples = 100;
      p._shuffle_training_data = false;
      p._standardize = standardize;
      p._hidden = new int[]{10,10};
      m = new DeepWater(p).trainModel().get();

      preds = m.score(p._train.get());
      Assert.assertTrue(m.testJavaScoring(p._train.get(),preds,1e-3));
    } finally {
      if (tr!=null) tr.remove();
      if (m!=null) m.remove();
      if (preds!=null) preds.remove();
    }
  }

  @Test public void MOJOTestNumericNonStandardized() { MOJOTest(Model.Parameters.CategoricalEncodingScheme.AUTO, false, false);}
  @Test public void MOJOTestNumeric() { MOJOTest(Model.Parameters.CategoricalEncodingScheme.AUTO, false, true);}
  @Test public void MOJOTestCatInternal() { MOJOTest(Model.Parameters.CategoricalEncodingScheme.OneHotInternal, true, true);}
  @Test public void MOJOTestCatExplicit() { MOJOTest(Model.Parameters.CategoricalEncodingScheme.OneHotExplicit, true, true);}

  // ------- Text conversions

  @Test
  public void textsToArrayTest() {
    ArrayList<String> texts = new ArrayList<>();
    ArrayList<String> labels = new ArrayList<>();

    texts.add("the rock is destined to be the 21st century's new \" conan \" and that he's going to make a splash even greater than arnold schwarzenegger , jean-claud van damme or steven segal .");
    texts.add("the gorgeously elaborate continuation of \" the lord of the rings \" trilogy is so huge that a column of words cannot adequately describe co-writer/director peter jackson's expanded vision of j . r . r . tolkien's middle-earth .");
    texts.add("effective but too-tepid biopic");
    labels.add("pos");
    labels.add("pos");
    labels.add("pos");

    texts.add("simplistic , silly and tedious .");
    texts.add("it's so laddish and juvenile , only teenage boys could possibly find it funny .");
    texts.add("exploitative and largely devoid of the depth or sophistication that would make watching such a graphic treatment of the crimes bearable .");
    labels.add("neg");
    labels.add("neg");
    labels.add("neg");

    ArrayList<int[]> coded = texts2array(texts);
   // System.out.println(coded);
    for (int[] a : coded) {
      System.out.println(Arrays.toString(a));
    }

    System.out.println("rows " + coded.size() + " cols " + coded.get(0).length);

    Assert.assertEquals(6, coded.size());
    Assert.assertEquals(38, coded.get(0).length);
  }

  public String cleanString(String s) {
    //Tokenization/string cleaning for all datasets except for SST.
    //        Original taken from https://github.com/yoonkim/CNN_sentence/blob/master/process_data.py
    String string = s;
    string = string.replaceAll("[^A-Za-z0-9(),!?\\'\\`]", " ");
    string = string.replaceAll("'s", " 's");
    string = string.replaceAll("'ve", " 've");
    string = string.replaceAll("n't", " n't");
    string = string.replaceAll("'re", " 're");
    string = string.replaceAll("'d", " 'd");
    string = string.replaceAll("'ll", " 'll");
    string = string.replaceAll(",", " , ");
    string = string.replaceAll("!", " ! ");
    string = string.replaceAll("\\(", " ( ");
    string = string.replaceAll("\\)", " ) ");
    string = string.replaceAll("\\?", " ? ");
    string = string.replaceAll("\\s{2,}", " ");
    return string.trim().toLowerCase();
  }

  public String[] tokenize(String text) {
   // System.out.println(cleanString(text));
    return cleanString(text).split(" ");
  }

  public int[] tokensToArray(String[] tokens, int padToLength, Map<String, Integer> dict) {
    int dictSize = dict.size();
    int len = tokens.length;
    int pad = padToLength - len;
    int[] data = new int[padToLength];
    int ix = 0;
    for (String t : tokens) {
      int index = dict.get(t);
      data[ix] = index;
      ix += 1;
    }
    for (int i = 0; i < pad; i++) {
      int index = dict.get(PADDING_SYMBOL);
      data[ix] = index;
      ix += 1;
    }
    return data;
  }

  public static String PADDING_SYMBOL = "</s>";

  public ArrayList<int[]> texts2array(ArrayList<String> texts) {
    int maxlen = 0;
    int index = 0;
    Map<String, Integer> dict = new HashMap<>();
    dict.put(PADDING_SYMBOL, index);
    index += 1;
    for (String text : texts) {
      String[] tokens = tokenize(text);
      for (String token : tokens) {
        if (!dict.containsKey(token)) {
          dict.put(token, index);
          index += 1;
        }
      }
      int len = tokens.length;
      if (len > maxlen) maxlen = len;
    }
    System.out.println(dict);
    System.out.println("maxlen " + maxlen);
    System.out.println("dict size " + dict.size());
    Assert.assertEquals(38, maxlen);
    Assert.assertEquals(88, index);
    Assert.assertEquals(88, dict.size());

    ArrayList<int[]> array = new ArrayList<>();
    for (String text: texts) {
      int[] data = tokensToArray(tokenize(text), maxlen, dict);
   //   System.out.println(text);
   //   System.out.println(Arrays.toString(data));
      array.add(data);
    }
    return array;
  }


  /*
  public ArrayList<int[]> texts2arrayOnehot(ArrayList<String> texts) {
    int maxlen = 0;
    int index = 0;
    Map<String, Integer> dict = new HashMap<>();
    dict.put(PADDING_SYMBOL, index);
    index += 1;
    for (String text : texts) {
      String[] tokens = tokenize(text);
      for (String token : tokens) {
        if (!dict.containsKey(token)) {
          dict.put(token, index);
          index += 1;
        }
      }
      int len = tokens.length;
      if (len > maxlen) maxlen = len;
    }
    System.out.println(dict);
    System.out.println("maxlen " + maxlen);
    System.out.println("dict size " + dict.size());
    Assert.assertEquals(38, maxlen);
    Assert.assertEquals(88, index);
    Assert.assertEquals(88, dict.size());

    ArrayList<int[]> array = new ArrayList<>();
    for (String text: texts) {
      ArrayList<int[]> data = tokensToArray(tokenize(text), maxlen, dict);
      System.out.println(text);
      System.out.println(" rows " + data.size() + "  cols " + data.get(0).length);
      //for (int[] x : data) {
      //  System.out.println(Arrays.toString(x));
      //}
      array.addAll(data);
    }
    return array;
  }
  */

}

