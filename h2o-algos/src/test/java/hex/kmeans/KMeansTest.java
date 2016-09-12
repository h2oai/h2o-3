package hex.kmeans;

import hex.ModelMetrics;
import hex.ModelMetricsClustering;
import hex.SplitFrame;
import org.junit.*;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;
import water.util.ArrayUtils;
import water.util.FrameUtils;
import water.util.Log;
import water.util.MathUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class KMeansTest extends TestUtil {
  public final double threshold = 1e-6;
  @BeforeClass() public static void setup() { stall_till_cloudsize(5); }

  // Run KMeans with a given seed, & check all clusters are non-empty
  private static KMeansModel doSeed( KMeansModel.KMeansParameters parms, long seed ) {
    parms._seed = seed;
    KMeans job = new KMeans(parms);
    KMeansModel kmm = job.trainModel().get();
    checkConsistency(kmm);
    for( int i=0; i<parms._k; i++ )
      Assert.assertTrue( "Seed: "+seed, kmm._output._size[i] != 0 );
    return kmm;
  }

  //PUBDEV-871: Double-check the training metrics (gathered by computeStatsFillModel) and the scoring logic by scoring on the training set
  private static void checkConsistency(KMeansModel kmm) {
    //FIXME: TODO: remove this false, and fix the algo! PUBDEV-871
    if (false) {
      KMeansModel.KMeansParameters parms = kmm._parms;
      Assert.assertTrue((ArrayUtils.sum(kmm._output._size) - parms.train().numRows()) <= 1);

//    Log.info(kmm._output._model_summary);
//    Log.info(kmm._output._scoring_history);
//    Log.info(((ModelMetricsClustering)kmm._output._training_metrics).createCentroidStatsTable().toString());
      kmm.score(parms.train()).delete(); //this scores on the training data and appends a ModelMetrics
      ModelMetricsClustering mm = (ModelMetricsClustering) ModelMetrics.getFromDKV(kmm, parms.train());
      Assert.assertTrue(Arrays.equals(mm._size, ((ModelMetricsClustering) kmm._output._training_metrics)._size));
      for (int i = 0; i < parms._k; ++i) {
        Assert.assertTrue(MathUtils.compare(mm._withinss[i], ((ModelMetricsClustering) kmm._output._training_metrics)._withinss[i], 1e-6, 1e-6));
      }
      Assert.assertTrue(MathUtils.compare(mm._totss, ((ModelMetricsClustering) kmm._output._training_metrics)._totss, 1e-6, 1e-6));
      Assert.assertTrue(MathUtils.compare(mm._betweenss, ((ModelMetricsClustering) kmm._output._training_metrics)._betweenss, 1e-6, 1e-6));
      Assert.assertTrue(MathUtils.compare(mm._tot_withinss, ((ModelMetricsClustering) kmm._output._training_metrics)._tot_withinss, 1e-6, 1e-6));
    }
  }

  @Test public void testIris() {
    KMeansModel kmm = null;
    Frame fr = null, fr2= null;
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");

      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._train = fr._key;
      parms._k = 3;
      parms._standardize = true;
      parms._max_iterations = 10;
      parms._init = KMeans.Initialization.Random;
      kmm = doSeed(parms,0);

      // Iris last column is categorical; make sure centers are ordered in the
      // same order as the iris columns.
      double[/*k*/][/*features*/] centers = kmm._output._centers_raw;
      for( int k=0; k<parms._k; k++ ) {
        double flower = centers[k][4];
        Assert.assertTrue("categorical column expected",flower==(int)flower);
      }

      // Done building model; produce a score column with cluster choices
      fr2 = kmm.score(fr);

    } finally {
      if( fr  != null ) fr.delete();
      if( fr2 != null ) fr2.delete();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void testArrests() {
    // Initialize using first 4 rows of USArrests
    Frame init = ArrayUtils.frame(ard(ard(13.2, 236, 58, 21.2),
                                      ard(10.0, 263, 48, 44.5),
                                      ard(8.1, 294, 80, 31.0),
                                      ard(8.8, 190, 50, 19.5)));

    // R k-means results for comparison
    double totssR = 355807.8216;
    double btwssR = 318155.162076;
    double[] wssR = new double[] {2546.350000, 6705.906667, 9136.642857, 19263.760000};
    double[][] centersR = ard(ard( 4.270000,  87.550000, 59.750000, 14.390000),
                              ard( 8.214286, 173.285714, 70.642857, 22.842857),
                              ard(11.766667, 257.916667, 68.416667, 28.933333),
                              ard(11.950000, 316.500000, 68.000000, 26.700000));
    Frame predR = ArrayUtils.frame(ar("predict"), ear(1, 1, 2, 0, 1, 0, 3, 1, 2, 0, 3, 3, 1, 3,
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

      Arrays.sort(kmm._output._withinss);
      assertArrayEquals(wssR, kmm._output._withinss, threshold);
      assertEquals(totssR, kmm._output._totss, threshold);
      assertEquals(btwssR, kmm._output._betweenss, threshold);

      // Done building model; produce a score column with cluster choices
      fr2 = kmm.score(fr);
      Assert.assertTrue(kmm.testJavaScoring(fr,fr2,1e-15));
      assertVecEquals(predR.vec(0), fr2.vec(0), threshold);
      fr2.delete();
    } finally {
      init .delete();
      predR.delete();
      if( fr  != null ) fr.delete();
      if( fr2 != null ) fr2.delete();
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
      if( fr  != null ) fr.delete();
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
      if( fr  != null ) fr.delete();
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
    Frame fr = ArrayUtils.frame(ard(d(1,0,0),d(0,1,0),d(0,0,1)));
    Frame fr2;
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

        fr2=kmm.score(fr);
        Assert.assertTrue(kmm.testJavaScoring(fr,fr2,1e-15));
        fr2.delete();
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
      if( fr  != null ) fr.delete();
    }
  }

  @Test public void test1Dimension() {
    Frame fr = ArrayUtils.frame(ard(d(1,0),d(0,0),d(-1,0),d(4,0),d(1,0),d(2,0),d(0,0),d(0,0)));
    Frame fr2=null;
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
        fr2=kmm.score(fr);
        Assert.assertTrue(kmm.testJavaScoring(fr,fr2,1e-15));
        fr2.delete();
        kmm.delete();
      }

    } finally {
      if( fr  != null ) fr.delete();
      if( fr2  != null) fr2.delete();
    }
  }

  // Negative test - expect to throw IllegalArgumentException
  @Test (expected = H2OModelBuilderIllegalArgumentException.class) public void testTooManyK() {
    Frame fr = ArrayUtils.frame(ard(d(1,0),d(0,0),d(1,0),d(2,0),d(0,0),d(0,0)));
    Frame fr2=null;
    KMeansModel kmm = null;
    KMeansModel.KMeansParameters parms;
    try {

      parms = new KMeansModel.KMeansParameters();
      parms._train = fr._key;
      parms._k = 10; //too high -> will throw
      kmm = doSeed(parms, System.nanoTime());
      fr2=kmm.score(fr);
      Assert.assertTrue(kmm.testJavaScoring(fr,fr2,1e-15));
      fr2.delete();

    } finally {
      if( fr  != null) fr.delete();
      if( fr2  != null) fr2.delete();
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
      parms._k = 3;
      parms._standardize = true;
      parms._max_iterations = 10;
      parms._init = KMeans.Initialization.Random;
      kmm = doSeed(parms,0);

      // Done building model; produce a score column with cluster choices
      fr2 = kmm.score(fr);
      Assert.assertTrue(kmm.testJavaScoring(fr,fr2,1e-15));
      fr2.delete();

    } finally {
      if( fr  != null ) fr.delete();
      if( fr2 != null ) fr2.delete();
      if( kmm != null ) kmm.delete();
    }
  }

  @Test public void testValidation() {
    KMeansModel kmm = null;
      for (boolean standardize : new boolean[]{true,false}) {
      Frame fr = null, fr2= null;
      Frame tr = null, te= null;
      try {
        fr = parse_test_file("smalldata/iris/iris_wheader.csv");

        SplitFrame sf = new SplitFrame(fr,new double[] { 0.5, 0.5 },new Key[] { Key.make("train.hex"), Key.make("test.hex")});
        // Invoke the job
        sf.exec().get();
        Key<Frame>[] ksplits = sf._destination_frames;
        tr = DKV.get(ksplits[0]).get();
        te = DKV.get(ksplits[1]).get();

        KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
        parms._train = ksplits[0];
        parms._valid = ksplits[1];
        parms._k = 3;
        parms._standardize = standardize;
        parms._max_iterations = 10;
        parms._init = KMeans.Initialization.Random;
        kmm = doSeed(parms, 0);

        // Iris last column is categorical; make sure centers are ordered in the
        // same order as the iris columns.
        double[/*k*/][/*features*/] centers = kmm._output._centers_raw;
        for( int k=0; k<parms._k; k++ ) {
          double flower = centers[k][4];
          Assert.assertTrue("categorical column expected",flower==(int)flower);
        }

        // Done building model; produce a score column with cluster choices
        fr2 = kmm.score(te);
        Assert.assertTrue(kmm.testJavaScoring(te,fr2,1e-15));
        fr2.delete();
        tr .delete();
        te .delete();

      } finally {
        if( fr  != null ) fr.delete();
        if( fr2 != null ) fr2.delete();
        if( tr  != null ) tr .delete();
        if( te  != null ) te .delete();
        if( kmm != null ) kmm.delete();
      }
    }
  }

  @Ignore
  @Test public void testValidationSame() {
    for (boolean categorical : new boolean[]{true,false}) {
      for (boolean missing : new boolean[]{/*true,*/false}) { //FIXME: Enable missing PUBDEV-871
        for (boolean standardize : new boolean[]{true,false}) {
          Log.info("categorical: " + categorical);
          Log.info("missing: " + missing);
          Log.info("standardize: " + standardize);
          KMeansModel kmm = null;
          Frame fr = null, fr2= null;
          Frame train = null, valid = null;
          try {
            fr = parse_test_file("smalldata/iris/iris_wheader.csv");

            if (missing) {
              // insert 10% missing values - check the math
              FrameUtils.MissingInserter mi = new FrameUtils.MissingInserter(fr._key, 1234, 0.1f);
              fr = mi.execImpl().get();
            }
            train = new Frame(Key.<Frame>make("train"), fr.names(), fr.vecs());
            DKV.put(train);
            valid = new Frame(Key.<Frame>make("valid"), fr.names(), fr.vecs());
            DKV.put(valid);

            KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
            parms._train = train._key;
            parms._valid = valid._key;
            if (!categorical) {
              parms._ignored_columns = new String[]{fr._names[4]};
            }
            parms._k = 3;
            parms._standardize = standardize;
            parms._max_iterations = 10;
            parms._init = KMeans.Initialization.PlusPlus;
            kmm = doSeed(parms, 0);

            if (categorical) {
              // Iris last column is categorical; make sure centers are ordered in the
              // same order as the iris columns.
              double[/*k*/][/*features*/] centers = kmm._output._centers_raw;
              for( int k=0; k<parms._k; k++ ) {
                double flower = centers[k][4];
                Assert.assertTrue("categorical column expected",flower==(int)flower);
              }
            }

            Assert.assertTrue(
                    MathUtils.compare(
                            ((ModelMetricsClustering) kmm._output._training_metrics)._totss,
                            ((ModelMetricsClustering) kmm._output._validation_metrics)._totss,
                            1e-6, 1e-6)
                );

            Assert.assertTrue(
                    MathUtils.compare(
                            ((ModelMetricsClustering) kmm._output._training_metrics)._betweenss,
                            ((ModelMetricsClustering) kmm._output._validation_metrics)._betweenss,
                            1e-6, 1e-6)
            );

            Assert.assertTrue(
                    MathUtils.compare(
                            ((ModelMetricsClustering) kmm._output._training_metrics)._tot_withinss,
                            ((ModelMetricsClustering) kmm._output._validation_metrics)._tot_withinss,
                            1e-6, 1e-6)
            );

            for (int i=0; i<parms._k; ++i) {
              Assert.assertTrue(
                      MathUtils.compare(
                              ((ModelMetricsClustering) kmm._output._training_metrics)._withinss[i],
                              ((ModelMetricsClustering) kmm._output._validation_metrics)._withinss[i],
                              1e-6, 1e-6)
              );
              Assert.assertEquals(
                  ((ModelMetricsClustering) kmm._output._training_metrics)._size[i],
                  ((ModelMetricsClustering) kmm._output._validation_metrics)._size[i]
              );
            }

            // Done building model; produce a score column with cluster choices
            fr2 = kmm.score(fr);
            Assert.assertTrue(kmm.testJavaScoring(fr, fr2, 1e-15));
            fr.delete();
            fr2.delete();
            train.delete();
            valid.delete();
          } finally {
            if( fr  != null ) fr .delete();
            if( fr2 != null ) fr2.delete();
            if( train != null ) train.delete();
            if( valid != null ) valid.delete();
            if( kmm != null ) kmm.delete();
          }
        }
      }
    }
  }

  double _ref_betweenss = 429.75370357154713;
  double _ref_tot_withinss = 266.24628336259855;
  double _ref_totss = 695.9999869341457;
  double _ref_withinss[] = new double[]{ 195.73312749536535, 17.291967560290328, 27.73183120896519};
  long _ref_size[] = new long[]{96, 32, 22};

  @Test
  public void testNfolds() {
    Frame tfr = null, vfr = null;
    KMeansModel kmeans = null;

    Scope.enter();
    try {
      tfr = parse_test_file("smalldata/iris/iris_wheader.csv");
      DKV.put(tfr);
      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._train = tfr._key;
      parms._seed = 0xdecaf;
      parms._k = 3;
      parms._nfolds = 3;

      // Build a first model; all remaining models should be equal
      KMeans job = new KMeans(parms);
      kmeans = job.trainModel().get();
      checkConsistency(kmeans);

      ModelMetricsClustering mm = (ModelMetricsClustering)kmeans._output._cross_validation_metrics;
      assertEquals(_ref_betweenss, mm._betweenss, 1e-8);
      assertEquals(_ref_tot_withinss, mm._tot_withinss, 1e-8);
      assertEquals(_ref_totss, mm._totss, 1e-6);
      for (int i=0; i<parms._k; ++i) {
        Assert.assertTrue(
                MathUtils.compare(
                        ((ModelMetricsClustering) kmeans._output._training_metrics)._withinss[i],
                        _ref_withinss[i],
                        1e-6, 1e-6)
        );
        Assert.assertEquals(
                ((ModelMetricsClustering) kmeans._output._training_metrics)._size[i],
                _ref_size[i]
        );
      }
    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (kmeans != null) {
        kmeans.deleteCrossValidationModels();
        kmeans.delete();
      }
      Scope.exit();
    }
  }

}
