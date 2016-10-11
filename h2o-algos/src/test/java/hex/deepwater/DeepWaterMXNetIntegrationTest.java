package hex.deepwater;

import deepwater.backends.BackendModel;
import deepwater.backends.BackendParams;
import deepwater.backends.RuntimeOptions;
import deepwater.datasets.ImageDataSet;
import org.junit.*;
import water.parser.BufferedString;
import water.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static hex.genmodel.algos.DeepWaterMojo.createDeepWaterBackend;

public class DeepWaterMXNetIntegrationTest extends DeepWaterAbstractIntegrationTest {
  @Before
  public void setUp() throws Exception {
    backend = createDeepWaterBackend(DeepWaterParameters.Backend.mxnet.toString());
  }
  @BeforeClass
  static public void checkBackend() {
    Assume.assumeTrue(DeepWater.haveBackend(DeepWaterParameters.Backend.mxnet));
   }

  // This test has nothing to do with H2O - Pure integration test of deepwater/backends/mxnet
  // FIXME: push this to the actual deepwater.backends.mxnet.test module
  @Ignore
  @Test
  public void inceptionPredictionMX() throws IOException {
    for (boolean gpu : new boolean[]{true, false}) {

      // Set model parameters
      int w = 224, h = 224, channels = 3;
      ImageDataSet id = new ImageDataSet(w,h,channels);
      RuntimeOptions opts = new RuntimeOptions();
      opts.setSeed(1234);
      opts.setUseGPU(gpu);
      BackendParams bparm = new BackendParams();
      bparm.set("mini_batch_size", 1);

      // Load the model
//    File file = new File(getClass().getClassLoader().getResource("deepwater/backends/mxnet/models/Inception/synset.txt").getFile()); //FIXME: Use the model in the resource
      String path = "../deepwater/mxnet/src/main/resources/deepwater/backends/mxnet/models/Inception/";
      BackendModel _model = backend.buildNet(id, opts, bparm, 1000, StringUtils.expandPath(path + "Inception_BN-symbol.json"));
      backend.loadParam(_model, StringUtils.expandPath(path + "Inception_BN-0039.params"));
      water.fvec.Frame labels = parse_test_file(path + "synset.txt");
      float[] mean = backend.loadMeanImage(_model, path + "mean_224.nd");

      // Turn the image into a vector of the correct size
      File imgFile = find_test_file("smalldata/deepwater/imagenet/test2.jpg");
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
      String expected =
      "\n" +
          "Top 5 predictions:\n" +
          " Score: 0.6647\tn02113023 Pembroke\n" +
          " Score: 0.0309\tn02112018 Pomeranian\n" +
          " Score: 0.0179\tn02115641 dingo\n" +
          " Score: 0.0028\tn03803284 muzzle\n" +
          " Score: 0.0018\tn02342885 hamster\n";
      Assert.assertTrue("Illegal predictions!", expected.equals(sb.toString()));
      labels.remove();
    }
  }
}
