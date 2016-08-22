package hex.deepwater;

import hex.ModelMetricsMultinomial;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Futures;
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
import java.util.Collections;
import java.util.Random;

public class DeepWaterTest extends TestUtil {
  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  final boolean GPU = System.getenv("CUDA_PATH")!=null;

  static String expandPath(String path) {
    return path.replaceFirst("^~", System.getProperty("user.home"));
  }

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

      DeepWaterImageIterator img_iter = new DeepWaterImageIterator(train_data, train_labels, null /*do not subtract mean*/, batch_size, 224, 224, 3);
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
    DeepWaterImageIterator img_iter = new DeepWaterImageIterator(test_data, test_labels, null /*do not subtract mean*/, batch_size, 224, 224, 3);
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
    int counter=100;
    while(counter-- > 0) {
      try {
        DeepWaterParameters p = new DeepWaterParameters();
        p._train = (tr=parse_test_file(expandPath("~/kaggle/statefarm/input/train.10.csv")))._key;
        p._response_column = "C2";
        p._network = DeepWaterParameters.Network.alexnet;
        p._mini_batch_size = 32;
        p._epochs = 1;
        m = new DeepWater(p).trainModel().get();
        Log.info(m);
      } finally {
        if (m!=null) m.deleteCrossValidationModels();
        if (m!=null) m.delete();
        if (tr!=null) tr.remove();
      }
    }
  }

  @Test
  public void deepWaterGrayScale() throws IOException {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file(expandPath("~/kaggle/statefarm/input/train.10.csv")))._key;
      p._response_column = "C2";
      p._mini_batch_size = 10;
      p._channels = 1;
      p._epochs = 1;
      m = new DeepWater(p).trainModel().get();
      Log.info(m);
    } finally {
      if (m!=null) m.deleteCrossValidationModels();
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  @Test
  public void deepWaterLoadSaveTest() throws IOException {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      p._train = (tr=parse_test_file(expandPath("~/kaggle/statefarm/input/train.10.csv")))._key;
      p._response_column = "C2";
      p._mini_batch_size = 10;
      p._epochs = 1;
      m = new DeepWater(p).trainModel().get();

      // exercise some work
      m.model_info().javaToNative();
      m.model_info().nativeToJava();

      Log.info(m);
    } finally {
      if (m!=null) m.deleteCrossValidationModels();
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }

  @Test
  public void deepWaterCV() throws IOException {
    DeepWaterModel m = null;
    Frame tr = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      // cat driver_imgs_list.csv | awk -F, '{print "/users/arno/kaggle/statefarm/input/train/"$2"/"$3,$2,$1}' \
      // | awk '{if ($3=="p002" || $3=="p042" || $3=="p075") print $1, $2, "A"; else print $1, $2, "B"}' | sed 1d | gshuf | head -n 10 > train.10.csv
      p._train = (tr=parse_test_file(expandPath("~/kaggle/statefarm/input/train.10.csv")))._key;
      p._response_column = "C2";
      p._fold_column = "C3";
      p._mini_batch_size = 5;
      p._epochs = 1;
      m = new DeepWater(p).trainModel().get();
      Log.info(m);
    } finally {
      if (m!=null) m.deleteCrossValidationModels();
      if (m!=null) m.delete();
      if (tr!=null) tr.remove();
    }
  }
}

