package hex.deepwater;

import hex.genmodel.algos.deepwater.caffe.DeepwaterCaffeModel;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Random;
import java.util.zip.GZIPInputStream;

public class DeepWaterCaffeIntegrationTest extends DeepWaterAbstractIntegrationTest {

  @Override
  DeepWaterParameters.Backend getBackend() { return DeepWaterParameters.Backend.caffe; }

  @BeforeClass
  public static void checkBackend() { Assume.assumeTrue(DeepWater.haveBackend(DeepWaterParameters.Backend.caffe)); };

  @Ignore
  @Test
  public void run() throws Exception {
    /*
      MNIST demo. Get the data first in your home folder:
      cd
      wget http://yann.lecun.com/exdb/mnist/train-images-idx3-ubyte.gz
      wget http://yann.lecun.com/exdb/mnist/train-labels-idx1-ubyte.gz
    */
    final int PIXELS = 28 * 28;
    String home = System.getProperty("user.home");
    DataInputStream pixels = new DataInputStream(new GZIPInputStream(new FileInputStream(new File(
        home + "/train-images-idx3-ubyte.gz"))));
    DataInputStream labels = new DataInputStream(new GZIPInputStream(new FileInputStream(new File(
        home + "/train-labels-idx1-ubyte.gz"))));

    pixels.readInt(); // Magic
    int count = pixels.readInt();
    pixels.readInt(); // Rows
    pixels.readInt(); // Cols

    labels.readInt(); // Magic
    labels.readInt(); // Count

    System.out.println("Read " + count + " samples");
    byte[][] rawI = new byte[count][PIXELS];
    byte[] rawL = new byte[count];
    for (int i = 0; i < count; i++) {
      pixels.readFully(rawI[i]);
      rawL[i] = labels.readByte();
    }

    System.out.println("Randomize");
    Random rand = new Random();
    for (int i = count - 1; i >= 0; i--) {
      int shuffle = rand.nextInt(i + 1);
      byte[] image = rawI[shuffle];
      rawI[shuffle] = rawI[i];
      rawI[i] = image;
      byte label = rawL[shuffle];
      rawL[shuffle] = rawL[i];
      rawL[i] = label;
    }

    System.out.println("Create model");
    final int batch = 256;
    DeepwaterCaffeModel model = new DeepwaterCaffeModel(
        batch,
        new int[] {PIXELS, 4024, 4024, 4048, 10},
        new String[] {"data", "relu", "relu", "relu", "loss"},
        new double[] {.9, .5, .5, .5, 0.},
        1234,
        true // GPU
    );

    System.out.println("Train");

    float[] ps = new float[batch * PIXELS];
    float[] ls = new float[batch];
    for (int iter = 0; iter < 10; iter++) {
      for (int b = 0; b < batch; b++) {
        for (int i = 0; i < PIXELS; i++)
          ps[b * PIXELS + i] = (rawI[b][i] & 0xff) * 0.00390625f;
        ls[b] = rawL[b];
      }
      model.train(ps, ls);
      model.predict(ps);
    }

    model.saveModel("/tmp/graph");
    model.saveParam("/tmp/params");
    model.loadParam("/tmp/params");
 }
}
