package hex.deeplearning;

import javax.imageio.ImageIO;

import hex.ModelMetricsMultinomial;
import org.junit.BeforeClass;
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
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class DeepWaterTest extends TestUtil {
  @BeforeClass
  public static void stall() { stall_till_cloudsize(1); }

  final String myhome = "/home/arno/";
  final boolean GPU = true;

  @Test
  public void inceptionPrediction() throws IOException {

    // load the cuda lib in CUDA_PATH, optional. theoretically we can find them if they are in LD_LIBRARY_PATH
    if (GPU) util.loadCudaLib();
    util.loadNativeLib("mxnet");
    util.loadNativeLib("Native");

    BufferedImage img = ImageIO.read(new File(myhome + "/deepwater/test/test2.jpg"));

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
    m.setModelPath(myhome + "/deepwater/Inception");

    m.loadInception();

    System.out.println("\n\n" + m.predict(pixels)+"\n\n");
  }

  @Test
  public void inceptionFineTuning() throws IOException {
      if (GPU) util.loadCudaLib();
      util.loadNativeLib("mxnet");
      util.loadNativeLib("Native");

      String path = myhome + "/kaggle/statefarm/input/";
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

          ImageIter img_iter = new ImageIter(train_data, train_labels, batch_size, 224, 224);
          while(img_iter.Next()){
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
        int batch_size = 1; //avoid issues with batching at the end of the test set
        ImageIter img_iter = new ImageIter(test_data, test_labels, batch_size, 224, 224);
        fw.write("img,c0,c1,c2,c3,c4,c5,c6,c7,c8,c9\n");
        while(img_iter.Next()) {
            float[] data = img_iter.getData();
            float[] labels = img_iter.getLabel();
            String[] files = img_iter.getFiles();
            float[] pred = m.predict(data, labels);
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
}

