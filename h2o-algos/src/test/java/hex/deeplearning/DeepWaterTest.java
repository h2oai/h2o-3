package hex.deeplearning;

import javax.imageio.ImageIO;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.gpu.ImageIter;
import water.gpu.ImagePred;
import water.gpu.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class DeepWaterTest extends TestUtil {
  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  @Test
  public void inceptionPrediction() throws IOException {

    // load the cuda lib in CUDA_PATH, optional. theoretically we can find them if they are in LD_LIBRARY_PATH
    util.loadCudaLib();
    util.loadNativeLib("mxnet");
    util.loadNativeLib("Native");

    BufferedImage img = ImageIO.read(new File("/home/arno/deepwater/test/test1.jpg"));

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
    m.setModelPath("/home/arno/deepwater/Inception");

    m.loadInception();

    System.out.println("\n\n" + m.predict(pixels)+"\n\n");
  }

  @Test
  public void inceptionFineTuning() throws IOException {

        util.loadCudaLib();
        util.loadNativeLib("mxnet");
        util.loadNativeLib("Native");

        BufferedReader br = new BufferedReader(new FileReader(new File("/home/ops/Desktop/sf1_train.lst")));

        String line = null;

        ArrayList<Float> label_lst = new ArrayList<>();
        ArrayList<String> img_lst = new ArrayList<>();

        while ((line = br.readLine()) != null) {
            String[] tmp = line.split("\t");

            label_lst.add(new Float(tmp[1]).floatValue());
            img_lst.add(tmp[2]);
        }

        br.close();

        int batch_size = 40;

        int max_iter = 10;

        ImageClassify m = new ImageClassify();

        m.buildNet(10, batch_size);

        ImageIter img_iter = new ImageIter(img_lst, label_lst, batch_size, 224, 224);

        for (int iter = 0; iter < max_iter; iter++) {
            img_iter.Reset();
            while(img_iter.Nest()){
                float[] data = img_iter.getData();
                float[] labels = img_iter.getLabel();
                float[] pred = m.train(data, labels, true);
                System.out.println("pred " + pred.length);

                for (int i = 0; i < batch_size * 10; i++) {
                    System.out.print(pred[i] + " ");
                }
                System.out.println();
            }
        }


  }


}

