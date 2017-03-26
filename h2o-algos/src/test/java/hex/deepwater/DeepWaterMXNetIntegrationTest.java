package hex.deepwater;

import deepwater.backends.BackendModel;
import deepwater.backends.BackendParams;
import deepwater.backends.RuntimeOptions;
import deepwater.datasets.ImageDataSet;
import org.junit.*;
import water.parser.BufferedString;
import water.util.FileUtils;
import water.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Paths;

public class DeepWaterMXNetIntegrationTest extends DeepWaterAbstractIntegrationTest {

  static long copy(InputStream var0, OutputStream var1) throws IOException {
    byte[] var2 = new byte[4096];
    long var3 = 0L;

    while(true) {
      int var5 = var0.read(var2);
      if(var5 == -1) {
        return var3;
      }

      var1.write(var2, 0, var5);
      var3 += (long)var5;
    }
  }


  @Override
  DeepWaterParameters.Backend getBackend() { return DeepWaterParameters.Backend.mxnet; }

  @BeforeClass
  public static void checkBackend() { Assume.assumeTrue(DeepWater.haveBackend(DeepWaterParameters.Backend.mxnet)); }

  public static String extractFile(String path, String file) throws IOException {
    InputStream in = DeepWaterMXNetIntegrationTest.class.getClassLoader().getResourceAsStream(Paths.get(path, file).toString());
    String target = Paths.get(System.getProperty("java.io.tmpdir"), file).toString();
    OutputStream out = new FileOutputStream(target);
    copy(in, out);
    return target;
  }

  // This test has nothing to do with H2O - Pure integration test of deepwater/backends/mxnet
  @Test
  public void inceptionPredictionMX() throws IOException {
    for (boolean gpu : new boolean[]{true, false}) {

      // Set model parameters
      int w = 224, h = 224, channels = 3, nclasses=1000;
      ImageDataSet id = new ImageDataSet(w,h,channels,nclasses);
      RuntimeOptions opts = new RuntimeOptions();
      opts.setSeed(1234);
      opts.setUseGPU(gpu);
      BackendParams bparm = new BackendParams();
      bparm.set("mini_batch_size", 1);

      // Load the model
      String path = "deepwater/backends/mxnet/models/Inception/";
      BackendModel _model = backend.buildNet(id, opts, bparm, nclasses, StringUtils.expandPath(extractFile(path, "Inception_BN-symbol.json")));
      backend.loadParam(_model, StringUtils.expandPath(extractFile(path, "Inception_BN-0039.params")));
      water.fvec.Frame labels = parse_test_file(extractFile(path, "synset.txt"));
      float[] mean = backend.loadMeanImage(_model, extractFile(path, "mean_224.nd"));

      // Turn the image into a vector of the correct size
      File imgFile = FileUtils.getFile("smalldata/deepwater/imagenet/test2.jpg");
      BufferedImage img = ImageIO.read(imgFile);
      BufferedImage scaledImg = new BufferedImage(w, h, img.getType());
      Graphics2D g2d = scaledImg.createGraphics();
      g2d.drawImage(img, 0, 0, w, h, null);
      g2d.dispose();
      float[] pixels = new float[w * h * channels];
      int r_idx = 0;
      int g_idx = r_idx + w * h;
      int b_idx = g_idx + w * h;
      for (int i = 0; i < h; i++) {
        for (int j = 0; j < w; j++) {
          Color mycolor = new Color(scaledImg.getRGB(j, i));
          int red = mycolor.getRed();
          int green = mycolor.getGreen();
          int blue = mycolor.getBlue();
          pixels[r_idx] = red - mean[r_idx];
          r_idx++;
          pixels[g_idx] = green - mean[g_idx];
          g_idx++;
          pixels[b_idx] = blue - mean[b_idx];
          b_idx++;
        }
      }
      float[] preds = backend.predict(_model, pixels);
      int K = 5;
      int[] topK = new int[K];
      for ( int i = 0; i < preds.length; i++ ) {
        for ( int j = 0; j < K; j++ ) {
          if ( preds[i] > preds[topK[j]] ) {
            topK[j] = i;
            break;
          }
        }
      }

      // Display the top 5 predictions
      StringBuilder sb = new StringBuilder();
      sb.append("\nTop " + K + " predictions:\n");
      BufferedString str = new BufferedString();
      for ( int j = 0; j < K; j++ ) {
        String label = labels.anyVec().atStr(str, topK[j]).toString();
        sb.append(" Score: " + String.format("%.4f", preds[topK[j]]) + "\t" + label + "\n");
      }
      System.out.println("\n\n" + sb.toString() +"\n\n");
      Assert.assertTrue("Illegal predictions!", sb.toString().substring(40,60).contains("Pembroke"));
      labels.remove();
    }
  }

  @Ignore
  @Test
  public void PreTrainedMOJO() {
    water.fvec.Frame tr = null;
    water.fvec.Frame preds = null;
    DeepWaterModel m = null;
    try {
      DeepWaterParameters p = new DeepWaterParameters();
      //p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cc.csv"))._key;
      p._train = (tr=parse_test_file("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))._key;
      p._response_column = "C2";
//      p._problem_type = DeepWaterParameters.ProblemType.image_classification;
//      p._train.get().remove("C3");
//      for (String col : new String[]{p._train.get().name(0)}) {
//        Vec v = tr.remove(col);
//        tr.add(col, v.toStringVec());
//        v.remove();
//      }
//      for (String col : new String[]{p._response_column}) {
//        Vec v = tr.remove(col);
//        tr.add(col, v.toCategoricalVec());
//        v.remove();
//      }
      String path = "../deepwater/mxnet/src/main/resources/deepwater/backends/mxnet/models/Inception/";
//      p._network = DeepWaterParameters.Network.user;
      p._image_shape = new int[]{224, 224};
      p._channels = 3;
      p._network_definition_file = path + "Inception_BN-symbol.json"; //TODO: allow loading this 1000-class graph for this 3-class problem
      p._network_parameters_file = path + "Inception_BN-0039.params"; //TODO: allow loading this parameter file for the 3-class modified graph
      p._mean_image_file         = path + "mean_224.nd";
      p._epochs = 0.1; //just make a model, no training needed
      p._learning_rate = 0; //just make a model, no training needed
      DeepWater j = new DeepWater(p);
      m = j.trainModel().get();
      preds = m.score(p._train.get());
      Assert.assertTrue(m.testJavaScoring(p._train.get(),preds,1e-3));
    } finally {
      if (tr!=null) tr.remove();
      if (preds!=null) preds.remove();
      if (m!=null) m.remove();
    }
  }
}
