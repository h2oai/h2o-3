package hex.gam;

import hex.gam.GamSplines.NBSplinesTypeIDerivative;
import hex.genmodel.algos.gam.ISplines;
import hex.genmodel.algos.gam.NBSplinesTypeI;
import hex.genmodel.algos.gam.NBSplinesTypeII;
import jsr166y.ThreadLocalRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.MemoryManager;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;
import java.util.stream.DoubleStream;
import static hex.gam.GamSplines.NBSplinesUtils.integratePolynomial;
import static hex.genmodel.algos.gam.GamUtilsISplines.extractKnots;
import static hex.genmodel.algos.gam.GamUtilsISplines.fillKnots;
import static hex.gam.GamSplines.NBSplinesTypeIDerivative.form1stOrderDerivatives;
import static hex.genmodel.algos.gam.GamUtilsISplines.*;
import static hex.genmodel.algos.gam.NBSplinesTypeI.extractNBSplineCoeffs;
import static hex.genmodel.algos.gam.NBSplinesTypeI.formBasisDeriv;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GamBasicISplineTest extends TestUtil {
    public static final double EPS = 1e-11;

    /***
     * Java unit test to make sure that gam I-spline beta constraint takes precedence over glm non-negative 
     * constraints.  All coefficients should be non-negative except the coefficients belonging to the I-spline
     * which in this case is specified to be non-positive.
     */
    @Test
    public void testNonNegative() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile("smalldata/gam_test/cosPp1X.csv");;
            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._train = train._key;
            params._response_column = "y2";
            params._non_negative = true;
            params._splines_non_negative = new boolean[]{false};
            params._gam_columns = new String[][]{{"x"}};
            params._bs = new int[]{2};
            params._ignored_columns = new String[]{"C1"};
            GAMModel gam = new GAM(params).trainModel().get();
            Scope.track_generic(gam);
            // make sure gamified columns coefficients have non-positive values
            double[] coeffs = gam._output._model_beta;
            // first coefficient belong to predictor "x"
            assertTrue(coeffs[0] >= 0); // non-negative
            // next three coefficients belong to gamified columns of "x"
            assertTrue(coeffs[1] <= 0);
            assertTrue(coeffs[2] <= 0);
            assertTrue(coeffs[3] <= 0);
        } finally {
            Scope.exit();
        }
    }

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
        for (int sizeIndex = 0; sizeIndex < nSizes.length; sizeIndex++) {
            for (int orderIndex = 0; orderIndex < nOrder.length; orderIndex++) {
                double[] knots = DoubleStream
                        .generate(ThreadLocalRandom.current()::nextDouble)
                        .limit(nSizes[sizeIndex]).sorted().toArray();  // knots need to be sorted
                double[] duplicatedKnots = fillKnots(knots, nOrder[orderIndex]);
                assertTrue(duplicatedKnots.length == nSizes[sizeIndex] + 2 * nOrder[orderIndex] - 2); // correct length
                for (int index = 1; index < nOrder[orderIndex]; index++) { // duplication at beginning and end are correct
                    assertTrue(Math.abs(duplicatedKnots[0] - duplicatedKnots[index]) < EPS);
                    assertTrue(Math.abs(duplicatedKnots[duplicatedKnots.length - 1] -
                            duplicatedKnots[duplicatedKnots.length - index - 1]) < EPS);
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
        for (int sizeIndex = 0; sizeIndex < nSizes.length; sizeIndex++) {
            for (int orderIndex = 0; orderIndex < nOrder.length; orderIndex++) {
                double[] knots = DoubleStream
                        .generate(ThreadLocalRandom.current()::nextDouble)
                        .limit(nSizes[sizeIndex]).sorted().toArray();  // knots need to be sorted
                double[] duplicatedKnots = fillKnots(knots, nOrder[orderIndex]);
                int numBasisFuncs = nSizes[sizeIndex] + nOrder[orderIndex] - 2;
                for (int basisIndex = 0; basisIndex < numBasisFuncs; basisIndex++) {
                    double[] basisKnots = extractKnots(basisIndex, nOrder[orderIndex], duplicatedKnots);
                    double[] manualKnots = extractKnotsManual(basisIndex, nOrder[orderIndex], duplicatedKnots);
                    assertArrayEquals(basisKnots, manualKnots, EPS);
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
        NBSplinesTypeII splineOrder1 = new NBSplinesTypeII(order, knots);
        NBSplineOrder manualOrder1 = new NBSplineOrder(order, knots);
        assertCorrectNBSpline(splineOrder1, manualOrder1, testValues);
        assertCorrectNBSpline(splineOrder1, manualOrder1, knots);

        order = 2;            // test for order 2
        NBSplinesTypeII splineOrder2 = new NBSplinesTypeII(order, knots);
        NBSplineOrder manualOrder2 = new NBSplineOrder(order, knots);
        assertCorrectNBSpline(splineOrder2, manualOrder2, testValues);
        assertCorrectNBSpline(splineOrder2, manualOrder2, knots);

        order = 3;            // test for order 3
        NBSplinesTypeII splineOrder3 = new NBSplinesTypeII(order, knots);
        NBSplineOrder manualOrder3 = new NBSplineOrder(order, knots);
        assertCorrectNBSpline(splineOrder3, manualOrder3, testValues);
        assertCorrectNBSpline(splineOrder3, manualOrder3, knots);

        order = 4;             // test for order 4
        NBSplinesTypeII splineOrder4 = new NBSplinesTypeII(order, knots);
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

    /**
     * test that correct coefficients are generated after invoking a NBSplineTypeI splines
     */
    @Test
    public void testNBSplineTypeICoeffs() {
        double[] knots = new double[]{-1, -0.6, -0.5, -0.3, 0, 0.3, 0.5, 0.6, 1};
        int order = 1;    // order 1 test
        double[][][] manualCoeff = new double[][][]{{{1 / 0.4}}, {null, {1 / 0.1}}, {null, null, {1 / 0.2}}, {null, null, null, {1 / 0.3}},
                {null, null, null, null, {1 / 0.3}}, {null, null, null, null, null, {1 / 0.2}}, {null, null, null, null, null, null, {1 / 0.1}},
                {null, null, null, null, null, null, null, {1 / 0.4}}};
        NBSplinesTypeI[] nbSplines = genNBSplines(knots, order);
        assertCorrectNBSplineCoeffs(manualCoeff, nbSplines);

        order = 2;
        manualCoeff = new double[][][]{{null, {7.5, 12.5}}, {null, {-10, -10}, {20, 40}},
                {null, null, {-40, -20 / 0.3}, {10, 10 / 0.3}}, {null, null, null, {-10, -20}, {0, 4 / 0.3}},
                {null, null, null, null, {-2 / 0.6, -2 / (0.3 * 0.6)}, {-2 / 0.6, 2 / (0.3 * 0.6)}},
                {null, null, null, null, null, {0, -4 / 0.3}, {-4 * 0.5 / 0.2, 20}},
                {null, null, null, null, null, null, {10, -10 / 0.3}, {-20 * 0.6 / 0.3, 20 / 0.3}}, {null, null, null, null, null, null, null, {20, -40},
                {-10, 10}}, {null, null, null, null, null, null, null, null, {0.6 * 12.5, -12.5}}};
        nbSplines = genNBSplines(knots, order);
        assertCorrectNBSplineCoeffs(manualCoeff, nbSplines);

        order = 3;
        double[] fullKnots = fillKnots(knots, order);
        nbSplines = genNBSplines(knots, order);
        manualCoeff = genManualCoeffsOrder2(fullKnots, nbSplines, order);
        assertCorrectNBSplineCoeffs(manualCoeff, nbSplines);
    }

    /***
     * Test to make sure correct coefficients are generated for derivatives of NBSplinesTypeI.
     * 
     */
    @Test
    public void testDerivativeCoeffs() {
        double[] knots = new double[]{-1, -0.6, -0.5, -0.3, 0, 0.3, 0.5, 0.6, 1};
        int order = 2;    // order 2 test, skip order = 1 because it has zero derivative
        double[] knotsWithDuplicates = fillKnots(knots, order);
        int numBasis = knots.length+order-2;
        double[][][] manualCoefs = new double[][][]{{null, {-12.5}}, {null, {10}, {-40}}, {null, null, {20/0.3}, 
                {-10/0.3}}, {null, null, null, {20},{-4/0.3}}, {null, null, null, null, {2/0.18},{-2/0.18}}, {null, 
                null, null, null, null, {4/0.3},{-4/0.2}}, {null, null, null, null, null, null, {2/0.06},{-2/0.03}},
                {null, null, null, null, null, null, null, {4/0.1},{-4/0.4}}, {null, null, null, null, null, null, 
                null, null, {5/0.4}}};
        NBSplinesTypeIDerivative[] allDerivatives = form1stOrderDerivatives(numBasis, order, knotsWithDuplicates);
        assertCorrectDerivativeCoeffs(allDerivatives, manualCoefs);
        
        order = 3;
        manualCoefs = new double[][][]{{null, null, {-56.25, -93.75}},
                {null, null, {17.5*3/0.5, 22.5*3/0.5}, {-20*3/0.5, -40*3/0.5}}, 
                {null, null, {-30/0.7, -30/0.7}, {60/0.7+120/0.7, 120/0.7+60/0.21}, {-30/0.7, -30/0.21}}, 
                {null, null, null, {-120/0.6, -60/0.18}, {30/0.6+30/0.6, 30/0.18+60/0.6}, {0,-12/0.18}}, 
                {null, null, null, null, {-30/0.8, -60/0.8}, {6/0.48, 12/0.24+6/(0.8*0.18)}, {6/0.48, -6/(0.18*0.8)}}, 
                {null, null, null, null, null, {-6/0.48, -6/(0.8*0.18)},{-6/0.48, 6/(0.8*0.18)+12/0.24},{6/0.16, -60/0.8}}, 
                {null, null, null, null, null, null,{0,-12/0.18},{-6/0.12-30/0.6, 60/0.6+30/0.18},{36/0.18, -60/0.18}},
                {null, null, null, null, null, null, null, {30/0.7, -30/0.21},{-36/0.21-60/0.7, 60/0.21+120/0.7}, {30/0.7, 
                -30/0.7}},
                {null, null, null, null, null, null, null, null,{60/0.5, -120/0.5},{-30/0.5-7.5*3/0.5, 
                30/0.5+12.5*3/0.5}}, 
                {null, null, null, null, null, null, null, null, null, {7.5*3/0.4, -12.5*3/0.4}}};
        knotsWithDuplicates = fillKnots(knots, order);
        allDerivatives = form1stOrderDerivatives(numBasis, order, knotsWithDuplicates);
        assertCorrectDerivativeCoeffs(allDerivatives, manualCoefs);
    }

    /**
     * Test the sum of integration of polynomial is correct.
     */
    @Test
    public void testIntegratePoly() {
       double[] knots = fillKnots(new double[]{-1, -0.6, -0.5, -0.3, 0}, 1);
        // test the sum of integration of 1 over -1, -0.6, 1-x over -0.6, -0.5, 1-x+x*x over -0.5, -0.3, 1-x+x*x-x*x*x 
        // over -0.3, 0
        double[][] coeffs = new double[][]{{1}, {1, -1}, {1, -1, 1}, {1, -1, 1, -1}};
        double correctAnswer = 0.4+0.155+(0.2-0.5*(0.3*0.3-0.5*0.5)+(-0.3*0.3*0.3+0.5*0.5*0.5)/3)+0.356025; // integrated by hand
        double integratedSum = integratePolynomial(knots, coeffs);
        assertTrue(Math.abs(correctAnswer-integratedSum) < EPS);
    }

    /**
     * test to check polynomial multiplication is correct.  This should give us confidence that formDerivateProduct
     * is also correct.
     */
    @Test
    public void testMultiplyPoly() {
        double[][] poly1 = new double[][]{{1}, {1,1}, {1, 1, -2}, {1, 1, -2}}; // null, 1+x, 1+x-2x*x, 1+x-2x*x
        double[][] poly2 = new double[][]{{0}, {1}, {1, -1}, {1, 1, -3, 4}};// 1+x, 1, 1+x, 1-x, 1+x-3x*x+4*x*x*x
        double[][] manualProduct = new double[][]{{0}, {1,1}, {1, 0, -3, 2}, {1,2,-4, -1, 10, -8}};
        int numPoly = poly1.length;
        for (int index=0; index<numPoly; index++) {
            double[] polyProduct = polynomialProduct(poly1[index], poly2[index]);
            double[] polyProductReverse = polynomialProduct(poly2[index], poly1[index]);
            assertArrayEquals(polyProduct, manualProduct[index], EPS);
            assertArrayEquals(polyProductReverse, manualProduct[index], EPS);
        }
    }

    /***
     * I have generated a bunch of splines using R toolbox splines2 for order = 2, 3, ..., 9 (yes, I overdid it again
     * by checking order upto 9.  The general guideline is to use splines with order no more than 3.  But there are 
     * always crazy people out there using splines more than 3....  I am verifying the higher orders for those people.)
     * Here, I am generating the same splines using the same R inputs but using our own I-splines.
     * The goal is to make sure the splines generated using R splines2 and our own Java implementation will
     * be the same.  
     */
    @Test
    public void testSplines2Compare() {
        String inputPath = "smalldata/gam_test/spline2input.csv";
        // test for order = 2 to 9
        compareRSplines2Results(2, "smalldata/gam_test/spline2Order2.csv", inputPath);
        compareRSplines2Results(3, "smalldata/gam_test/spline2Order3.csv", inputPath);
        compareRSplines2Results(4, "smalldata/gam_test/spline2Order4.csv", inputPath);
        compareRSplines2Results(5, "smalldata/gam_test/spline2Order5.csv", inputPath);
        compareRSplines2Results(6, "smalldata/gam_test/spline2Order6.csv", inputPath);
        compareRSplines2Results(7, "smalldata/gam_test/spline2Order7.csv", inputPath);
        compareRSplines2Results(8, "smalldata/gam_test/spline2Order8.csv", inputPath);
        compareRSplines2Results(9, "smalldata/gam_test/spline2Order9.csv", inputPath);
        
    }
    
    public static void compareRSplines2Results(int order, String resultFile, String inputFile) {
        try {
            Scope.enter();
            double[] knots = new double[]{0,0.3,0.5,0.6,1};
            ISplines ispline = new ISplines(order, knots);
            final Frame resultFrame = parseAndTrackTestFile(resultFile);
            int numRows = (int) resultFrame.numRows()-1;
            double[][] resultArray = MemoryManager.malloc8d((int) resultFrame.numRows(), resultFrame.numCols());
            resultArray = new ArrayUtils.FrameToArray(0, resultFrame.numCols()-1, resultFrame.numRows(),
                    resultArray).doAll(resultFrame).getArray();
            double[][] inputArray = MemoryManager.malloc8d((int) resultFrame.numRows(), 1); // first row is header
            final Frame inputFrame = parseAndTrackTestFile(inputFile);
            inputArray = new ArrayUtils.FrameToArray(0,0,resultFrame.numRows(),
                    inputArray).doAll(inputFrame).getArray(); // first row is data


            double[] iGamifiedValues = new double[ispline._numIBasis];
            for (int dataIndex = 0; dataIndex < numRows; dataIndex++) {
                ispline.gamifyVal(iGamifiedValues, inputArray[dataIndex][0]);
                assertArrayEquals(resultArray[dataIndex+1], iGamifiedValues, EPS);
            }
        } finally {
            Scope.exit();
        }
    }
    
    public static double[][][] genManualCoeffsOrder2(double[] fullKnots, NBSplinesTypeI[] nbSplines, int order) {
        int numBasis = nbSplines.length;
        double[][][] manualCoefs = new double[numBasis][][];
        for (int index = 0; index < numBasis; index++) {  // for each basis function
            manualCoefs[index] = new double[order + index][];
            // second index = 0
            if (Math.abs(fullKnots[index] - fullKnots[index + 1]) > EPS) { // take care of first segment
                double oneOverdenom1 = 1.0 / ((fullKnots[index + 3] - fullKnots[index]) *
                        (fullKnots[index + 2] - fullKnots[index]) *
                        (fullKnots[index + 1] - fullKnots[index]));
                manualCoefs[index][index] = new double[]{3 * fullKnots[index] * fullKnots[index] * oneOverdenom1,
                        -6 * fullKnots[index] * oneOverdenom1,
                        3 * oneOverdenom1};
            }
            // second index = 1
            if (Math.abs(fullKnots[index + 2] - fullKnots[index + 1]) > EPS) {
                double oneOverDenom21 = 1.0 / ((fullKnots[index + 2] - fullKnots[index + 1]) *
                        (fullKnots[index + 2] - fullKnots[index]) * (fullKnots[index + 3] - fullKnots[index]));
                double oneOverDenom22 = 1.0 / ((fullKnots[index + 3] - fullKnots[index + 1]) *
                        (fullKnots[index + 2] - fullKnots[index + 1]) * (fullKnots[index + 3] - fullKnots[index]));
                manualCoefs[index][index + 1] = new double[]{-3 * fullKnots[index] * fullKnots[index + 2] * oneOverDenom21
                        - 3 * fullKnots[index + 1] * fullKnots[index + 3] * oneOverDenom22, (3 * fullKnots[index + 2]
                        + 3 * fullKnots[index]) * oneOverDenom21 + (3 * fullKnots[index + 3] + 3 * fullKnots[index + 1])
                        * oneOverDenom22, -3 * (oneOverDenom21 + oneOverDenom22)};
            }
            // second index = 2
            if (Math.abs(fullKnots[index + 3] - fullKnots[index + 2]) > EPS) {
                double oneOverDenom3 = 1.0 / ((fullKnots[index + 3] - fullKnots[index]) * (fullKnots[index + 3] -
                        fullKnots[index + 2]) * (fullKnots[index + 3] - fullKnots[index + 1]));
                manualCoefs[index][index + 2] = new double[]{3 * fullKnots[index + 3] * fullKnots[index + 3] * oneOverDenom3,
                        -6 * fullKnots[index + 3] * oneOverDenom3, 3 * oneOverDenom3};
            }
        }
        return manualCoefs;
    }

    public void assertCorrectNBSplineCoeffs(double[][][] manualCoeffs, NBSplinesTypeI[] nbSplines) {
        int numBasis = nbSplines.length;
        for (int index = 0; index < numBasis; index++) {
            extractNBSplineCoeffs(nbSplines[index], nbSplines[index]._order, new double[]{1}, 1, index);
            double[][] coeffs = nbSplines[index]._nodeCoeffs;
            assert2DArrayEqual(coeffs, manualCoeffs[index]);
        }
    }
    
    public static void assert2DArrayEqual(double[][] coeff1, double[][] coeff2) {
        int arrayLen = coeff2.length;
        for (int index=0; index< arrayLen; index++)
                if (coeff1[index] == null)
                    assertTrue(coeff2[index] == null);
                else
                    assertArrayEquals(coeff2[index], coeff1[index], EPS);
    }
    public void assertCorrectDerivativeCoeffs(NBSplinesTypeIDerivative[] allDerivs, double[][][] manualCoeffs) {
        int numBasis = allDerivs.length;
        for (int index=0; index<numBasis; index++) {
            double[][] derivCoeffs = allDerivs[index]._coeffs;
            assert2DArrayEqual(derivCoeffs, manualCoeffs[index]);
        }
    }

    public NBSplinesTypeI[] genNBSplines(double[] knots, int order) {
        double[] fullKnots = fillKnots(knots, order);
        int numBasis = knots.length + order - 2;
        NBSplinesTypeI[] nbsplines = new NBSplinesTypeI[numBasis];
        for (int index = 0; index < numBasis; index++) {
            nbsplines[index] = formBasisDeriv(fullKnots, order, index, fullKnots.length - 1);
        }
        return nbsplines;
    }

    public void assertCorrectISpline(ISplines ispline, ISplineManual mspline, double[] data) {
        double[] iGamifiedValues = new double[ispline._numIBasis];
        for (int dataIndex = 0; dataIndex < data.length; dataIndex++) {
            double[] mGamifiedValues = mspline.evaluate(data[dataIndex]);
            ispline.gamifyVal(iGamifiedValues, data[dataIndex]);
            assertArrayEquals(mGamifiedValues, iGamifiedValues, EPS);
        }
    }

    public static double[] extractKnotsManual(int knotIndex, int order, double[] knots) {
        double[] extractedKnots = new double[order + 1];
        for (int index = 0; index <= order; index++) {
            extractedKnots[index] = knots[index + knotIndex];
        }
        return extractedKnots;
    }

    public static void assertCorrectNBSpline(NBSplinesTypeII bpSpline, NBSplineOrder manualSpline, double[] values) {
        double[] manualGamifiedValues = new double[manualSpline._numBasis];
        double[] gamifiedValues = new double[bpSpline._totBasisFuncs];
        for (int dataIndex = 0; dataIndex < values.length; dataIndex++) {
            for (int index = 0; index < manualSpline._numBasis; index++)    // manually formed gamified row
                manualGamifiedValues[index] = manualSpline._bSplines[index].evaluate(values[dataIndex]);
            bpSpline.gamify(gamifiedValues, values[dataIndex]);
            assertArrayEquals(manualGamifiedValues, gamifiedValues, EPS);
        }
    }

    /**
     * ISpline using manually derived formula
     */
    public class ISplineManual {
        public int _order;
        public double[] _knots;
        public int _numBasis;
        public int _totKnots;
        public ISplineBasisM[] _iSplines;

        public ISplineManual(int order, double[] knots) {
            _order = order;
            _knots = fillKnots(knots, order);
            _totKnots = _knots.length;
            _numBasis = knots.length + order - 2;
            _iSplines = new ISplineBasisM[_numBasis];
            for (int index = 0; index < _numBasis; index++)
                _iSplines[index] = new ISplineBasisM(order, _knots, index);
        }

        public double[] evaluate(double val) {
            double[] gamifiedVal = new double[_numBasis];
            for (int index = 0; index < _numBasis; index++) {
                gamifiedVal[index] = gamify(val, index);
            }
            return gamifiedVal;
        }

        public double gamify(double val, int basisInd) {
            double[] knots = _iSplines[basisInd]._knots;
            if (val < knots[0])
                return 0;
            if (val >= knots[_order])
                return 1;
            if (_order == 1) { // order of the ISpline
                if (val >= knots[0] && val < knots[1] && knots[1] != knots[0])
                    return (val - knots[0]) / (knots[1] - knots[0]);
            } else if (_order == 2) {
                if (val >= knots[0] && val < knots[1] && Math.abs(knots[1] - knots[0]) > 1e-12) {
                    return 2 * (val * val / 2 - knots[0] * knots[0] / 2 - knots[0] * (val - knots[0])) /
                            ((knots[2] - knots[0]) * (knots[1] - knots[0]));
                }
                if (val >= knots[1] && val < knots[2] && knots[2] != knots[1]) {
                    double temp = 0;
                    if (Math.abs(knots[1] - knots[0]) > 1e-12)
                        temp = 2 * (knots[1] * knots[1] / 2 - knots[0] * knots[0] / 2 - knots[0] *
                                (knots[1] - knots[0])) / ((knots[2] - knots[0]) * (knots[1] - knots[0]));
                    temp += 2 * (knots[2] * (val - knots[1]) - val * val / 2 + knots[1] * knots[1] / 2) /
                            ((knots[2] - knots[1]) * (knots[2] - knots[0]));
                    return temp;
                }
            } else if (_order == 3) {
                if (val < knots[0])
                    return 0;
                if (val >= knots[0] && val < knots[1] && knots[0] != knots[1])
                    return n4Part1(_iSplines[basisInd]._knots, val);
                if (val >= knots[1] && val < knots[2] && knots[1] != knots[2]) {
                    return n4Part2(_iSplines[basisInd]._knots, val) + n4Part1(_iSplines[basisInd]._knots, _iSplines[basisInd]._knots[1] - EPS / 10);
                }
                if (val >= knots[2] && val < knots[3] && knots[2] != knots[3]) {
                    return n4Part3(_iSplines[basisInd]._knots, val) + n4Part2(_iSplines[basisInd]._knots, _iSplines[basisInd]._knots[2] - EPS / 10)
                            + n4Part1(_iSplines[basisInd]._knots, _iSplines[basisInd]._knots[1] - EPS / 10);
                }
            }
            return 0;
        }

        public double n4Part1(double[] knots, double val) {
            if (val >= knots[0] && val < knots[1] && knots[1] != knots[0])
                return (val * val * val - knots[0] * knots[0] * knots[0] - 3 * knots[0] * (val * val - knots[0] * knots[0])
                        + 3 * knots[0] * knots[0] * (val - knots[0])) / ((knots[3] - knots[0]) * (knots[2] -
                        knots[0]) * (knots[1] - knots[0]));
            else
                return 0;
        }

        public double n4Part2(double[] knots, double val) {
            if (val >= knots[1] && val < knots[2]) {
                double denom1 = (knots[3] - knots[0]) * (knots[2] - knots[1]) * (knots[2] - knots[0]);
                double part1 = knots[2] == knots[1] ? 0 : (1.5 * knots[2] * (val * val - knots[1] * knots[1]) - 3 * knots[0] * knots[2]
                        * (val - knots[1]) - (val * val * val - knots[1] * knots[1] * knots[1]) + 1.5 * knots[0] *
                        (val * val - knots[1] * knots[1])) / denom1;
                double denom2 = (knots[3] - knots[1]) * (knots[2] - knots[1]) * (knots[3] - knots[0]);
                double part2 = knots[2] == knots[1] ? 0 : (1.5 * knots[3] * (val * val - knots[1] * knots[1]) -
                        (val * val * val - knots[1] * knots[1] * knots[1]) - 3 * knots[1] * knots[3] * (val - knots[1])
                        + 1.5 * knots[1] * (val * val -
                        knots[1] * knots[1]))
                        / denom2;

                return part1 + part2;
            } else {
                return 0;
            }
        }

        public double n4Part3(double[] knots, double val) {
            if (val >= knots[2] && val < knots[3] && Math.abs(knots[2]-knots[3])>EPS) {
                return (3 * knots[3] * knots[3] * (val - knots[2]) - 3 * knots[3] * (val * val - knots[2]
                        * knots[2]) + val * val * val - knots[2] * knots[2] * knots[2])
                        / ((knots[3] - knots[0]) * (knots[3] - knots[1])
                        * (knots[3] - knots[2]));
            } else {
                return 0;
            }
        }

        public class ISplineBasisM {
            public int _order;
            public double[] _knots;
            public int _basisInd;

            public ISplineBasisM(int order, double[] knots, int index) {
                _order = order;
                _basisInd = index;
                _knots = extractKnots(index, order, knots);
            }
        }
    }


    /**
     * Basis function here is generated manually using actual formula and no recursion
     */
    public class NBSplineOrder {
        public int _order;
        public double[] _knots; // knots with duplications at both ends
        public int _numBasis;
        public int _totKnots;   // number of knots with duplication
        public BSplineBasis[] _bSplines;

        public NBSplineOrder(int order, double[] knots) {
            _order = order;
            _totKnots = knots.length + 2 * order - 2;
            _numBasis = knots.length + order - 2;
            _knots = fillKnots(knots, order);
            _bSplines = new BSplineBasis[_numBasis];
            for (int index = 0; index < _numBasis; index++)
                _bSplines[index] = new BSplineBasis(_order, index, _knots);
        }

        public class BSplineBasis {
            public double[] _knots; // knots over which basis function is non-zero
            public int _order;

            public BSplineBasis(int order, int index, final double[] knots) {
                _order = order;
                _knots = extractKnots(index, order, knots);
            }

            public double evaluate(double value) {
                double gamifiedValue = 0;
                if (_order == 1) {
                    if (value >= _knots[0] && value < _knots[1])
                        return 1;
                } else if (_order == 2) {
                    if (value >= _knots[0] && value < _knots[1] && Math.abs(_knots[0]-_knots[1])>EPS)
                        return (value - _knots[0]) / (_knots[1] - _knots[0]);
                    else if (value >= _knots[1] && value < _knots[2] && Math.abs(_knots[1]-_knots[2])>EPS)
                        return (_knots[2] - value) / (_knots[2] - _knots[1]);
                } else if (_order == 3) {
                    if (value >= _knots[0] && value < _knots[1] && Math.abs(_knots[0]- _knots[1])>EPS)
                        return (value - _knots[0]) * (value - _knots[0])
                                / ((_knots[2] - _knots[0]) * (_knots[1] - _knots[0]));
                    if (value >= _knots[1] && value < _knots[2] && Math.abs(_knots[1]-_knots[2])>EPS) {
                        double part1 = (value - _knots[0]) * (_knots[2] - value)
                                / ((_knots[2] - _knots[0]) * (_knots[2] - _knots[1]));
                        double part2 = (_knots[3] - value) * (value - _knots[1])
                                / ((_knots[3] - _knots[1]) * (_knots[2] - _knots[1]));
                        return part1 + part2;
                    }
                    if (value >= _knots[2] && value < _knots[3] && Math.abs(_knots[2]-_knots[3])>EPS) {
                        return (_knots[3] - value) * (_knots[3] - value)
                                / ((_knots[3] - _knots[1]) * (_knots[3] - _knots[2]));
                    }
                } else if (_order == 4) {
                    if (value >= _knots[0] && value < _knots[1] && Math.abs(_knots[0]-_knots[1])>EPS)
                        return (value - _knots[0]) * (value - _knots[0]) * (value - _knots[0])
                                / ((_knots[3] - _knots[0]) * (_knots[2] - _knots[0]) * (_knots[1] - _knots[0]));
                    if (value >= _knots[1] && value < _knots[2] && (_knots[1] != _knots[2])) {
                        double part1 = (value - _knots[0]) * (value - _knots[0]) * (_knots[2] - value)
                                / ((_knots[3] - _knots[0]) * (_knots[2] - _knots[0]) * (_knots[2] - _knots[1]));
                        double part2 = (value - _knots[0]) * (value - _knots[1]) * (_knots[3] - value)
                                / ((_knots[3] - _knots[0]) * (_knots[3] - _knots[1]) * (_knots[2] - _knots[1]));
                        double part3 = (_knots[4] - value) * (value - _knots[1]) * (value - _knots[1])
                                / ((_knots[4] - _knots[1]) * (_knots[2] - _knots[1]) * (_knots[3] - _knots[1]));
                        return part1 + part2 + part3;
                    }
                    if (value >= _knots[2] && value < _knots[3] && Math.abs(_knots[2] - _knots[3]) > EPS) {
                        double part1 = (value - _knots[0]) * (_knots[3] - value) * (_knots[3] - value)
                                / ((_knots[3] - _knots[0]) * (_knots[3] - _knots[1]) * (_knots[3] - _knots[2]));
                        double part2 = (_knots[4] - value) * (value - _knots[1]) * (_knots[3] - value)
                                / ((_knots[4] - _knots[1]) * (_knots[3] - _knots[1]) * (_knots[3] - _knots[2]));
                        double part3 = (_knots[4] - value) * (value - _knots[2]) * (_knots[4] - value)
                                / ((_knots[4] - _knots[1]) * (_knots[4] - _knots[2]) * (_knots[3] - _knots[2]));
                        return part1 + part2 + part3;
                    }
                    if (value >= _knots[3] && value < _knots[4] && Math.abs(_knots[3] - _knots[4])>EPS)
                        return (_knots[4] - value) * (_knots[4] - value) * (_knots[4] - value)
                                / ((_knots[4] - _knots[1]) * (_knots[4] - _knots[2]) * (_knots[4] - _knots[3]));
                }
                return gamifiedValue;
            }
        }
    }
}
