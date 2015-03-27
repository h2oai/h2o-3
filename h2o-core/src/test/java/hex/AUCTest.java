package hex;

import java.util.Arrays;
import org.junit.*;
import water.*;
import water.fvec.Frame;
//import water.fvec.Vec;
//import water.util.ArrayUtils;

public class AUCTest extends TestUtil {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  @Test public void testAUC0() {
    // Sorted probabilities.  At threshold 1e-6 flips from false to true, on
    // average.  However, there are a lot of random choices at 1e-3.
    double probs[] = new double[]{1e-8,1e-7,1e-6,1e-5,1e-4,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-2,1e-1};
    double actls[] = new double[]{   0,   0,    1,   1,   1,  1,   0,   1,   0,   1,   0,   1,   0,   1,   1,   1,   1};

    // Positives & Negatives
    int P = 0;
    for( int i=0; i<actls.length; i++ ) P += (int)actls[i];
    int N = actls.length - P;
    System.out.println("P="+P+", N="+N);

    // Compute TP & FP for all thresholds
    double thresh[] = new double[]{1e-1,1e-2,1e-3+1e-9,1e-3,1e-3-1e-9,1e-4,1e-5,1e-6,1e-7,1e-8,0};
    int tp[] = new int[thresh.length], fp[] = new int[thresh.length];
    for( int i=0; i<probs.length; i++ ) {
      for( int t=0; t<thresh.length; t++ ) {
        if( probs[i] >= thresh[t] ) // Not interested if below threshold
          if( actls[i]==0.0 ) fp[t]++; // False positive
          else tp[t]++;                // True  positive
      }
    }
    System.out.println(Arrays.toString(tp));
    System.out.println(Arrays.toString(fp));
    for( int i=0; i<tp.length; i++ ) System.out.print("{"+((double)tp[i]/P)+","+((double)fp[i]/N)+"} ");
    System.out.println();
    // The AUC for this dataset, according to R's ROCR package, is 0.6363636363
    Assert.assertEquals(doAUC(probs,actls),0.636363636363,1e-5);

    // Shuffle, check again
    swap(0, 5, probs, actls);
    swap(1, 6, probs, actls);
    swap(7, 15, probs, actls);
    Assert.assertEquals(doAUC(probs,actls),0.636363636363,1e-5);

    // Now from a large test file
    double ROCR_auc = 0.7244389;
    Frame fr = parse_test_file("smalldata/junit/auc4.csv");
    for( int i=10; i<1000; i+=10 )
      System.out.println("bins="+i+", aucERR="+Math.abs(new AUC2(i,fr.vec("V1"),fr.vec("V2"))._auc-ROCR_auc)/ROCR_auc);

    Assert.assertEquals(ROCR_auc, new AUC2(fr.vec("V1"), fr.vec("V2"))._auc, 1e-4);

    fr.remove();
  }

  private static double doAUC(double probs[], double actls[]) {
    double rows[][] = new double[probs.length][];
    for( int i=0; i<probs.length; i++ )
      rows[i] = new double[]{probs[i],actls[i]};
    Frame fr = frame(new String[]{"probs","actls"},rows);
    AUC2 auc = new AUC2(fr.vec("probs"),fr.vec("actls"));
    fr.remove();
    for( int i=0; i<auc._nBins; i++ ) System.out.print("{"+((double)auc._tps[i]/auc._tp)+","+((double)auc._fps[i]/auc._fp)+"} ");
    System.out.println();
    return auc._auc;
  }

  private static void swap(int x, int y, double probs[], double actls[]) {
    double tmp0 = probs[x]; probs[x] = probs[y]; probs[y] = tmp0;
    double tmp1 = actls[x]; actls[x] = actls[y]; actls[y] = tmp1;
  }

}
