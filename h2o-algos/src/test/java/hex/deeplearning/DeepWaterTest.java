package hex.deeplearning;

import javax.imageio.ImageIO;

import hex.ModelMetricsMultinomial;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.TestUtil;
import water.fvec.*;
import water.fvec.Frame;
import water.gpu.ImageIter;
import water.gpu.ImagePred;
import water.gpu.ImageTrain;
import water.gpu.util;
import water.util.RandomUtils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class DeepWaterTest extends TestUtil {
  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  @Test
  public void inceptionPrediction() throws IOException {

    // load the cuda lib in CUDA_PATH, optional. theoretically we can find them if they are in LD_LIBRARY_PATH
    util.loadCudaLib();
    util.loadNativeLib("mxnet");
    util.loadNativeLib("Native");

    BufferedImage img = ImageIO.read(new File("/home/arno/deepwater/test/test2.jpg"));

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

      String path = "/home/arno/kaggle/statefarm/input/";
      BufferedReader br = new BufferedReader(new FileReader(new File(path+"driver_imgs_list.csv")));

      ArrayList<Float> label_lst = new ArrayList<>();
      ArrayList<String> img_lst = new ArrayList<>();

      String line;
      br.readLine(); //skip header
      while ((line = br.readLine()) != null) {
          String[] tmp = line.split(",");
          label_lst.add(new Float(tmp[1].substring(1)).floatValue());
          img_lst.add(path+"train/"+tmp[1]+"/"+tmp[2]);
      }

      br.close();



      int batch_size = 64;

      int max_iter = 100;

      ImageTrain m = new ImageTrain();

      int classes = 10;
      m.buildNet(classes, batch_size, "inception_bn");

      for (int iter = 0; iter < max_iter; iter++) {
          //each iteration does a different random shuffle
          Random rng = RandomUtils.getRNG(0);
          rng.setSeed(0xDECAF+0xD00D*iter);
          Collections.shuffle(label_lst,rng);
          rng.setSeed(0xDECAF+0xD00D*iter);
          Collections.shuffle(img_lst,rng);

          ImageIter img_iter = new ImageIter(img_lst, label_lst, batch_size, 224, 224);
          while(img_iter.Next()){
              float[] data = img_iter.getData();
              float[] labels = img_iter.getLabel();
              float[] pred = m.train(data, labels);

              Vec[] classprobs = new Vec[classes];
              String[] names = new String[classes];
              for (int i=0;i<classes;++i) {
                  names[i] = "c" + i;
                  double[] vals=new double[batch_size];
                  for (int j = 0; j < batch_size; ++j) {
//                      int idx=i*batch_size+j; //[p0,...,p9,p0,...,p9, ... ,p0,...,p9]
                      int idx=j*classes+i; //[p0,...,p0,p1,...,p1,p2,...,p2, ... ,p9,...,p9]
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
      }


  }


}

