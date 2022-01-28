package hex.gam;

import hex.gam.GamSplines.NormalizedBSplines;
import jsr166y.ThreadLocalRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import static hex.gam.GamSplines.NBSplinesUtils.extractKnots;
import static hex.gam.GamSplines.NBSplinesUtils.fillKnots;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GamISplineTest extends TestUtil {
    public static final double TEST_TOLERANCE = 1e-12;

    /***
     * Given a knot sequence length and a spline order, I want to make sure the correct number of duplication is 
     * generated at the beginning and end of the knot sequence.  The following method is used to generate the 
     * duplications:
     * 1. For spline order m, there should be m points at the beginning of the knot sequence with the same value as
     *    knots[0].
     * 2. For spline order m, there should be m points at the end of the knot sequence with the same value as 
     *    knots[knots.length-1].
     *    
     * This means the final knot sequence after the duplication will contain N+2m-2 points with the first m and last m 
     * points having equal values.
     */
    @Test
    public void testDuplicateKnots() {
        int[] nSizes = new int[]{3, 10, 50};
        int[] nOrder = new int[]{1, 4, 6};
        for (int sizeIndex=0; sizeIndex<nSizes.length; sizeIndex++) {
            for (int orderIndex = 0; orderIndex < nOrder.length; orderIndex++) {
                double[] knots = DoubleStream
                        .generate(ThreadLocalRandom.current()::nextDouble)
                        .limit(nSizes[sizeIndex]).sorted().toArray();  // knots need to be sorted
                List<Double> duplicatedKnots = fillKnots(knots, nOrder[orderIndex]);
                assertTrue(duplicatedKnots.size() == nSizes[sizeIndex]+2*nOrder[orderIndex]-2); // correct length
                for (int index=1; index < nOrder[orderIndex]; index++) { // duplication at beginning and end are correct
                    assertTrue(Math.abs(duplicatedKnots.get(0)-duplicatedKnots.get(index)) < TEST_TOLERANCE);
                    assertTrue(Math.abs(duplicatedKnots.get(duplicatedKnots.size()-1)-
                            duplicatedKnots.get(duplicatedKnots.size()-index-1)) < TEST_TOLERANCE);
                }
            }
        }
    }

    /**
     * Given the whole knot sequence and an index that choose the NB basis spline, we need to extract the sequence
     * of knots that can generate non-zero values for the NB basis spline.  This test is meant to check that the 
     * extraction process generates the correct knot sequence
     */
    @Test
    public void testExtractKnots() {
        int[] nSizes = new int[]{3, 10, 50};
        int[] nOrder = new int[]{1, 4, 6};
        for (int sizeIndex=0; sizeIndex<nSizes.length; sizeIndex++) {
            for (int orderIndex = 0; orderIndex < nOrder.length; orderIndex++) {
                double[] knots = DoubleStream
                        .generate(ThreadLocalRandom.current()::nextDouble)
                        .limit(nSizes[sizeIndex]).sorted().toArray();  // knots need to be sorted
                List<Double> duplicatedKnots = fillKnots(knots, nOrder[orderIndex]);
                int numBasisFuncs = nSizes[sizeIndex]+nOrder[orderIndex]-2;
                for (int basisIndex = 0; basisIndex < numBasisFuncs; basisIndex++) {
                    double[] basisKnots = Stream.of((extractKnots(basisIndex, nOrder[orderIndex], duplicatedKnots)
                                                     .stream().toArray(Double[]::new)))
                                                .mapToDouble(Double::doubleValue)
                                                .toArray();
                    double[] manualKnots = extractKnotsManual(basisIndex, nOrder[orderIndex], duplicatedKnots);
                    assertArrayEquals(basisKnots, manualKnots, TEST_TOLERANCE);
                }
            }
        }
    }
    
    public static double[] extractKnotsManual(int knotIndex, int order, List<Double> knots) {
        double[] extractedKnots = new double[order+1];
        for (int index=0; index<=order; index++) {
            extractedKnots[index] = knots.get(index+knotIndex);
        }
        return extractedKnots;
    }
    
    /**
     * This test is used to test the implementation of M-splines for order = 1, 2, 3, 4
     */
    @Test
    public void testNBSplines() {
        Scope.enter();
        try {
            double[] testValues = DoubleStream
                    .generate(ThreadLocalRandom.current()::nextDouble)
                    .limit(50)
                    .toArray();
            double[] knots = new double[]{0,0.3,0.5,0.6,1};
            NormalizedBSplines splineOrder1 = new NormalizedBSplines(1, knots);
            NBSplineOrder manualOrder1 = new NBSplineOrder(1, knots);
            assertCorrectNBSpline(splineOrder1, manualOrder1);
            
        } finally {
            Scope.exit();
        }
    }
    
    public static void assertCorrectNBSpline(NormalizedBSplines bpSpline, NBSplineOrder manualSpline) {
        
    }
    

    /**
     * Basis function here is generated manually using actual formula and no recursion
     */
    public class NBSplineOrder {
        public int _order;
        public List<Double> _knots;
        public int _numBasis;
        public int _totKnots;   // number of knots with duplication
        public BSplineBasis[] _bSplines;
        
        public NBSplineOrder(int order, double[] knots) {
            _order = order;
            _totKnots = knots.length + 2*order - 2;
            _numBasis = knots.length+order-2;

        }
        
        public class BSplineBasis {
            public List<Double> _knots; // knots over which basis function is non-zero
            public int _order;
            
        }
    }

    /**
     * This test is used to test the implementation of I-spline for order = 1, 2, 3
     */
    @Test
    public void testISplines() {
        Scope.enter();
        try {
            Frame knotsFrame = ArrayUtils.frame(ard(ard(0), ard(0.3), ard(0.5), ard(0.6), 
                    ard(1)));
            Scope.track(knotsFrame);
            //Frame dataFrame = TestUtil.generateRealWithRangeOnly(1, 100, 0, 12345, 1);
            double[] testValues = DoubleStream
                    .generate(ThreadLocalRandom.current()::nextDouble)
                    .limit(100)
                    .toArray();
            // convert testValues to basis functions manually
            // convert testValues to basis functions using GAM I-spline
        } finally {
            Scope.exit();
        }
    }

    public class ISplineOrder1 {
        List<Double> _knots;
        

    }
    
    public class ISplineOrder2 {
        
    }

    public class ISplineOrder3 {

    }
}
