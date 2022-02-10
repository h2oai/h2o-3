package hex.gam;

import hex.gam.GamSplines.ISplines;
import hex.gam.GamSplines.NormalizedBSplines;
import jsr166y.ThreadLocalRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.TestUtil;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import static hex.gam.GamSplines.NBSplinesUtils.extractKnots;
import static hex.gam.GamSplines.NBSplinesUtils.fillKnots;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GamBasicISplineTest extends TestUtil {
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
     * of knots that can generate non-zero values for the chosen NB basis spline.  This test is meant to check that the 
     * extraction process generates the correct knot sequences
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
    
    /**
     * This test is used to test the implementation of M-splines for order = 1, 2, 3, 4.  Manually implemented M-splines
     * use the formulae directly without recursion.  The actual NB splines used in GAM is implemented with recursion
     * that can generate M-splines of any order.  However, from my research off the internet, the process slows down
     * significantly after about order = 28.  To evaluate the correct NB spline implementation, a bunch of values are
     * given to both splines and they should return the same gamified values.
     */
    @Test
    public void testNBSplines() {
        double[] testValues = DoubleStream
                .generate(ThreadLocalRandom.current()::nextDouble)
                .limit(50)
                .toArray();
        double[] knots = new double[]{0, 0.3, 0.5, 0.6, 1};

        int order = 1;            // test for order 1
        NormalizedBSplines splineOrder1 = new NormalizedBSplines(order, knots);
        NBSplineOrder manualOrder1 = new NBSplineOrder(order, knots);
        assertCorrectNBSpline(splineOrder1, manualOrder1, testValues);
        assertCorrectNBSpline(splineOrder1, manualOrder1, knots);

        order = 2;            // test for order 2
        NormalizedBSplines splineOrder2 = new NormalizedBSplines(order, knots);
        NBSplineOrder manualOrder2 = new NBSplineOrder(order, knots);
        assertCorrectNBSpline(splineOrder2, manualOrder2, testValues);
        assertCorrectNBSpline(splineOrder2, manualOrder2, knots);

        order = 3;            // test for order 3
        NormalizedBSplines splineOrder3 = new NormalizedBSplines(order, knots);
        NBSplineOrder manualOrder3 = new NBSplineOrder(order, knots);
        assertCorrectNBSpline(splineOrder3, manualOrder3, testValues);
        assertCorrectNBSpline(splineOrder3, manualOrder3, knots);

        order = 4;             // test for order 4
        NormalizedBSplines splineOrder4 = new NormalizedBSplines(order, knots);
        NBSplineOrder manualOrder4 = new NBSplineOrder(order, knots);
        assertCorrectNBSpline(splineOrder4, manualOrder4, testValues);
        assertCorrectNBSpline(splineOrder4, manualOrder4, knots);

    }

    /**
     * This test is used to test the implementation of I-spline for order = 1, 2, 3.  Again just like testing the
     * NB Splines, we manually generate the I-splines directly from formulae we derived.  GAM used the cummulative
     * sum of NB Splines to derive the I-splines.  Since formulae for order = 1, 2, 3 are found/derived, we can only
     * test upto that order.  It should be enough to verify if we are coding it correctly.
     */
    @Test
    public void testISplines() {

        double[] testValues = DoubleStream
                .generate(ThreadLocalRandom.current()::nextDouble)
                .limit(50)
                .toArray();
        double[] knots = new double[]{0, 0.3, 0.5, 0.6, 1};
        int order = 1;
        ISplines ispline1 = new ISplines(order, knots);
        ISplineManual mspline1 = new ISplineManual(order, knots);
        assertCorrectISpline(ispline1, mspline1, testValues);
        assertCorrectISpline(ispline1, mspline1, knots);
        order = 2;
        ISplines ispline2 = new ISplines(order, knots);
        ISplineManual mspline2 = new ISplineManual(order, knots);
        assertCorrectISpline(ispline2, mspline2, testValues);
        assertCorrectISpline(ispline2, mspline2, knots);
        order = 3;
        ISplines ispline3 = new ISplines(order, knots);
        ISplineManual mspline3 = new ISplineManual(order, knots);
        assertCorrectISpline(ispline3, mspline3, testValues);
        assertCorrectISpline(ispline3, mspline3, knots);
    }
    
    public void assertCorrectISpline(ISplines ispline, ISplineManual mspline, double[] data) {
        double[] iGamifiedValues = new double[ispline._numIBasis];
        for (int dataIndex=0; dataIndex < data.length; dataIndex++) {
            double[] mGamifiedValues = mspline.evaluate(data[dataIndex]);
            ispline.gamifyVal(iGamifiedValues, data[dataIndex]);
            assertArrayEquals(mGamifiedValues, iGamifiedValues, TEST_TOLERANCE);
        }
    }

    public static double[] extractKnotsManual(int knotIndex, int order, List<Double> knots) {
        double[] extractedKnots = new double[order+1];
        for (int index=0; index<=order; index++) {
            extractedKnots[index] = knots.get(index+knotIndex);
        }
        return extractedKnots;
    }
    
    public static void assertCorrectNBSpline(NormalizedBSplines bpSpline, NBSplineOrder manualSpline, double[] values) {
        double[] manualGamifiedValues = new double[manualSpline._numBasis];
        double[] gamifiedValues = new double[bpSpline._totBasisFuncs];
        for (int dataIndex = 0; dataIndex < values.length; dataIndex++) {
            for (int index = 0; index < manualSpline._numBasis; index++)    // manually formed gamified row
                manualGamifiedValues[index] = manualSpline._bSplines[index].evaluate(values[dataIndex]);
            bpSpline.gamify(gamifiedValues, values[dataIndex]);
            assertArrayEquals(manualGamifiedValues, gamifiedValues, TEST_TOLERANCE);
        }
    }

    /**
     * ISpline using manually derived formula
     */
    public class ISplineManual {
        public int _order;
        public List<Double> _knots;
        public int _numBasis;
        public int _totKnots;
        public ISplineBasisM[] _iSplines;
        
        public ISplineManual(int order, double[] knots) {
            _order = order;
            _knots = fillKnots(knots, order+1);
            _totKnots = _knots.size();
            _numBasis = knots.length+order-1;
            _iSplines = new ISplineBasisM[_numBasis];
            for (int index=0; index < _numBasis; index++)
                _iSplines[index] = new ISplineBasisM(order, _knots, index);
        }
        
        public double[] evaluate(double val) {
            double[] gamifiedVal = new double[_numBasis];
            for (int index=0; index < _numBasis; index++) {
                gamifiedVal[index] = gamify(val, index);
            }
            return gamifiedVal;
        }
        
        public double gamify(double val, int basisInd) {
            List<Double> knots = _iSplines[basisInd]._knots;
            if (val < knots.get(0))
                return 0;
            if (val >= knots.get(_order))
                return 1;
            if (_order == 1) { // order of the ISpline
                if (val >= knots.get(0) && val < knots.get(1) && knots.get(1)!=knots.get(0))
                    return (val-knots.get(0))/(knots.get(1)-knots.get(0));
            } else if (_order == 2) {
                if (val >= knots.get(0) && val < knots.get(1) && knots.get(1)!=knots.get(0))
                    return (val-knots.get(0))*(val-knots.get(0))/((knots.get(2)-knots.get(0))*(knots.get(1)-knots.get(0)));
                if (val >= knots.get(1) && val < knots.get(2) && knots.get(2)!=knots.get(1)) {
                    double part1 = (val-knots.get(0))*(knots.get(2)-val)/((knots.get(2)-knots.get(0))*(knots.get(2)-knots.get(1)));
                    double part2 = (knots.get(3)-val)*(val-knots.get(1))/((knots.get(3)-knots.get(1))*(knots.get(2)-knots.get(1)));
                    double part3 = (val-knots.get(1))*(val-knots.get(1))/((knots.get(3)-knots.get(1))*(knots.get(2)-knots.get(1)));
                    return part1+part2+part3;
                }
            } else if (_order == 3) {
                if (val < knots.get(0))
                    return 0;
                if (val >= knots.get(0) && val < knots.get(1) && knots.get(0)!=knots.get(1))
                    return n4Part1(_iSplines[basisInd]._knots, val);
                if (val >= knots.get(1) && val < knots.get(2) && knots.get(1) != knots.get(2))
                    return n4Part2(_iSplines[basisInd]._knots, val) + n4Part1(_iSplines[basisInd+1]._knots, val);
                if (val >= knots.get(2) && val < knots.get(3) && knots.get(2)!=knots.get(3))
                    return n4Part3(_iSplines[basisInd]._knots, val)+n4Part2(_iSplines[basisInd+1]._knots, val)+n4Part1(_iSplines[basisInd+2]._knots, val);
            }
            return 0;
        }
        
        public double n4Part1(List<Double> knots, double val) {
            if (val >= knots.get(0) && val < knots.get(1) && knots.get(1)!=knots.get(0))
            return (val-knots.get(0))*(val-knots.get(0))*(val-knots.get(0))
                    /((knots.get(3)-knots.get(0))*(knots.get(2)-knots.get(0))*(knots.get(1)-knots.get(0)));
            else
                return 0;
        }
        
        public double n4Part2(List<Double> knots, double value) {
            if (value >= knots.get(1) && value < knots.get(2) &&  knots.get(1) != knots.get(2)) {
                double part1 = (value-knots.get(0)) * (value-knots.get(0)) *(knots.get(2)-value)
                        /((knots.get(3)-knots.get(0))*(knots.get(2)-knots.get(0))
                        *(knots.get(2)-knots.get(1)));
                double part2 = (value-knots.get(0))*(value-knots.get(1))*(knots.get(3)-value)
                        /((knots.get(3)-knots.get(0))*(knots.get(3)-knots.get(1))
                        *(knots.get(2)-knots.get(1)));
                double part3 = (knots.get(4)-value)*(value-knots.get(1))*(value-knots.get(1))
                        /((knots.get(4)-knots.get(1))*(knots.get(2)-knots.get(1))
                        *(knots.get(3)-knots.get(1)));
                return part1+part2+part3;
            } else {
                return 0;
            }
        }
        
        public double n4Part3(List<Double> knots, double value) {
            if (value >= knots.get(2) && value < knots.get(3) && knots.get(2)!=knots.get(3)) {
                double part1 = (value-knots.get(0))*(knots.get(3)-value)*(knots.get(3)-value)
                        /((knots.get(3)-knots.get(0))*(knots.get(3)-knots.get(1))
                        *(knots.get(3)-knots.get(2)));
                double part2 = (knots.get(4)-value)*(value-knots.get(1))*(knots.get(3)-value)
                        /((knots.get(4)-knots.get(1))*(knots.get(3)-knots.get(1))
                        *(knots.get(3)-knots.get(2)));
                double part3 = (knots.get(4)-value)*(value-knots.get(2))*(knots.get(4)-value)
                        /((knots.get(4)-knots.get(1))*(knots.get(4)-knots.get(2))
                        *(knots.get(3)-knots.get(2)));
                return part1+part2+part3;
            } else {
                return 0;
            }
        }
        
        public class ISplineBasisM {
            public int _order;
            public List<Double> _knots;
            public int _basisInd;
            
            public ISplineBasisM(int order, List<Double> knots, int index) {
                _order = order;
                _basisInd = index;
                _knots = extractKnots(index, order+1, knots);
            }
        }
    }


    /**
     * Basis function here is generated manually using actual formula and no recursion
     */
    public class NBSplineOrder {
        public int _order;
        public List<Double> _knots; // knots with duplications at both ends
        public int _numBasis;
        public int _totKnots;   // number of knots with duplication
        public BSplineBasis[] _bSplines;
        
        public NBSplineOrder(int order, double[] knots) {
            _order = order;
            _totKnots = knots.length + 2*order - 2;
            _numBasis = knots.length+order-2;
            _knots = fillKnots(knots, order);
            _bSplines = new BSplineBasis[_numBasis];
            for (int index=0; index < _numBasis; index++)
                _bSplines[index] = new BSplineBasis(_order, index, _knots);
        }
        
        public class BSplineBasis {
            public List<Double> _knots; // knots over which basis function is non-zero
            public int _order;

            public BSplineBasis(int order, int index, final List<Double> knots) {
                _order = order;
                _knots = extractKnots(index, order, knots);
            }

            public double evaluate(double value) {
                double gamifiedValue = 0;
                if (_order == 1) {
                    if (value >= _knots.get(0) && value < _knots.get(1))
                        return 1;
                } else if (_order == 2) {
                    if (value >= _knots.get(0) && value < _knots.get(1) && _knots.get(0) != _knots.get(1))
                        return (value - _knots.get(0)) / (_knots.get(1) - _knots.get(0));
                    else if (value >= _knots.get(1) && value < _knots.get(2) && _knots.get(1) != _knots.get(2))
                        return (_knots.get(2) - value) / (_knots.get(2) - _knots.get(1));
                } else if (_order == 3) {
                    if (value >= _knots.get(0) && value < _knots.get(1) && _knots.get(0) != _knots.get(1))
                        return (value-_knots.get(0))*(value-_knots.get(0))
                                /((_knots.get(2)-_knots.get(0))*(_knots.get(1)-_knots.get(0)));
                    if (value >= _knots.get(1) && value < _knots.get(2) && _knots.get(1) != _knots.get(2)) {
                        double part1 = (value - _knots.get(0))*(_knots.get(2)-value)
                                /((_knots.get(2)-_knots.get(0))*(_knots.get(2)-_knots.get(1)));
                        double part2 = (_knots.get(3)-value)*(value-_knots.get(1))
                                /((_knots.get(3)-_knots.get(1))*(_knots.get(2)-_knots.get(1)));
                        return part1+part2;
                    }
                    if (value >= _knots.get(2) && value < _knots.get(3) && _knots.get(2)!=_knots.get(3)) {
                        return (_knots.get(3)-value)*(_knots.get(3)-value)
                                /((_knots.get(3)-_knots.get(1))*(_knots.get(3)-_knots.get(2)));
                    }
                } else if (_order == 4) {
                    if (value >= _knots.get(0) && value < _knots.get(1) && _knots.get(0) != _knots.get(1))
                        return (value-_knots.get(0))*(value-_knots.get(0))*(value-_knots.get(0))
                                /((_knots.get(3)-_knots.get(0))*(_knots.get(2)-_knots.get(0))*(_knots.get(1)-_knots.get(0)));
                    if (value >= _knots.get(1) && value < _knots.get(2) && (_knots.get(1) != _knots.get(2)) &&
                            (_knots.get(0) != _knots.get(1))) {
                        double part1 = (value-_knots.get(0)) * (value-_knots.get(0)) *(_knots.get(2)-value)
                                /((_knots.get(3)-_knots.get(0))*(_knots.get(2)-_knots.get(0))*(_knots.get(2)-_knots.get(1)));
                        double part2 = (value-_knots.get(0))*(value-_knots.get(1))*(_knots.get(3)-value)
                                /((_knots.get(3)-_knots.get(0))*(_knots.get(3)-_knots.get(1))*(_knots.get(2)-_knots.get(1)));
                        double part3 = (_knots.get(4)-value)*(value-_knots.get(1))*(value-_knots.get(1))
                                /((_knots.get(4)-_knots.get(1))*(_knots.get(2)-_knots.get(1))*(_knots.get(3)-_knots.get(1)));
                        return part1+part2+part3;
                    }
                    if (value >= _knots.get(2) && value < _knots.get(3) && _knots.get(2)!=_knots.get(3)) {
                        double part1 = (value-_knots.get(0))*(_knots.get(3)-value)*(_knots.get(3)-value)
                                /((_knots.get(3)-_knots.get(0))*(_knots.get(3)-_knots.get(1))*(_knots.get(3)-_knots.get(2)));
                        double part2 = (_knots.get(4)-value)*(value-_knots.get(1))*(_knots.get(3)-value)
                                /((_knots.get(4)-_knots.get(1))*(_knots.get(3)-_knots.get(1))*(_knots.get(3)-_knots.get(2)));
                        double part3 = (_knots.get(4)-value)*(value-_knots.get(2))*(_knots.get(4)-value)
                                /((_knots.get(4)-_knots.get(1))*(_knots.get(4)-_knots.get(2))*(_knots.get(3)-_knots.get(2)));
                        return part1+part2+part3;
                    }
                    if (value >= _knots.get(3) && value < _knots.get(4) && _knots.get(3)!=_knots.get(4)) 
                        return (_knots.get(4)-value)*(_knots.get(4)-value)*(_knots.get(4)-value)
                                /((_knots.get(4)-_knots.get(1))*(_knots.get(4)-_knots.get(2))*(_knots.get(4)-_knots.get(3)));
                }
                return gamifiedValue;
            }
        }
    }


    
}
