package water.codegen.java;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import water.Key;
import water.codegen.driver.CodeGenDriver;
import water.codegen.driver.DirectOutputDriver;
import water.codegen.driver.ZipOutputDriver;
import water.fvec.Frame;

/**
 * Created by michal on 3/21/16.
 */
public class KMeansModelJCodeGenTest extends water.TestUtil {

  static final String IRIS_PATH = "smalldata/iris/iris_wheader.csv";

  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }

  @Test public void simpleModel() throws IOException {
    Frame fr = null;
    KMeansModel model = null;
    try {
      final Frame f = fr = parse_test_file(IRIS_PATH);

      KMeansModel.KMeansParameters params = new KMeansModel.KMeansParameters() {{
        _train = f._key;
        _ignored_columns = new String[] {"class"};
        _k = 3;
        _standardize = true;
        _max_iterations = 10;
        _init = KMeans.Initialization.Random;
      }};

      model = new KMeans(params).trainModel().get();
      //CodeGenDriver driver = new ZipOutputDriver();
      CodeGenDriver driver = new DirectOutputDriver();
      FileOutputStream fos = new FileOutputStream(new File("/tmp/testmodel.java"));
      try {
        // FIXME: i cannot switch the driver without switching a target
        // Probably builder patter would be better here
        driver.codegen(new KMeansModelJCodeGen(model).build(), fos);
      } finally {
        fos.close();
      }
      //model.toJava(System.err, false, true);

    } finally {
      if (fr != null) fr.delete();
      if (model != null) model.delete();
    }
  }
}