package hex.kmeans;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.*;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;

public class KMeansTest extends TestUtil {
  public final double threshold = 1e-6;
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  
  // Run KMeans with a given seed, & check all clusters are non-empty
  private static KMeansModel doSeed( KMeansModel.KMeansParameters parms, long seed ) {
    parms._seed = seed;
    KMeans job = null;
    KMeansModel kmm = null;
    try {
      job = new KMeans(parms);
      kmm = job.trainModel().get();
    } finally {
      if (job != null) job.remove();
    }
    for( int i=0; i<parms._k; i++ )
      Assert.assertTrue( "Seed: "+seed, kmm._output._size[i] != 0 );
    return kmm;
  }

  @Test public void testIris() {
    KMeansModel kmm = null;
    Frame fr = null, fr2= null;
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");

      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._train = fr._key;
      parms._ignored_columns = new String[] {"class"};
      parms._k = 3;
      parms._standardize = true;
      parms._max_iterations = 10;
      parms._init = KMeans.Initialization.Random;
      kmm = doSeed(parms,0);

      // Done building model; produce a score column with cluster choices
      fr2 = kmm.score(fr);

    } finally {
      if( fr  != null ) fr .remove();
      if( fr2 != null ) fr2.remove();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void testArrests() {
    // Initialize using first 4 rows of USArrests
    Frame init = frame(ard(ard(13.2, 236, 58, 21.2),
                           ard(10.0, 263, 48, 44.5),
                           ard( 8.1, 294, 80, 31.0),
                           ard( 8.8, 190, 50, 19.5)));

    // R k-means results for comparison
    double totssR = 355807.821599;
    double btwssR = 318155.162076;
    double[] wmseR = new double[] {558.825556, 636.587500, 652.617347, 963.188000};
    double[][] centersR = ard(ard( 4.270000,  87.550000, 59.750000, 14.390000),
                              ard( 8.214286, 173.285714, 70.642857, 22.842857),
                              ard(11.766667, 257.916667, 68.416667, 28.933333),
                              ard(11.950000, 316.500000, 68.000000, 26.700000));
    Frame predR = frame(ar("predict"), ear(1, 1, 2, 0, 1, 0, 3, 1, 2, 0, 3, 3, 1, 3,
                                           3, 3, 3, 1, 3, 2, 0, 1, 3, 1, 0, 3, 3, 1,
                                           3, 0, 1, 1, 2, 3, 3, 0, 0, 3, 0, 1, 3, 0,
                                           0, 3, 3, 0, 0, 3, 3, 0));

    KMeansModel kmm = null;
    Frame fr = null, fr2 = null;
    try {
      fr = parse_test_file("smalldata/pca_test/USArrests.csv");

      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._train = fr._key;
      parms._k = 4;
      parms._standardize = false;
      parms._init = KMeans.Initialization.User;
      parms._user_points = init._key;
      kmm = doSeed(parms, 0);

      // Sort cluster centers by first column for comparison purposes
      double[][] centers = new double[parms._k][];
      for(int i = 0; i < parms._k; i++)
        centers[i] = kmm._output._centers_raw[i].clone();
      Arrays.sort(centers, new Comparator<double[]>() {
        @Override
        public int compare(final double[] a, final double[] b) {
          Double d1 = a[0]; Double d2 = b[0];
          return d1.compareTo(d2);
        }
      });
      for(int i = 0; i < centers.length; i++)
        assertArrayEquals(centersR[i], centers[i], threshold);

      Arrays.sort(kmm._output._within_mse);
      assertArrayEquals(wmseR, kmm._output._within_mse, threshold);
      assertEquals(totssR / fr.numRows(), kmm._output._avg_ss, threshold);
      assertEquals(btwssR / fr.numRows(), kmm._output._avg_between_ss, threshold);

      // Done building model; produce a score column with cluster choices
      fr2 = kmm.score(fr);
      assertVecEquals(predR.vec(0), fr2.vec(0), threshold);
    } finally {
      init .delete();
      predR.delete();
      if( fr  != null ) fr .remove();
      if( fr2 != null ) fr2.remove();
      if( kmm != null ) kmm.delete();
    }
  }


  @Test public void testBadCluster() {
    Frame fr = null;
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");

      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._train = fr._key;
      parms._ignored_columns = new String[] {"class"};
      parms._k = 3;
      parms._standardize = true;
      parms._max_iterations = 10;
      parms._init = KMeans.Initialization.Random;

      doSeed(parms,341534765239617L).delete(); // Seed triggers an empty cluster on iris
      doSeed(parms,341579128111283L).delete(); // Seed triggers an empty cluster on iris
      for( int i=0; i<10; i++ )
        doSeed(parms,System.nanoTime()).delete();

    } finally {
      if( fr  != null ) fr .remove();
    }
  }

  // "datasets directory not always available"
  @Test @Ignore public void testCovtype() {
    Frame fr = null;
    try {
      File f = find_test_file("../datasets/UCI/UCI-large/covtype/covtype.data");
      if( f==null ) return;     // Ignore if large file not found
      NFSFileVec nfs = NFSFileVec.make(f);
      fr = ParseDataset.parse(Key.make(), nfs._key);

      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._train = fr._key;
      parms._k = 7;
      parms._standardize = true;
      parms._max_iterations = 100;
      parms._init = KMeans.Initialization.Random;

      for( int i=0; i<10; i++ )
        doSeed(parms,System.nanoTime()).delete();

    } finally {
      if( fr  != null ) fr .remove();
    }
  }

  private double[] d(double... ds) { return ds; }

  boolean close(double[] a, double[] b) {
    for (int i=0;i<a.length;++i) {
      if (Math.abs(a[i]-b[i]) > 1e-8) return false;
    }
    return true;
  }

  @Test
  public void testCentroids(){
    Frame fr = frame(ard(d(1,0,0),d(0,1,0),d(0,0,1)));
    try {
      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._train = fr._key;
      parms._k = 3;
      parms._standardize = true;
      parms._max_iterations = 100;
      parms._init = KMeans.Initialization.Random;

      double[][] exp1 = new double[][]{ d(1, 0, 0), d(0, 1, 0), d(0, 0, 1), };
      double[][] exp2 = new double[][]{ d(0, 1, 0), d(1, 0, 0), d(0, 0, 1), };
      double[][] exp3 = new double[][]{ d(0, 1, 0), d(0, 0, 1), d(1, 0, 0), };
      double[][] exp4 = new double[][]{ d(1, 0, 0), d(0, 0, 1), d(0, 1, 0), };
      double[][] exp5 = new double[][]{ d(0, 0, 1), d(1, 0, 0), d(0, 1, 0), };
      double[][] exp6 = new double[][]{ d(0, 0, 1), d(0, 1, 0), d(1, 0, 0), };

      for( int i=0; i<10; i++ ) {
        KMeansModel kmm = doSeed(parms, System.nanoTime());
        Assert.assertTrue(kmm._output._centers_raw.length == 3);

        boolean gotit = false;
        for (int j = 0; j < parms._k; ++j) gotit |= close(exp1[j], kmm._output._centers_raw[j]);
        for (int j = 0; j < parms._k; ++j) gotit |= close(exp2[j], kmm._output._centers_raw[j]);
        for (int j = 0; j < parms._k; ++j) gotit |= close(exp3[j], kmm._output._centers_raw[j]);
        for (int j = 0; j < parms._k; ++j) gotit |= close(exp4[j], kmm._output._centers_raw[j]);
        for (int j = 0; j < parms._k; ++j) gotit |= close(exp5[j], kmm._output._centers_raw[j]);
        for (int j = 0; j < parms._k; ++j) gotit |= close(exp6[j], kmm._output._centers_raw[j]);
        Assert.assertTrue(gotit);

        kmm.delete();
      }

    } finally {
      if( fr  != null ) fr.remove();
    }
  }

  @Test public void test1Dimension() {
    Frame fr = frame(ard(d(1,0),d(0,0),d(-1,0),d(4,0),d(1,0),d(2,0),d(0,0),d(0,0)));
    try {
      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._train = fr._key;
      parms._k = 2;
      parms._standardize = true;
      parms._max_iterations = 100;
      parms._init = KMeans.Initialization.Furthest;

      for( int i=0; i<10; i++ ) {
        KMeansModel kmm = doSeed(parms, System.nanoTime());
        Assert.assertTrue(kmm._output._centers_raw.length == 2);
        kmm.delete();
      }

    } finally {
      if( fr  != null ) fr .remove();
    }
  }

  // Negative test - expect to throw IllegalArgumentException
  @Test (expected = IllegalArgumentException.class) public void testTooManyK() {
    Frame fr = frame(ard(d(1,0),d(0,0),d(1,0),d(2,0),d(0,0),d(0,0)));
    KMeansModel kmm = null;
    KMeansModel.KMeansParameters parms;
    try {

      parms = new KMeansModel.KMeansParameters();
      parms._train = fr._key;
      parms._k = 10; //too high -> will throw
      kmm = doSeed(parms, System.nanoTime());

    } finally {
      if( fr  != null) fr .remove();
      if( kmm != null) kmm.delete();
    }
  }


  @Test public void testPOJO() {
    // Ignore test if the compiler failed to load
    Assume.assumeTrue(water.util.JCodeGen.canCompile());

    KMeansModel kmm = null;
    Frame fr = null, fr2= null;
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");
      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._train = fr._key;
      parms._ignored_columns = new String[] {"class"};
      parms._k = 3;
      parms._standardize = true;
      parms._max_iterations = 10;
      parms._init = KMeans.Initialization.Random;
      kmm = doSeed(parms,0);

      // Done building model; produce a score column with cluster choices
      fr2 = kmm.score(fr);

      Assert.assertTrue(kmm.testJavaScoring(fr,fr2));

    } finally {
      if( fr  != null ) fr .remove();
      if( fr2 != null ) fr2.remove();
      if( kmm != null ) kmm.delete();
    }
  }
}
