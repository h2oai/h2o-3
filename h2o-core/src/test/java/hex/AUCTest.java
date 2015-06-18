package hex;

import java.util.Arrays;
import org.junit.*;
import water.*;
import water.fvec.Frame;

public class AUCTest extends TestUtil {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  @Test public void testAUC0() {
    double auc0 = AUC2.perfectAUC(new double[]{0,0.5,0.5,1}, new double[]{0,0,1,1});
    Assert.assertEquals(0.875,auc0,1e-7);
    // Flip the tied actuals
    double auc1 = AUC2.perfectAUC(new double[]{0,0.5,0.5,1}, new double[]{0,1,0,1});
    Assert.assertEquals(0.875,auc1,1e-7);

    // Area is 10/12 (TPS=4, FPS=3, so area is 4x3 or 12 units; 10 are under).
    double auc2 = AUC2.perfectAUC(new double[]{0.1,0.2,0.3,0.4,0.5,0.6,0.7}, new double[]{0,0,1,1,0,1,1});
    Assert.assertEquals(0.8333333,auc2,1e-7);


    // Sorted probabilities.  At threshold 1e-6 flips from false to true, on
    // average.  However, there are a lot of random choices at 1e-3.
    double probs[] = new double[]{1e-8,1e-7,1e-6,1e-5,1e-4,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-3,1e-2,1e-1};
    double actls[] = new double[]{   0,   0,    1,   1,   1,  1,   0,   1,   0,   1,   0,   1,   0,   1,   1,   1,   1};

    // Positives & Negatives
    int P = 0;
    for( double a : actls ) P += (int)a;
    int N = actls.length - P;
    System.out.println("P="+P+", N="+N);

    // Compute TP & FP for all thresholds
    double thresh[] = new double[]{1e-1,1e-2,1e-3+1e-9,1e-3,1e-3-1e-9,1e-4,1e-5,1e-6,1e-7,1e-8,0};
    int tp[] = new int[thresh.length], fp[] = new int[thresh.length];
    int tn[] = new int[thresh.length], fn[] = new int[thresh.length];
    for( int i=0; i<probs.length; i++ ) {
      for( int t=0; t<thresh.length; t++ ) {
        if( probs[i] >= thresh[t] ) // Not interested if below threshold
          if( actls[i]==0.0 ) fp[t]++; // False positive
          else tp[t]++;                // True  positive
        else
          if( actls[i]==0.0 ) tn[t]++; // True  negative
          else fn[t]++;                // False negative
      }
    }
    System.out.println(Arrays.toString(tp));
    System.out.println(Arrays.toString(fp));
    System.out.println(Arrays.toString(fn));
    System.out.println(Arrays.toString(tn));
    for( int i=0; i<tp.length; i++ ) System.out.print("{"+((double)tp[i]/P)+","+((double)fp[i]/N)+"} ");
    System.out.println();
    // The AUC for this dataset, according to R's ROCR package, is 0.6363636363
    Assert.assertEquals(doAUC(probs,actls),0.636363636363,1e-5);
    Assert.assertEquals(AUC2.perfectAUC(probs,actls),0.636363636363,1e-7);

    // Shuffle, check again
    swap(0, 5, probs, actls);
    swap(1, 6, probs, actls);
    swap(7, 15, probs, actls);
    Assert.assertEquals(doAUC(probs,actls),0.636363636363,1e-5);
    Assert.assertEquals(AUC2.perfectAUC(probs,actls),0.636363636363,1e-7);

    // Now from a large test file
    double ROCR_auc = 0.7244389;
    Frame fr = parse_test_file("smalldata/junit/auc.csv.gz");
    // Slow; used to confirm the accuracy as we increase bin counts
    //for( int i=10; i<1000; i+=10 ) {
    //  AUC2 auc = new AUC2(i,fr.vec("V1"),fr.vec("V2"));
    //  System.out.println("bins="+i+", aucERR="+Math.abs(auc._auc-ROCR_auc)/ROCR_auc);
    //  Assert.assertEquals(fr.numRows(), auc._p+auc._n);
    //}

    double aucp = AUC2.perfectAUC(fr.vec("V1"), fr.vec("V2"));
    Assert.assertEquals(ROCR_auc, aucp, 1e-4);
    AUC2 auc = new AUC2(fr.vec("V1"), fr.vec("V2"));
    Assert.assertEquals(ROCR_auc, auc._auc, 1e-4);

    Assert.assertEquals(1.0, AUC2.ThresholdCriterion.precision.max_criterion(auc), 1e-4);

    double ROCR_max_abs_mcc = 0.4553512;
    Assert.assertEquals(ROCR_max_abs_mcc, AUC2.ThresholdCriterion.absolute_MCC.max_criterion(auc), 1e-3);

    double ROCR_f1 = 0.9920445; // same as ROCR "f" with alpha=0, or alternative beta=1
    Assert.assertEquals(ROCR_f1, AUC2.ThresholdCriterion.f1.max_criterion(auc), 1e-4);

    fr.remove();
  }

  private static double doAUC(double probs[], double actls[]) {
    double rows[][] = new double[probs.length][];
    for( int i=0; i<probs.length; i++ )
      rows[i] = new double[]{probs[i],actls[i]};
    Frame fr = frame(new String[]{"probs","actls"},rows);
    AUC2 auc = new AUC2(fr.vec("probs"),fr.vec("actls"));
    fr.remove();
    for( int i=0; i<auc._nBins; i++ ) System.out.print("{"+((double)auc._tps[i]/auc._p)+","+((double)auc._fps[i]/auc._n)+"} ");
    System.out.println();
    for( int i=0; i<auc._nBins; i++ ) System.out.print(AUC2.ThresholdCriterion.min_per_class_accuracy.exec(auc,i)+" ");
    System.out.println();
    return auc._auc;
  }

  private static void swap(int x, int y, double probs[], double actls[]) {
    double tmp0 = probs[x]; probs[x] = probs[y]; probs[y] = tmp0;
    double tmp1 = actls[x]; actls[x] = actls[y]; actls[y] = tmp1;
  }

}
