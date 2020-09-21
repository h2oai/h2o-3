package hex;

import hex.ModelMetricsRegressionCoxPH.StatTree;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.RandomUtils;

import java.util.Collections;
import java.util.Random;

import static hex.ModelMetricsRegressionCoxPH.MetricBuilderRegressionCoxPH.concordance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ModelMetricsRegressionCoxPHTest extends TestUtil {
    
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
        }, 0.5d, 0.03d); 
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
            assertEquals((pairCount - 1) / pairCount, c, 0.001);
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void concordanceExampleOneBadEstimateAndCensoring() throws Exception {
        try {
            Scope.enter();
            final Vec starts = Scope.track(Vec.makeVec(new double[] {0d, 0d, 0d, 0d, 0d, 0d, 0d}, Vec.newKey()));
            final Vec stops = Scope.track(Vec.makeVec(new double[] {0d, 1d, 2d, 3d, 4d, 5d, 6d}, Vec.newKey()));
            final Vec status = Scope.track(Vec.makeVec(new double[] {1d, 0d, 1d, 1d, 0d, 1d, 1d}, Vec.newKey()));
            final Vec estimates = Scope.track(Vec.makeVec(new double[] {6d, 5d, 4d, 3d, 2d, 0d, 1d}, Vec.newKey()));
            
            final double c = concordance(starts, stops, status, Collections.emptyList(), estimates).c();
            
            assertEquals(0.92857, c, 0.001);
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
            assertEquals((pairCount - 1) / pairCount, c, 0.001);
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
            assertEquals((pairCount - 3) / pairCount, c, 0.001);
        } finally {
            Scope.exit();
        }
    } 
    
    @Test
    public void concordanceExampleOneTie() throws Exception {
        try {
            Scope.enter();
            final Vec starts = null;
            final Vec stops = Scope.track(Vec.makeVec(new double[] {0d, 10d, 20d, 30d, 40d, 50d, 60d}, Vec.newKey()));
            final Vec status = Scope.track(Vec.makeVec(new double[] {1d, 1d, 1d, 1d, 1d, 1d, 1d}, Vec.newKey()));
            final Vec estimates = Scope.track(Vec.makeVec(new double[] {6d, 5d, 4d, 3d, 2d, 2d, 0d}, Vec.newKey()));

            final double c = concordance(starts, stops, status, Collections.emptyList(), estimates).c();

            final double pairCount = stops.length() * (stops.length() - 1) / 2d;
            assertEquals((pairCount - 0.5) / pairCount, c, 0.001);
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
            assertEquals((pairCount - 0.5) / pairCount, c, 0.001);
        } finally {
            Scope.exit();
        }
    }

    private void checkConcordanceForEstimate(MRTask estimateTask, double expected, double delta) {
        try {
            Scope.enter();
            final int len = 5000;

            final Vec starts = Scope.track(Vec.makeCon(0.0, len));
            final Vec times = Scope.track(Vec.makeCon(0.0, len).makeRand(0));
            final Vec status = Scope.track(Vec.makeOne(len, Vec.T_CAT));
            status.setDomain(new String[]{"0", "1"});

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

    @Test
    public void statTreeSimple() throws Exception {
        StatTree t = new StatTree(new double[] {0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0});
        
        for (int i = 0; i < 10; i++) {
            assertEquals(t.rankAndCount(i).count, 0);
            assertEquals(t.rankAndCount(i).rank, 0);
        }

        assertEquals(t.len(), 0);
        t.insert(5);
        t.insert(6);
        t.insert(6);
        t.insert(0);
        t.insert(9);
        assertEquals(t.len(), 5);

        assertEquals(t.rankAndCount(0).rank,0);
        assertEquals(t.rankAndCount(0).count,1);
        assertEquals(t.rankAndCount(0.5).rank,1);
        assertEquals(t.rankAndCount(0.5).count,0);
        assertEquals(t.rankAndCount(4.5).rank,1);
        assertEquals(t.rankAndCount(4.5).count,0);
        assertEquals(t.rankAndCount(5).rank, 1);
        assertEquals(t.rankAndCount(5).count, 1);
        assertEquals(t.rankAndCount(5.5).rank,2);
        assertEquals(t.rankAndCount(5.5).count, 0);
        assertEquals(t.rankAndCount(6).rank,2);
        assertEquals(t.rankAndCount(6).count, 2);
        assertEquals(t.rankAndCount(6.5).rank,4);
        assertEquals(t.rankAndCount(6.5).count,0);
        assertEquals(t.rankAndCount(8.5).rank,4);
        assertEquals(t.rankAndCount(8.5).count,0);
        assertEquals(t.rankAndCount(9).rank,4);
        assertEquals(t.rankAndCount(9).count, 1);
        assertEquals(t.rankAndCount(9.5).rank,5);
        assertEquals(t.rankAndCount(9.5).count, 0);

        try {
            t.insert(123.4);
            assertTrue("Should fail with exception", false);
        } catch (IllegalArgumentException e) {
            assertEquals("Value 123.4 not contained in tree. Tree counts now in illegal state;", e.getMessage());
        }
    }
    
    @Test
    public void statTreeConstructionAndInsertAndRank() throws Exception { 
        for (int len = 1; len < 200 ; len++) {
            final double[] values = new double[len];

           for (int i = 0; i < len; i++) {
                values[i] = 11 * i;
            }

            final StatTree statTree = new StatTree(values);

            for (double value : values) {
                statTree.insert(value);
            }
            for (double value : values) {
                statTree.insert(value);
                statTree.insert(value);
            }

            for (long count : statTree.counts) {
                assertTrue(count >= 1);
            }

            for (double value : values) {
                StatTree.RankAndCount rankAndCount = statTree.rankAndCount(value);

                assertEquals(3, rankAndCount.count);
            }
        }
    }
}
