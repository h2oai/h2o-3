package hex;

import java.util.Arrays;
import org.junit.*;
import water.*;
import water.fvec.Frame;
//import water.fvec.Vec;
//import water.util.ArrayUtils;

public class AUCTest extends TestUtil {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  @Test public void testAUC() {
    // Sorted probabilities.  At threshold 1e-6 flips from false to true, on
    // average.  However, there are a lot of random choices at 1e-3.
    double probs[] = new double[]{1e-8,1e-7,1e-6,1e-5,1e-4,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-2,1e-1};
    double actls[] = new double[]{   0,   0,    1,   1,   1,  1,   0,   1,   0,   1,   0,   1,   0,   1,   1,   1,   1};
    //Frame fr = frame(new String[]{"probs","actls"}, probs, actls);

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

    double rows[][] = new double[probs.length][];
    for( int i=0; i<probs.length; i++ )
      rows[i] = new double[]{probs[i],actls[i]};
    Frame fr = frame(new String[]{"probs","actls"},rows);
    AUC2 auc = new AUC2(5,fr.vec("probs"),fr.vec("actls"));
    for( int i=0; i<auc._nBins; i++ ) System.out.print("{"+((double)auc._tps[i]/auc._tp)+","+((double)auc._fps[i]/auc._fp)+"} ");
    System.out.println();


    fr.remove();
  }

}
