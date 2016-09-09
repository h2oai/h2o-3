package hex.deepwater;

import hex.ModelMetricsMultinomial;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.gpu.ImagePred;
import water.gpu.ImageTrain;
import water.gpu.util;
import water.util.Log;
import water.util.RandomUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public class DeepWaterTest extends TestUtil {
  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  final boolean GPU = System.getenv("CUDA_PATH")!=null;

  static String expandPath(String path) {
    return path.replaceFirst("^~", System.getProperty("user.home"));
  }

  // This test has nothing to do with H2O - Pure integration test of deepwater/backends/mxnet
  @Ignore
  @Test
  public void inceptionPredictionMX() throws IOException {

    // load the cuda lib in CUDA_PATH, optional. theoretically we can find them if they are in LD_LIBRARY_PATH
    if (GPU) util.loadCudaLib();
    util.loadNativeLib("mxnet");
    util.loadNativeLib("Native");

    File imgFile = find_test_file("smalldata/deepwater/imagenet/test2.jpg");
    BufferedImage img = ImageIO.read(imgFile);

    int w = 224, h = 224;

    BufferedImage scaledImg = new BufferedImage(w, h, img.getType());

    Graphics2D g2d = scaledImg.createGraphics();
    g2d.drawImage(img, 0, 0, w, h, null);
    g2d.dispose();

    float[] pixels = new float[w * h * 3];

    int r_idx = 0;
    int g_idx = r_idx + w * h;
    int b_idx = g_idx + w * h;

    for (int i = 0; i < h; i++) {
      for (int j = 0; j < w; j++) {
        Color mycolor = new Color(scaledImg.getRGB(j, i));
        int red = mycolor.getRed();
        int green = mycolor.getGreen();
        int blue = mycolor.getBlue();
        pixels[r_idx] = red; r_idx++;
        pixels[g_idx] = green; g_idx++;
        pixels[b_idx] = blue; b_idx++;
      }
    }

    ImagePred m = new ImagePred();

    // the path to Inception model
    m.setModelPath(expandPath("~/deepwater/backends/mxnet/Inception"));

    m.loadInception();

    System.out.println("\n\n" + m.predict(pixels)+"\n\n");
  }

  // This tests the DeepWaterImageIterator
  @Ignore
  @Test
  public void inceptionFineTuning() throws IOException {
    if (GPU) util.loadCudaLib();
    util.loadNativeLib("mxnet");
    util.loadNativeLib("Native");

    String path = expandPath("~/kaggle/statefarm/input/");
    BufferedReader br = new BufferedReader(new FileReader(new File(path+"driver_imgs_list.csv")));

    ArrayList<Float> train_labels = new ArrayList<>();
    ArrayList<String> train_data = new ArrayList<>();

    String line;
    br.readLine(); //skip header
    while ((line = br.readLine()) != null) {
      String[] tmp = line.split(",");
      train_labels.add(new Float(tmp[1].substring(1)).floatValue());
      train_data.add(path+"train/"+tmp[1]+"/"+tmp[2]);
    }
    br.close();

    int batch_size = 64;
    int classes = 10;

    ImageTrain m = new ImageTrain();
    m.buildNet(classes, batch_size, "inception_bn");
    m.loadParam(expandPath("~/deepwater/backends/mxnet/Inception/model.params"));

    int max_iter = 6; //epochs
    int count = 0;
    for (int iter = 0; iter < max_iter; iter++) {
      m.setLR(3e-3f/(1+iter));
      //each iteration does a different random shuffle
      Random rng = RandomUtils.getRNG(0);
      rng.setSeed(0xDECAF+0xD00D*iter);
      Collections.shuffle(train_labels,rng);
      rng.setSeed(0xDECAF+0xD00D*iter);
      Collections.shuffle(train_data,rng);

      DeepWaterImageIterator img_iter = new DeepWaterImageIterator(train_data, train_labels, null /*do not subtract mean*/, batch_size, 224, 224, 3, true);
      Futures fs = new Futures();
      while(img_iter.Next(fs)){
        float[] data = img_iter.getData();
        float[] labels = img_iter.getLabel();
        float[] pred = m.train(data, labels);
        if (count++ % 10 != 0) continue;

        Vec[] classprobs = new Vec[classes];
        String[] names = new String[classes];
        for (int i=0;i<classes;++i) {
          names[i] = "c" + i;
          double[] vals=new double[batch_size];
          for (int j = 0; j < batch_size; ++j) {
            int idx=j*classes+i; //[p0,...,p9,p0,...,p9, ... ,p0,...,p9]
            vals[j] = pred[idx];
          }
          classprobs[i] = Vec.makeVec(vals,Vec.newKey());
        }
        water.fvec.Frame preds = new Frame(names,classprobs);
        long[] lab = new long[batch_size];
        for (int i=0;i<batch_size;++i)
          lab[i] = (long)labels[i];
        Vec actual = Vec.makeVec(lab,names,Vec.newKey());
        ModelMetricsMultinomial mm = ModelMetricsMultinomial.make(preds,actual);
        System.out.println(mm.toString());
      }
      m.saveParam(path+"/param."+iter);
    }
    scoreTestSet(path,classes,m);
  }

  public static void scoreTestSet(String path, int classes, ImageTrain m) throws IOException {
    // make test set predictions
    BufferedReader br = new BufferedReader(new FileReader(new File(path+"test_list.csv"))); //file created with 'cut -d, -f1 sample_submission.csv | sed 1d > test_list.csv'

    ArrayList<Float> test_labels = new ArrayList<>();
    ArrayList<String> test_data = new ArrayList<>();

    String line;
    while ((line = br.readLine()) != null) {
      test_labels.add(new Float(-999)); //dummy
      test_data.add(path+"test/"+line);
    }

    br.close();

    FileWriter fw = new FileWriter(path+"/submission.csv");
    int batch_size = 64; //avoid issues with batching at the end of the test set
    DeepWaterImageIterator img_iter = new DeepWaterImageIterator(test_data, test_labels, null /*do not subtract mean*/, batch_size, 224, 224, 3, true);
    fw.write("img,c0,c1,c2,c3,c4,c5,c6,c7,c8,c9\n");
    Futures fs = new Futures();
    while(img_iter.Next(fs)) {
      float[] data = img_iter.getData();
      String[] files = img_iter.getFiles();
      float[] pred = m.predict(data);
      for (int i=0;i<batch_size;++i) {
        String file = files[i];
        String[] pcs = file.split("/");
        fw.write(pcs[pcs.length-1]);
        for (int j=0;j<classes;++j) {
          int idx=i*classes+j;
          fw.write(","+pred[idx]);
        }
        fw.write("\n");
      }
    }
    fw.close();
  }

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
        p._rate = 1e-4;
        p._mini_batch_size = 8;
        p._train_samples_per_iteration = 0;
        p._epochs = 1;
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
      p._rate = 1e-3;
      p._epochs = 1;
      p._train_samples_per_iteration = samples;
      m = new DeepWater(p).trainModel().get();
      Assert.assertEquals(expected,m.iterations);
    } finally {
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  @Test public void trainSamplesPerIteration0() { trainSamplesPerIteration(0,1); }
  @Test public void trainSamplesPerIteration_auto() { trainSamplesPerIteration(-2,3); }
  @Test public void trainSamplesPerIteration_neg1() { trainSamplesPerIteration(-1,1); }
  @Test public void trainSamplesPerIteration_32() { trainSamplesPerIteration(32,9); }
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
      p._rate = 0.01;
      p._momentum_start = 0.5;
      p._momentum_stable = 0.5;
      p._stopping_rounds = 0;
      p._image_shape = new int[]{28,28};
      p._network = DeepWaterParameters.Network.lenet;
//      p._network = DeepWaterParameters.Network.inception_bn; //FAILS

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

  void runInception(int channels) {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._rate = 1e-3;
      p._epochs = 30;
      p._channels = channels;
      m = new DeepWater(p).trainModel().get();
      Log.info(m);
      Assert.assertTrue(m._output._training_metrics.cm().accuracy()>0.9);
    } finally {
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  @Test public void convergenceInceptionColor() { runInception(3); }
  @Test public void convergenceInceptionGrayScale() { runInception(1); }

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
        p._rate = 0; //no updates to original weights
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
        p._rate = 0; //no updates to original weights
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
      p._mini_batch_size = 4;
      p._epochs = 0.01;
//      p._rate = 0; //needed pass the test for inception/resnet
      p._seed = 1234;
      p._score_training_samples = 0;
      p._train_samples_per_iteration = p._mini_batch_size;
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
      DeepWaterModelInfo mi = m1.model_info().deep_clone();
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
        p._rate = 1e-4;
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
      p._mini_batch_size = 4;
      p._epochs = 0.01;
      p._seed = 1234;
      p._score_training_samples = 0;
      p._train_samples_per_iteration = p._mini_batch_size;
      m = new DeepWater(p).trainModel().get();
      Log.info(m);

      Assert.assertTrue(m.model_info()._imageTrain==null);

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
      p._mini_batch_size = 4;
      p._train_samples_per_iteration = p._mini_batch_size;
      p._rate = 0e-3;
      p._seed = 12345;
      p._epochs = 0.01;
      p._quiet_mode = true;
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
    try {
      if (GPU) util.loadCudaLib();
      util.loadNativeLib("mxnet");
      util.loadNativeLib("Native");
    } catch(Throwable t) {}

    int batch_size = 64;
    int classes = 10;
    ImageTrain m = new ImageTrain(28,28,1,0,1234,true);
    m.buildNet(classes, batch_size, "lenet");

    float[] data = new float[28*28*1*batch_size];
    float[] labels = new float[batch_size];
    int count=0;
    while(count++<1000) {
      Log.info("Iteration: " + count);
      m.train(data, labels);
    }
  }

  @Test
  public void saveLoop() {
    try {
      if (GPU) util.loadCudaLib();
      util.loadNativeLib("mxnet");
      util.loadNativeLib("Native");
    } catch(Throwable t) {}

    int batch_size = 64;
    int classes = 10;
    ImageTrain m = new ImageTrain(28,28,1,0,1234,true);
    m.buildNet(classes, batch_size, "lenet");

    int count=0;
    while(count++<1000) {
      Log.info("Iteration: " + count);
      m.saveParam("/tmp/testParam");
    }
  }

  @Test
  public void predictLoop() {
    try {
      if (GPU) util.loadCudaLib();
      util.loadNativeLib("mxnet");
      util.loadNativeLib("Native");
    } catch(Throwable t) {}

    int batch_size = 64;
    int classes = 10;
    ImageTrain m = new ImageTrain(28,28,1,0,1234,true);
    m.buildNet(classes, batch_size, "lenet");

    float[] data = new float[28*28*1*batch_size];
    int count=0;
    while(count++<1000) {
      Log.info("Iteration: " + count);
      m.predict(data);
    }
  }

  @Test
  public void trainPredictLoop() {
    try {
      if (GPU) util.loadCudaLib();
      util.loadNativeLib("mxnet");
      util.loadNativeLib("Native");
    } catch(Throwable t) {}

    int batch_size = 64;
    int classes = 10;
    ImageTrain m = new ImageTrain(28,28,1,0,1234,true);
    m.buildNet(classes, batch_size, "lenet");

    float[] data = new float[28*28*1*batch_size];
    float[] labels = new float[batch_size];
    int count=0;
    while(count++<1000) {
      Log.info("Iteration: " + count);
      m.train(data,labels);
      float[] p = m.predict(data);
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
    p._rate = 0e-3;
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
}

