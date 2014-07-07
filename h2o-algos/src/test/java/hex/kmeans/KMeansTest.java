package hex.kmeans;

import org.testng.AssertJUnit;
import org.testng.annotations.*;

import java.io.File;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;

public class KMeansTest extends TestUtil {
  // Run KMeans with a given seed, & check all clusters are non-empty
  private static KMeansModel doSeed( KMeansModel.KMeansParameters parms, long seed ) {
    parms._seed = seed;
    KMeans job = new KMeans(parms);
    KMeansModel kmm = job.get();
    job.remove();
    for( int i=0; i<parms._K; i++ )
      AssertJUnit.assertTrue( "Seed: "+seed, kmm._rows[i] != 0 );
    return kmm;
  }

  @Test public void testIris() {
    KMeansModel kmm = null;
    Frame fr = null, fr2= null;
    try {
      fr = parse_test_file("smalldata/iris/iris_wheader.csv");

      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._src = fr._key;
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
      parms._src = fr._key;
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
  @Test(enabled=false) public void testCovtype() {
    Frame fr = null;
    try {
      File f = find_test_file("../datasets/UCI/UCI-large/covtype/covtype.data");
      if( f==null ) return;     // Ignore if large file not found
      NFSFileVec nfs = NFSFileVec.make(f);
      fr = water.parser.ParseDataset2.parse(Key.make(),nfs._key);

      KMeansModel.KMeansParameters parms = new KMeansModel.KMeansParameters();
      parms._src = fr._key;
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
}
