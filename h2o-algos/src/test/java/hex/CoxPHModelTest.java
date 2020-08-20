package hex;

import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.util.RandomUtils;

import java.util.Collections;
import java.util.Random;

import static hex.ModelMetricsRegressionCoxPH.MetricBuilderRegressionCoxPH.concordance;
import static org.junit.Assert.assertEquals;

public class CoxPHModelTest extends TestUtil {
    
    @BeforeClass
    public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void concordanceOfAPerfectEstimateIsOne() {
        checkConcordanceForEstimate(new MRTask() {
            @Override
            public void map(Chunk c) {
                for (int i = 0; i < c._len; ++i) {
                    c.set(i, c.atd(i) * -0.1);
                }
            }
        }, 1.0d, 0.0001d);
    }

    @Test
    public void concordanceOfATerribleEstimateIsZero() {
        checkConcordanceForEstimate(new MRTask() {
            @Override
            public void map(Chunk c) {
                for (int i = 0; i < c._len; ++i) {
                    c.set(i, c.atd(i));
                }
            }
        }, 0.0d, 0.0001d);
    }
    
    @Test
    public void concordanceOfARandomEstimateIsOneHalf() {
        checkConcordanceForEstimate(new MRTask() {
            @Override public void map(Chunk c){
                Random rng = new RandomUtils.PCGRNG(c.start(),1);
                for(int i = 0; i < c._len; ++i) {
                    c.set(i, rng.nextFloat());
                }
            }
        }, 0.5d, 0.01d); 
    }

    @Test
    public void concordanceExampleOneBadEstimate() throws Exception {
        try {
            Scope.enter();
            final Vec starts = Scope.track(Vec.makeVec(new double[] {0d, 0d, 0d, 0d, 0d, 0d, 0d}, Vec.newKey()));
            final Vec stops = Scope.track(Vec.makeVec(new double[] {0d, 1d, 2d, 3d, 4d, 5d, 6d}, Vec.newKey()));
            final Vec status = Scope.track(Vec.makeVec(new double[] {1d, 1d, 1d, 1d, 1d, 1d, 1d}, Vec.newKey()));
            final Vec estimates = Scope.track(Vec.makeVec(new double[] {6d, 5d, 4d, 3d, 2d, 0d, 1d}, Vec.newKey()));
            
            final double c = concordance(starts, stops, status, Collections.emptyList(), estimates).c();
            
            final double pairCount = starts.length() * (starts.length() - 1) / 2d;
            assertEquals((pairCount - 1) / pairCount, c, 0.01);
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void concordanceExampleOneBadEstimateWithStrata() throws Exception {
        try {
            Scope.enter();
            final Vec starts = Scope.track(Vec.makeVec(new double[] {0d, 0d, 0d, 0d, 0d, 0d, 0d}, Vec.newKey()));
            final Vec stops = Scope.track(Vec.makeVec(new double[] {0d, 1d, 2d, 3d, 4d, 5d, 6d}, Vec.newKey()));
            final Vec status = Scope.track(Vec.makeVec(new double[] {1d, 1d, 1d, 1d, 1d, 1d, 1d}, Vec.newKey()));
            final Vec estimates = Scope.track(Vec.makeVec(new double[] {6d, 5d, 4d, 3d, 2d, 0d, 1d}, Vec.newKey()));
            final Vec strata = Scope.track(Vec.makeVec(new long[] {1L, 1L, 1L, 1L, 0L, 0L, 0L}, new String[] {"00", "11"}, Vec.newKey()));
            
            final double c = concordance(starts, stops, status, Collections.singletonList(strata), estimates).c();
            
            final double pairCount = 4 * (4 - 1) / 2 + 3 * (3 - 1) / 2;
            assertEquals((pairCount - 1) / pairCount, c, 0.01);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void concordanceExampleMoreBadEstimates() throws Exception {
        try {
            Scope.enter();
            final Vec starts = null;
            final Vec stops = Scope.track(Vec.makeVec(new double[] {0d, 1d, 2d, 3d, 4d, 5d, 6d}, Vec.newKey()));
            final Vec status = Scope.track(Vec.makeVec(new double[] {1d, 1d, 1d, 1d, 1d, 1d, 1d}, Vec.newKey()));
            final Vec estimates = Scope.track(Vec.makeVec(new double[] {6d, 5d, 4d, 3d, 0d, 1d, 2d}, Vec.newKey()));

            final double c = concordance(starts, stops, status, Collections.emptyList(), estimates).c();

            final double pairCount = stops.length() * (stops.length() - 1) / 2d;
            assertEquals((pairCount - 3) / pairCount, c, 0.01);
        } finally {
            Scope.exit();
        }
    } 
    
    @Test
    public void concordanceExampleOneTie() throws Exception {
        try {
            Scope.enter();
            final Vec starts = null;
            final Vec stops = Scope.track(Vec.makeVec(new double[] {0d, 1d, 2d, 3d, 4d, 5d, 6d}, Vec.newKey()));
            final Vec status = Scope.track(Vec.makeVec(new double[] {1d, 1d, 1d, 1d, 1d, 1d, 1d}, Vec.newKey()));
            final Vec estimates = Scope.track(Vec.makeVec(new double[] {6d, 5d, 4d, 3d, 2d, 2d, 0d}, Vec.newKey()));

            final double c = concordance(starts, stops, status, Collections.emptyList(), estimates).c();

            final double pairCount = stops.length() * (stops.length() - 1) / 2d;
            assertEquals((pairCount - 0.5) / pairCount, c, 0.01);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void concordanceExampleOneTieWithNontrivialStarts() throws Exception {
        try {
            Scope.enter();
            final Vec starts = Scope.track(Vec.makeVec(new double[] {0d, 1d, 2d, 3d, 4d, 5d, 6d}, Vec.newKey()));
            final Vec stops = Scope.track(Vec.makeVec(new double[] {0d, 2d, 4d, 6d, 8d, 10d, 12d}, Vec.newKey()));
            final Vec status = Scope.track(Vec.makeVec(new double[] {1d, 1d, 1d, 1d, 1d, 1d, 1d}, Vec.newKey()));
            final Vec estimates = Scope.track(Vec.makeVec(new double[] {6d, 5d, 4d, 3d, 2d, 2d, 0d}, Vec.newKey()));

            final double c = concordance(starts, stops, status, Collections.emptyList(), estimates).c();

            final double pairCount = stops.length() * (stops.length() - 1) / 2d;
            assertEquals((pairCount - 0.5) / pairCount, c, 0.01);
        } finally {
            Scope.exit();
        }
    }

    private void checkConcordanceForEstimate(MRTask estimateTask, double expected, double delta) {
        try {
            Scope.enter();
            final int len = 2000;

            final Vec starts = Scope.track(Vec.makeCon(0.0, len));
            final Vec times = Scope.track(Vec.makeCon(0.0, len).makeRand(0));
            final Vec status = Scope.track(Vec.makeOne(len, Vec.T_CAT));

            new MRTask() {
                @Override
                public void map(Chunk c) {
                    for (int i = 0; i < c._len; ++i) {
                        c.set(i, 1L);
                    }
                }
            }.doAll(status);

            final Vec estimates = prepareEstimates(estimateTask, times);

            final double c = concordance(starts, times, status, Collections.emptyList(), estimates).c();

            assertEquals(expected, c, delta);
        } finally {
            Scope.exit();
        }
    }

    private Vec prepareEstimates(MRTask estimateTask, Vec times) {
        Vec estimates = Scope.track(times.doCopy());
        Key<Vec> estimatesKey = estimates._key;
        DKV.put(estimatesKey, estimates);
        Scope.track(estimates);

        estimateTask.doAll(estimates);
        return estimates;
    }
}
