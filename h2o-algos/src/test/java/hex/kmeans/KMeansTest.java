package hex.kmeans;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;

import java.io.File;

public class KMeansTest extends TestUtil {
  @BeforeClass() public static void setup() { stall_till_cloudsize(1); }
  
  // Run KMeans with a given seed, & check all clusters are non-empty
  private static KMeansModel doSeed( KMeansModel.KMeansParameters parms, long seed ) {
    parms._seed = seed;
    KMeans job = null;
    KMeansModel kmm = null;
    try {
      job = new KMeans(parms);
      job.train();
      kmm = job.get();
    } finally {
      if (job != null) job.remove();
    }
    for( int i=0; i<parms._K; i++ )
      Assert.assertTrue( "Seed: "+seed, kmm._output._rows[i] != 0 );
    return kmm;
  }

  @Test public void testIris() {
    KMeansModel kmm = null;
    Frame fr = null, fr2= null;
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");

      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._train = fr._key;
      parms._K = 3;
      parms._normalize = true;
      parms._max_iters = 10;
      parms._init = KMeans.Initialization.None;
      kmm = doSeed(parms,0);

      // Done building model; produce a score column with cluster choices
      fr2 = kmm.score(fr);

    } finally {
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
      parms._K = 3;
      parms._normalize = true;
      parms._max_iters = 10;
      parms._init = KMeans.Initialization.None;

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
      fr = water.parser.ParseDataset2.parse(Key.make(),nfs._key);

      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._train = fr._key;
      parms._K = 7;
      parms._normalize = true;
      parms._max_iters = 100;
      parms._init = KMeans.Initialization.None;

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
      parms._K = 3;
      parms._normalize = true;
      parms._max_iters = 100;
      parms._init = KMeans.Initialization.None;

      double[][] exp1 = new double[][]{ d(1, 0, 0), d(0, 1, 0), d(0, 0, 1), };
      double[][] exp2 = new double[][]{ d(0, 1, 0), d(1, 0, 0), d(0, 0, 1), };
      double[][] exp3 = new double[][]{ d(0, 1, 0), d(0, 0, 1), d(1, 0, 0), };
      double[][] exp4 = new double[][]{ d(1, 0, 0), d(0, 0, 1), d(0, 1, 0), };
      double[][] exp5 = new double[][]{ d(0, 0, 1), d(1, 0, 0), d(0, 1, 0), };
      double[][] exp6 = new double[][]{ d(0, 0, 1), d(0, 1, 0), d(1, 0, 0), };

      for( int i=0; i<10; i++ ) {
        KMeansModel kmm = doSeed(parms, System.nanoTime());
        Assert.assertTrue(kmm._output._clusters.length == 3);

        boolean gotit = false;
        for (int j = 0; j < parms._K; ++j) gotit |= close(exp1[j], kmm._output._clusters[j]);
        for (int j = 0; j < parms._K; ++j) gotit |= close(exp2[j], kmm._output._clusters[j]);
        for (int j = 0; j < parms._K; ++j) gotit |= close(exp3[j], kmm._output._clusters[j]);
        for (int j = 0; j < parms._K; ++j) gotit |= close(exp4[j], kmm._output._clusters[j]);
        for (int j = 0; j < parms._K; ++j) gotit |= close(exp5[j], kmm._output._clusters[j]);
        for (int j = 0; j < parms._K; ++j) gotit |= close(exp6[j], kmm._output._clusters[j]);
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
      parms._K = 2;
      parms._normalize = true;
      parms._max_iters = 100;
      parms._init = KMeans.Initialization.Furthest;

      for( int i=0; i<10; i++ ) {
        KMeansModel kmm = doSeed(parms, System.nanoTime());
        Assert.assertTrue(kmm._output._clusters.length == 2);
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
      parms._K = 10; //too high -> will throw
      parms._normalize = true;
      parms._max_iters = 100;
      parms._init = KMeans.Initialization.Furthest;
      kmm = doSeed(parms, System.nanoTime());

    } finally {
      if( fr  != null ) fr .remove();
      if (kmm != null) kmm.delete();
    }
  }


}
