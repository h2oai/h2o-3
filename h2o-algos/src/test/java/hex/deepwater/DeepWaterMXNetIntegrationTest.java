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
        pixels[r_idx] = red-117; r_idx++; //FIXME: Hardcoded mean image RGB values
        pixels[g_idx] = green-117; g_idx++;
        pixels[b_idx] = blue-117; b_idx++;
      }
    }

    // the path to Inception model
    //ImageTrain m = new ImageTrain(224,224,3);
    //m.buildNet(1000,1,"inception_bn");
    ImageDataSet id = new ImageDataSet(224,224,3);

    RuntimeOptions opts = new RuntimeOptions();
    opts.setSeed(1234);
    opts.setUseGPU(false); //FIXME: Fails when set to true (i.e., running on the GPU) due to BatchNormalization issues?

    BackendParams bparm = new BackendParams();
    bparm.set("mini_batch_size", 1);

    //FIXME: make the path a resource of the package
    BackendModel _model = backend.buildNet(id, opts, bparm, 1000, StringUtils.expandPath("~/deepwater/mxnet/src/main/resources/deepwater/backends/mxnet/models/Inception/Inception_BN-symbol.json"));
    backend.loadParam(_model, StringUtils.expandPath("~/deepwater/mxnet/src/main/resources/deepwater/backends/mxnet/models/Inception/Inception_BN-0039.params"));

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


    water.fvec.Frame labels = parse_test_file("~/deepwater/mxnet/src/main/resources/deepwater/backends/mxnet/models/Inception/synset.txt");

    StringBuilder sb = new StringBuilder();
    sb.append("\nTop " + K + " predictions:\n");
    BufferedString str = new BufferedString();
    for ( int j = 0; j < K; j++ ) {
      String label = labels.anyVec().atStr(str, topK[j]).toString();
      sb.append(" Score: " + String.format("%.4f", preds[topK[j]]) + "\t" + label + "\n");
    }

    System.out.println("\n\n" + sb.toString() +"\n\n");
    labels.remove();
  }
}
