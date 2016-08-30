package hex.deepwater;

import hex.ModelMetricsMultinomial;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.AutoBuffer;
import water.Futures;
import water.Key;
import water.TestUtil;
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
  public void memoryLeakTest() throws IOException {
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
        p._mini_batch_size = 16;
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

  @Test
  public void testTrainSamplesPerIteration0() throws IOException {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._rate = 1e-3;
      p._epochs = 1;
      p._train_samples_per_iteration = 0;
      m = new DeepWater(p).trainModel().get();
      Assert.assertTrue(m.iterations==1);
    } finally {
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  @Test
  public void testTrainSamplesPerIteration_auto() throws IOException {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._rate = 1e-3;
      p._epochs = 1;
      p._train_samples_per_iteration = -2;
      m = new DeepWater(p).trainModel().get();
      Assert.assertTrue(m.iterations>1);
    } finally {
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }
  @Test
  public void testTrainSamplesPerIteration_neg1() throws IOException {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._rate = 1e-3;
      p._epochs = 1;
      p._train_samples_per_iteration = -1;
      m = new DeepWater(p).trainModel().get();
      Assert.assertTrue(m.iterations==1);
    } finally {
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  @Test
  public void testTrainSamplesPerIteration_32() throws IOException {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._rate = 1e-3;
      p._epochs = 1;
      p._score_duty_cycle = 1;
      p._score_interval = 1;
      p._train_samples_per_iteration = p._mini_batch_size;
      m = new DeepWater(p).trainModel().get();
      Assert.assertTrue(m.iterations==9);
      Assert.assertTrue(m.epoch_counter>1);
      Assert.assertTrue(m.epoch_counter<2);
    } finally {
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  @Test
  public void testTrainSamplesPerIteration_1000() throws IOException {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._rate = 1e-3;
      p._epochs = 1;
      p._train_samples_per_iteration = 1000;
      m = new DeepWater(p).trainModel().get();
      Assert.assertTrue(m.iterations==1);
      Assert.assertTrue(m.epoch_counter>3);
      Assert.assertTrue(m.epoch_counter<4);
    } finally {
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  @Test
  public void testOverWriteWithBestModel() throws IOException {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._epochs = 2;
      p._rate = 0.01;
      p._momentum_start = 0.5;
      p._momentum_stable = 0.5;

      // score a lot
      p._train_samples_per_iteration = p._mini_batch_size;
      p._score_duty_cycle = 1;
      p._score_interval = 0;
      p._overwrite_with_best_model = true;

      m = new DeepWater(p).trainModel().get();
      Log.info(m);
      Assert.assertTrue(((ModelMetricsMultinomial)m._output._training_metrics).logloss()<2);
    } finally {
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  @Test
  public void testConvergenceInceptionColor() throws IOException {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._rate = 1e-3;
      p._epochs = 30;
      m = new DeepWater(p).trainModel().get();
      Log.info(m);
      Assert.assertTrue(m._output._training_metrics.cm().accuracy()>0.9);
    } finally {
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  @Test
  public void testConvergenceInceptionGrayScale() throws IOException {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._rate = 1e-3;
      p._epochs = 25;
      p._channels = 1;
      p._train_samples_per_iteration = 0;
      m = new DeepWater(p).trainModel().get();
      Log.info(m);
      Assert.assertTrue(m._output._training_metrics.cm().accuracy()>0.9);
    } finally {
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  @Test
  public void testReproInitialDistribution() throws IOException {
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
    for (int i=1;i<REPS;++i) Assert.assertEquals(values[0],values[i],1e-6);
  }

  @Test
  public void testReproInitialDistributionNegativeTest() throws IOException {
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
    for (int i=1;i<REPS;++i) Assert.assertNotEquals(values[0],values[i],1e-6);
  }

  @Test
  public void testReproTraining() throws IOException {
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
    for (int i=1;i<REPS;++i) Assert.assertEquals(values[0],values[i],1e-6);
  }

  @Test
  public void deepWaterLoadSaveTest() {
    for (DeepWaterParameters.Network network : DeepWaterParameters.Network.values()) {
      if (network == DeepWaterParameters.Network.user) continue;

      // FIXME
      if (network == DeepWaterParameters.Network.resnet) continue; //FAILS
      if (network == DeepWaterParameters.Network.inception_bn) continue; //FAILS
      if (network == DeepWaterParameters.Network.auto) continue; //FAILS

      DeepWaterModel m = null;
      Frame tr = null;
      Frame pred1 = null;
      Frame pred2 = null;
      Frame pred3 = null;
      try {
        DeepWaterParameters p = new DeepWaterParameters();
        p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
        p._response_column = "C2";
        p._network = network;
        p._mini_batch_size = 16;
        p._epochs = 0.1;
        p._seed = 1234;
        p._score_training_samples = 0;
        p._train_samples_per_iteration = p._mini_batch_size;
        m = new DeepWater(p).trainModel().get();
        Log.info(m);

        Assert.assertTrue(m.model_info()._imageTrain==null);

        // regular prediction
        pred1 = m.score(tr);
        pred1.remove(0).remove();
        ModelMetricsMultinomial mm = ModelMetricsMultinomial.make(pred1, tr.vec(p._response_column));
        Assert.assertEquals(mm._logloss, ((ModelMetricsMultinomial)m._output._training_metrics)._logloss, 1e-6);
        Assert.assertTrue(m.model_info()._imageTrain==null);

        // do it again
        pred2 = m.score(tr);
        pred2.remove(0).remove();
        Assert.assertTrue(isIdenticalUpToRelTolerance(pred1, pred2, 1e-6));

        // move stuff back and forth a bit
        int count=10;
        while(count-->0) {
          m.model_info().javaToNative();
          m.model_info().nativeToJava();
        }
        pred3 = m.score(tr);
        pred3.remove(0).remove();
        Assert.assertTrue(isIdenticalUpToRelTolerance(pred2, pred3, 1e-6));

      } finally {
        if (m!=null) m.delete();
        if (tr!=null) tr.remove();
        if (pred1!=null) pred1.remove();
        if (pred2!=null) pred2.remove();
        if (pred3!=null) pred3.remove();
      }
    }
  }

  @Test
  public void deepWaterCV() throws IOException {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
      p._network = DeepWaterParameters.Network.lenet;
//      p._network = DeepWaterParameters.Network.user;
//      p._network_definition_file = expandPath("~/deepwater/backends/mxnet/Inception/model-symbol.json");
//      p._network_parameters_file = expandPath("~/deepwater/backends/mxnet/Inception/model.params");
//      p._mean_image_file = expandPath("~/deepwater/backends/mxnet/Inception/mean.nd");
//      p._width = 224;
//      p._height = 224;
//      p._channels = 3;
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

  @Test
  public void testRestoreState() throws IOException {
    for (DeepWaterParameters.Network network : DeepWaterParameters.Network.values()) {
      if (network == DeepWaterParameters.Network.user) continue;

      // FIXME
      if (network == DeepWaterParameters.Network.resnet) continue; //FAILS
      if (network == DeepWaterParameters.Network.inception_bn) continue; //FAILS
      if (network == DeepWaterParameters.Network.auto) continue; //FAILS

      DeepWaterModel m1 = null;
      DeepWaterModel m2 = null;
      Frame tr = null;
      Frame pred = null;
      try {
        DeepWaterParameters p = new DeepWaterParameters();
        p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
        p._network = network;
        p._response_column = "C2";
        p._mini_batch_size = 16;
        p._train_samples_per_iteration = p._mini_batch_size;
        p._rate = 0e-3;
        p._seed = 12345;
        p._epochs = 0.1;
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

        Assert.assertEquals(((ModelMetricsMultinomial) m1._output._training_metrics).logloss(), mm1.logloss(), 1e-6); //make sure scoring is self-consistent
        Assert.assertEquals(mm1.logloss(), mm2.logloss(), 1e-6);

      } finally {
        if (m1 !=null) m1.delete();
        if (m2!=null) m2.delete();
        if (tr!=null) tr.remove();
        if (pred!=null) pred.remove();
      }
    }
  }
}

