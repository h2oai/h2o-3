package hex.glm;

import hex.DataInfo;
import org.apache.commons.math3.special.Gamma;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;
import water.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.IntStream;

import static hex.glm.GLMModel.GLMParameters.DispersionMethod.pearson;
import static hex.glm.GLMModel.GLMParameters.Family.tweedie;
import static org.apache.commons.math3.special.Gamma.logGamma;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMTweedieDispersionOnlyTest extends TestUtil {
    
    @Test
    public void testTweedieDispersionPLess2() {
        Scope.enter();
        try {
            // test with variance power > 1 and < 2
            Frame train2 = parseAndTrackTestFile("smalldata/glm_test/tweedie_1p8Power_2Dispersion_5Col_10KRows.csv");
            assertMLBetterThanPearson(train2, 2, 1.8, 1.5, "resp");
            assertMLBetterThanPearson(train2, 2, 1.8, 2.5, "resp");
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testTweedieDispersionPExceed2p3phi0p5() {
        Scope.enter();
        try {
            // test with variance power > 2
            Frame train = parseAndTrackTestFile("smalldata/glm_test/tweedie_p3_phi0p5_10KRows.csv");
            assertMLBetterThanPearson(train, 0.5, 3, 0.1, "x");
            assertMLBetterThanPearson(train, 0.5, 3, 1.5, "x");
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testTweedieDispersionPExceed2P3phi1() {
        Scope.enter();
        try {
            // test with variance power > 2
            Frame train = parseAndTrackTestFile("smalldata/glm_test/tweedie_p3_phi1_10KRows.csv");
            assertMLBetterThanPearson(train, 1, 3, 1.5, "x");
            assertMLBetterThanPearson(train, 1, 3, 0.5, "x");
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testTweedieDispersionPExceed2p3phi1p5() {
        Scope.enter();
        try {
            // test with variance power > 2
            Frame train = parseAndTrackTestFile("smalldata/glm_test/tweedie_p3_phi1p5_10KRows.csv");
            assertMLBetterThanPearson(train, 1.5, 3, 0.5, "x");
            assertMLBetterThanPearson(train, 1.5, 3, 2.0, "x");
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testTweedieDispersionPExceed2P5Phi0p5() {
        Scope.enter();
        try {
            // test with variance power > 2
            Frame train = parseAndTrackTestFile("smalldata/glm_test/tweedie_p5_phi0p5_10KRows.csv");
            assertMLBetterThanPearson(train, 0.5, 5, 0.1, "x");
            assertMLBetterThanPearson(train, 0.5, 5, 1.5, "x");
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testTweedieDispersionPExceed2P5Phi1() {
        Scope.enter();
        try {
            // test with variance power > 2
            Frame train = parseAndTrackTestFile("smalldata/glm_test/tweedie_p5_phi1_10KRows.csv");
            assertMLBetterThanPearson(train, 1, 5, 0.1, "x");
            assertMLBetterThanPearson(train, 1, 5, 1.5, "x");
        } finally {
            Scope.exit();
        }
    }
    
    public void assertMLBetterThanPearson(Frame train, double trueDispersion, double variance_power, double initDisp,
                                          String resp) {
        Scope.enter();
        try {
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._tweedie_variance_power=variance_power;
            params._family = tweedie;
            params._fix_tweedie_variance_power = true;
            params._debugTDispersionOnly = false;
            params._dispersion_parameter_method = GLMModel.GLMParameters.DispersionMethod.ml;
            params._compute_p_values = true;
            params._lambda = new double[]{0.0};
            params._response_column = resp;
            
            params._train = train._key;
            params._init_dispersion_parameter = initDisp;
            params._remove_collinear_columns = true;
            GLMModel glmML = new GLM(params).trainModel().get();
            Scope.track_generic(glmML);
            
            params._fix_tweedie_variance_power = false;
            params._dispersion_parameter_method = pearson;
            GLMModel glmPearson = new GLM(params).trainModel().get();
            Scope.track_generic(glmPearson);
            Log.info("ML dispersion "+glmML._output.dispersion());
            Log.info("Pearson dispersionm "+glmPearson._output.dispersion());
            Log.info("true dispersion "+trueDispersion);
            Assert.assertTrue(Math.abs(trueDispersion-glmML._output.dispersion()) < 
                    Math.abs(trueDispersion-glmPearson._output.dispersion()));
        } finally {
            Scope.exit();
        }
    }

    /***
     * This test is used to generate a plot of dispersion parameter over the maximum likelihood function to see where
     * the maximum is.  It is not to be used as a regular test.
     */
    @Ignore
    public void testGenerateLogllVersusDispersion() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile("smalldata/glm_test/tweedie_1p8Power_2Dispersion_5Col_10KRows.csv");
            //Frame train = parseAndTrackTestFile("smalldata/glm_test/tweedie_p2p5_phi2p5_5Cols_10KRows.csv");
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._tweedie_variance_power=1.8;
            params._family = tweedie;
            params._fix_tweedie_variance_power = true;
            params._debugTDispersionOnly = true;
            params._dispersion_parameter_method = pearson;
            params._compute_p_values = true;
            params._lambda = new double[]{0.0};
            params._remove_collinear_columns=true;
            params._response_column = "resp";
           // params._response_column = "x";
            params._train = train._key;
            GLMModel glmML = new GLM(params).trainModel().get();
            Scope.track_generic(glmML);
            double startVal = 0.1;
            double endVal = 3;
            double interval = 0.1;
            int listLength = (int) Math.ceil((endVal-startVal)/interval);
            double[] dispersionList = IntStream.rangeClosed(0, listLength).mapToDouble(x -> x*interval+startVal).toArray();
            double[] logll = new double[dispersionList.length];
            double[][] dispersionLogLL = new double[logll.length][7];   // dispersion, ll, nobs
            DataInfo dinfo = new DataInfo(params.train().clone(), null, 1, 
                    params._use_all_factor_levels || params._lambda_search, params._standardize ? 
                    DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
                    params.missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.Skip,
                    params.imputeMissing(), params.makeImputer(), false, false, false,
                    false, params.interactionSpec());
            TweedieMLDispersionOnly mlDisp = new TweedieMLDispersionOnly(train, params, glmML, 
                    glmML.beta(), dinfo);
            for (int index=0; index < logll.length; index++) {
                mlDisp.updateDispersionP(dispersionList[index]);
                DispersionTask.ComputeMaxSumSeriesTsk computeTask = new DispersionTask.ComputeMaxSumSeriesTsk(
                        mlDisp, params, false);
                computeTask.doAll(mlDisp._infoFrame);   // generated info columns
                logll[index] = computeTask._logLL;
                dispersionLogLL[index][0] = dispersionList[index];
                dispersionLogLL[index][1] = logll[index]/computeTask._nobsLL;
                dispersionLogLL[index][2] = computeTask._nobsLL;
                dispersionLogLL[index][3] = computeTask._nobsDLL;
                dispersionLogLL[index][4] = computeTask._nobsD2LL;
                dispersionLogLL[index][5] = computeTask._dLogLL;
                dispersionLogLL[index][6] = computeTask._d2LogLL;
            }
            TestUtil.writeFrameToCSV("/Users/wendycwong/temp/infoFrame.csv", mlDisp._infoFrame, true, false);
            mlDisp.cleanUp();
            Frame dispLogll = generateRealOnly(dispersionLogLL[0].length, logll.length, 0);
            new ArrayUtils.CopyArrayToFrame(0,dispersionLogLL[0].length-1, logll.length, dispersionLogLL).doAll(dispLogll);
            TestUtil.writeFrameToCSV("/Users/wendycwong/temp/dispersionLogLL.csv", dispLogll, true, false);
            Scope.track(dispLogll);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            Scope.exit();
        }
    }

    /***
     * This test is used to generate the V(k) and the Venv(k)
     */
    @Test
    public void testGenVK() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile("smalldata/glm_test/tweedie_p2p5_phi2p5_5Cols_10KRows.csv");
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._tweedie_variance_power = 2.5;
            params._family = tweedie;
            params._fix_tweedie_variance_power = true;
            params._init_dispersion_parameter = 2.5;
            params._debugTDispersionOnly = true;
            params._dispersion_parameter_method = GLMModel.GLMParameters.DispersionMethod.ml;
            params._compute_p_values = true;
            params._lambda = new double[]{0.0};
            params._response_column = "resp";
            params._train = train._key;
            GLMModel glmML = new GLM(params).trainModel().get();
            Scope.track_generic(glmML);
            DataInfo dinfo = new DataInfo(params.train().clone(), null, 1,
                    params._use_all_factor_levels || params._lambda_search, params._standardize ?
                    DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
                    params.missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.Skip,
                    params.imputeMissing(), params.makeImputer(), false, false, false,
                    false, params.interactionSpec());
            TweedieMLDispersionOnly mlDisp = new TweedieMLDispersionOnly(train, params, glmML, 
                    glmML.beta(), dinfo);
            int kMax = 100;
            double[] venvk = new double[kMax+1];
            double[] vk = new double[101];
            for (int index=0; index < kMax; index++) {

                DispersionTask.ComputeMaxSumSeriesTsk computeTask = new DispersionTask.ComputeMaxSumSeriesTsk(
                        mlDisp, params, true);
                computeTask.doAll(mlDisp._infoFrame);   // generated info columns

            }
            mlDisp.cleanUp();
        } finally {
            Scope.exit();
        }
    }
    
    /***
     * This test is written to make sure working columns generated is correct for variance power p, 1<p<2 and p>2
     */
    @Test
    public void testInfoColGeneration() {
        Scope.enter();
        try {
            final Frame trainL2 = parseAndTrackTestFile("smalldata/glm_test/tweedie_5Cols_500Rows_power_1p5_phi_0p5.csv");
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._tweedie_variance_power = 1.5;
            params._response_column = "resp";
            params._debugTDispersionOnly = true;
            assertCorrectInfoColGeneration(trainL2, params, 1e-6, true, (int) trainL2.numRows());
            // test for p > 2
            final Frame trainExceed2 = parseAndTrackTestFile("smalldata/glm_test/tweedie_5Cols_500Rows_power_2p1_phi_0p5.csv");
            params._tweedie_variance_power = 2.1;
            assertCorrectInfoColGeneration(trainExceed2, params, 2e-1, true, (int) trainL2.numRows());
        } finally {
            Scope.exit();
        }
    }

    /***
     * This test is used to make sure that the various infoFrame columns are correctly generated for some new 
     * Tweedie datasets
     */
    @Test
    public void testInfoColGeneration2() {
        Scope.enter();
        try {
            final Frame trainL2 = parseAndTrackTestFile("smalldata/glm_test/tweedie_p3_phi1_10KRows.csv");
            int rows2Check = 500;
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._tweedie_variance_power = 3;
            // run with dispersion parameter estimate lower than true dispersion parameter
            params._init_dispersion_parameter = 0.1;
            params._response_column = "x";
            params._debugTDispersionOnly = true;
            assertCorrectInfoColGeneration(trainL2, params, 2, false, rows2Check);
            // run with dispersion parameter estimate higher than true dispersion parameter
            params._init_dispersion_parameter = 2;
            assertCorrectInfoColGeneration(trainL2, params, 2, false, rows2Check);
            
            params._init_dispersion_parameter = 1;
            assertCorrectInfoColGeneration(trainL2, params, 2, false, rows2Check);
            
            final Frame trainExceed2 = parseAndTrackTestFile("smalldata/glm_test/tweedie_p5_phi0p5_10KRows.csv");
            params._tweedie_variance_power = 5;
            params._init_dispersion_parameter = 0.1;
            // in this case, at row index=169, the sumWV differs due to difference in Math.sin at index=52.
            // one will give Math.sin(-122.52211349000194)=5.882018519726826E-15 and the other will give
            // Math.sin(-122.52211349000194)=1.224646799e-16.  I have no control over this at this point.  This
            // are all different from the solution from R.
            assertCorrectInfoColGeneration(trainExceed2, params, 2, false, rows2Check);

            params._init_dispersion_parameter = 1.1;
            assertCorrectInfoColGeneration(trainExceed2, params, 2e-1, false, rows2Check);

            params._init_dispersion_parameter = 0.5;
            assertCorrectInfoColGeneration(trainExceed2, params, 2e-1, false, rows2Check);
            
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testInfoColGenerationWithWeight() {
        Scope.enter();
        try {
            final Frame trainL2 = parseAndTrackTestFile("smalldata/glm_test/tweedie_5Cols_500Rows_power_1p5_phi_0p5.csv");
            assertCorrectInfoColWithWeight(trainL2);

            final Frame trainE2 = parseAndTrackTestFile("smalldata/glm_test/tweedie_5Cols_500Rows_power_2p1_phi_0p5.csv");
            assertCorrectInfoColWithWeight(trainE2);
        } finally {
            Scope.exit();
        }
    }
    
    public void assertCorrectInfoColWithWeight(Frame train) {
        GLMModel.GLMParameters params = new GLMModel.GLMParameters();
        params._tweedie_variance_power = 1.5;
        params._response_column = "resp";
        params._debugTDispersionOnly = true;
        params._train = train._key;
        params._compute_p_values = true;
        params._dispersion_parameter_method = pearson;
        params._family = GLMModel.GLMParameters.Family.tweedie;
        params._lambda = new double[]{0.0};
        GLMModel glmML = new GLM(params).trainModel().get();
        Scope.track_generic(glmML);
        DataInfo dinfo = new DataInfo(params.train().clone(), null, 1,
                params._use_all_factor_levels || params._lambda_search, params._standardize ?
                DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
                params.missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.Skip,
                params.imputeMissing(), params.makeImputer(), false, false, false,
                false, params.interactionSpec());
        TweedieMLDispersionOnly mlDisp = new TweedieMLDispersionOnly(train, params, glmML, glmML.beta(), dinfo);
        DispersionTask.ComputeMaxSumSeriesTsk computeTask = new DispersionTask.ComputeMaxSumSeriesTsk(
                mlDisp, params, true);
        computeTask.doAll(mlDisp._infoFrame);   // generated info columns
        DKV.put(mlDisp._infoFrame);

        Vec vecOf1 = Vec.makeCon(1.0, train.numRows());
        train.add("weight", vecOf1);
        DKV.put(train);
        params._weights_column = "weight";
        GLMModel glmMLW = new GLM(params).trainModel().get();
        Scope.track_generic(glmMLW);
        
        TweedieMLDispersionOnly mlDispW = new TweedieMLDispersionOnly(train, params, glmMLW,
                glmMLW.beta(), dinfo);
        DispersionTask.ComputeMaxSumSeriesTsk computeTaskW = new DispersionTask.ComputeMaxSumSeriesTsk(
                mlDispW, params, true);
        computeTaskW.doAll(mlDispW._infoFrame);   // generated info columns
        DKV.put(mlDispW._infoFrame);
        for (int cInd=2; cInd<mlDisp._infoFrame.numCols(); cInd++) {
            TestUtil.assertVecEquals(mlDisp._infoFrame.vec(cInd), mlDispW._infoFrame.vec(cInd+1), 1e-7);
        }
        Scope.track(mlDisp._infoFrame);
        Scope.track(mlDispW._infoFrame);
        mlDisp.cleanUp();
        mlDispW.cleanUp();
    }

    /***
     * This test is written to make sure the constant columns generation is correct for variance power p, 1 < p < 2 and
     * p > 2.
     */
    @Test
    public void testConstColumnGeneration() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile("smalldata/glm_test/tweedie_5Cols_500Rows_power_1p2_phi_0p5.csv");
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._tweedie_variance_power = 1.2;
            params._response_column = "resp";
            assertConstColGeneration(train, params);
            // test for p > 2
            final Frame trainExceed2 = parseAndTrackTestFile("smalldata/glm_test/tweedie_5Cols_500Rows_power_2p1_phi_0p5.csv");
            params._tweedie_variance_power = 2.1;
            assertConstColGeneration(trainExceed2, params);
            
            // test with weight columns of 1.0.  Should provide the same result, 1<p<2
            Vec vecOf1 = Vec.makeCon(1.0, train.numRows());
            train.add("weight", vecOf1);
            DKV.put(train);
            params._weights_column = "weight";
            assertConstColGeneration(train, params);
            // test with weight and p>2
            trainExceed2.add("weight", vecOf1);
            DKV.put(trainExceed2);
            params._weights_column = "weight";
            assertConstColGeneration(train, params);
        } finally {
            Scope.exit();
        }
    }

    public void assertCorrectInfoColGeneration(Frame train, GLMModel.GLMParameters params, double tot, 
                                               boolean checkLLDLLD2LL, int numRows) {
        params._train = train._key;
        params._compute_p_values = true;
        params._dispersion_parameter_method = pearson;  // chosen to speed up test
        params._family = GLMModel.GLMParameters.Family.tweedie;
        params._lambda = new double[]{0.0};
        GLMModel glmML = new GLM(params).trainModel().get();
        Scope.track_generic(glmML);
        DataInfo dinfo = new DataInfo(params.train().clone(), null, 1,
                params._use_all_factor_levels || params._lambda_search, params._standardize ?
                DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
                params.missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.Skip,
                params.imputeMissing(), params.makeImputer(), false, false, false,
                false, params.interactionSpec());
        TweedieMLDispersionOnly mlDisp = new TweedieMLDispersionOnly(train, params, glmML, glmML.beta(), dinfo);
        DispersionTask.ComputeMaxSumSeriesTsk computeTask = new DispersionTask.ComputeMaxSumSeriesTsk(
                mlDisp, params, true);
        computeTask.doAll(mlDisp._infoFrame);   // generated info columns
        DKV.put(mlDisp._infoFrame);
        compareInfoColFrame(computeTask, params, mlDisp, tot, checkLLDLLD2LL, numRows);
        mlDisp.cleanUp();
    }

    public void compareInfoColFrame(DispersionTask.ComputeMaxSumSeriesTsk computeTsk, GLMModel.GLMParameters parms,
                                    TweedieMLDispersionOnly mlDisp, double tot, boolean compareLLDLLD2LL, int numRows) {
        Frame infoColFrame = mlDisp._infoFrame;
        Scope.track(infoColFrame);
        String[] infoColNames = mlDisp._workFrameNames;
        int infoColNum = infoColNames.length;
        double[] manualInfoColsRow = new double[infoColNum];
        double[] infoColsRow = new double[infoColNum];
        double loglikelihood = 0;
        double dLoglikelihood = 0;
        double d2Loglikelihood = 0;
        int lastInd = infoColNum-1;
        
        int offset = mlDisp._weightPresent ? 3+mlDisp._constFrameNames.length : 2+mlDisp._constFrameNames.length;
        for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
            extractFrame2Array(infoColsRow, infoColFrame, offset, rowIndex);
            manuallyGenerateInfoCols(manualInfoColsRow, mlDisp, parms, rowIndex, infoColsRow);
            if (compareLLDLLD2LL) {
                if (Double.isFinite(manualInfoColsRow[lastInd - 2]))
                    loglikelihood += manualInfoColsRow[lastInd - 2];
                if (Double.isFinite(manualInfoColsRow[lastInd - 1]))
                    dLoglikelihood += manualInfoColsRow[lastInd - 1];
                if (Double.isFinite(manualInfoColsRow[lastInd]))
                    d2Loglikelihood += manualInfoColsRow[lastInd];
            }

            Assert.assertTrue(TestUtil.equalTwoArrays(manualInfoColsRow, infoColsRow, tot));
        }
        // check loglikelihood, dloglikelihood and d2loglikelihood sums
        if (compareLLDLLD2LL) {
            if (Double.isNaN(computeTsk._logLL))
                Assert.assertTrue(Double.isNaN(loglikelihood));
            else
                Assert.assertTrue(Math.abs(Math.round(loglikelihood) - Math.round(computeTsk._logLL)) < tot);
            if (Double.isNaN(computeTsk._dLogLL))
                Assert.assertTrue(Double.isNaN(dLoglikelihood));
            else
                Assert.assertTrue(Math.abs(Math.round(dLoglikelihood) - Math.round(computeTsk._dLogLL)) < tot);
            if (Double.isNaN(computeTsk._d2LogLL))
                Assert.assertTrue(Double.isNaN(d2Loglikelihood));
            else
                Assert.assertTrue(Math.abs(Math.round(d2Loglikelihood) - Math.round(computeTsk._d2LogLL)) < tot);
        }
    }
    
    public void manuallyGenerateInfoCols(double[] manualInfoCols, TweedieMLDispersionOnly mlDisp,
                                         GLMModel.GLMParameters params, int rowInd, double[] infoColRow) {
        Frame respMu = mlDisp._infoFrame;
        Scope.track(respMu);
        double resp = respMu.vec(0).at(rowInd);
        double mu = respMu.vec(1).at(rowInd);
        double weight = mlDisp._weightPresent ? respMu.vec(2).at(rowInd) : 1;
        double p = params._tweedie_variance_power;    
        double phi = mlDisp._dispersionParameter;
        double alpha = (2.0-p)/(1.0-p);
        if (!Double.isNaN(resp)) {
            // generate jKMaxIndex
            double jkMax = resp == 0 ? 0 : Math.max(1, Math.ceil(p < 2 ? weight*Math.pow(resp, 2-p)/((2-p)*phi) : 
                    weight*Math.pow(resp, 2-p)/((p-2)*phi)));
            manualInfoCols[0] = jkMax;
            // generate logZ
            double logZ = resp == 0 ? 0 : Math.log(p<2 ?
                    Math.pow(resp, -alpha)*Math.pow(p-1, alpha)*Math.pow(weight, 1-alpha)/((2-p)*Math.pow(phi, 1-alpha))
                    : Math.pow(resp, -alpha)*Math.pow(p-1, alpha)*Math.pow(weight, 1-alpha)/((p-2)*Math.pow(phi, 1-alpha)));
            manualInfoCols[1] = logZ;
            // generate logWVMax excluding 1/y or 1/(PI*y)
            manualInfoCols[2] = resp == 0 ? 0 : (p<2 ? jkMax*logZ-Gamma.logGamma(1+jkMax)- Gamma.logGamma(-alpha*jkMax) :
                    jkMax*logZ+ Gamma.logGamma(1+alpha*jkMax)-Gamma.logGamma(1+jkMax));
            // generate dlogWVMax without the alphaMinusOneOverPhi and the 1/y or 1/(PI*y)
            manualInfoCols[3] = resp == 0 ? 0 : Math.log(jkMax)+manualInfoCols[2];
            // generate d2logWVMax without 1/y or 1/(PI*y) and the oneMinusAlpha/PhiSquare and alphaMinusOne Square over PhiSquare
            manualInfoCols[4] = resp == 0 ? 0 : Math.log(jkMax) + manualInfoCols[3];
            // verify that the lower and upper bounds jkL, jkU, djkL, djkU are generated correctly by making sure the
            // indices are where the lower and upper bounds should be
            if (resp != 0)
                assertCorrectBounds(infoColRow[5], infoColRow[6], infoColRow[7], infoColRow[8], infoColRow[9], 
                        infoColRow[10], manualInfoCols[1], alpha, p, Math.log(params._tweedie_epsilon), (int) jkMax);
            System.arraycopy(infoColRow, 5, manualInfoCols, 5, 6);
            // cal LL, DLL, D2LL
            double sumWV2 = resp == 0 ? 0 : calWVSum2(p, logZ, alpha, (int) manualInfoCols[5], (int) manualInfoCols[6],
                    resp, manualInfoCols[2]); // with 1/y or 1/(PI*y)
            manualInfoCols[11] = sumWV2*(p < 2 ? resp : (Math.PI*resp)); // without 1/y or 1/(PI*y)
            double part2 = -Math.pow(mu, 2-p)/(phi*(2-p));
            manualInfoCols[14] = resp==0 ? part2 : resp*Math.pow(mu, 1-p)/((1-p)*phi)+part2+Math.log(sumWV2); // ll
            double sumDWV = resp == 0 ? 0 : calDWV(p, logZ, alpha, (int) manualInfoCols[7], (int) manualInfoCols[8], phi, manualInfoCols[3]);
            manualInfoCols[12] = sumDWV; // without 1/y or 1/(PI*y)
            double dWOverW = resp == 0 ? 0 : sumDWV/Math.abs(manualInfoCols[11]);
            double dpart2 = Math.pow(mu, 2-p)/(phi*phi*(2-p));
            manualInfoCols[15] = resp==0?dpart2:dpart2+dWOverW+resp*Math.pow(mu,1-p)/(phi*phi*(p-1)); // dll
           double d2part2 = -2*Math.pow(mu, 2-p)/(phi*phi*phi*(2-p));
           double sumD2WV = resp==0 ? 0 : calD2WV(p, logZ, alpha, (int) manualInfoCols[9], (int) manualInfoCols[10], phi);
           manualInfoCols[13] = sumD2WV;
           double d2WVOverdPhi = resp==0 ? 0 : (manualInfoCols[11]*sumD2WV-sumDWV*sumDWV)/(manualInfoCols[11]*manualInfoCols[11]);
           // d2ll
           manualInfoCols[16] = resp==0 ? d2part2 : -2*resp*Math.pow(mu, 1-p)/(phi*phi*phi*(p-1))+d2part2+d2WVOverdPhi;
        }
    }

    public double calD2WV(double vPower, double logZ, double alpha, int djkL, int djkU, double phi) {
        double sumD2WV = 0;
        double sumD2WVPart1 = 0.0;
        double sumD2WVPart2 = 0.0;
        if (vPower < 2) {
            for (int index = djkL; index <= djkU; index++) {
                sumD2WVPart1 += Math.exp(calD2LogWV(vPower, logZ, alpha, index));
                sumD2WVPart2 += Math.exp(calDLogWV(vPower, logZ, alpha, index));
                sumD2WV += Math.exp(calD2LogWV(vPower, logZ, alpha, index)) * (1 - alpha) * (1 - alpha) / (phi * phi) +
                        Math.exp(calDLogWV(vPower, logZ, alpha, index)) * (1 - alpha) / (phi * phi);
            }
            sumD2WV = sumD2WVPart1*(1 - alpha) * (1 - alpha) / (phi * phi)+sumD2WVPart2*(1 - alpha) / (phi * phi);
        } else {
            for (int index = djkL; index <= djkU; index++) {
                sumD2WVPart1 += Math.exp(calD2LogWV(vPower, logZ, alpha, index)) * Math.pow(-1, index) *
                        Math.sin(-index * Math.PI * alpha);
                sumD2WVPart2 += Math.exp(calDLogWV(vPower, logZ, alpha, index)) * Math.pow(-1, index) *
                        Math.sin(-index * Math.PI * alpha);
                sumD2WV += Math.exp(calD2LogWV(vPower, logZ, alpha, index)) * Math.pow(-1, index) *
                        Math.sin(-index * Math.PI * alpha) * (1 - alpha) * (1 - alpha) / (phi * phi) +
                        Math.exp(calDLogWV(vPower, logZ, alpha, index)) * Math.pow(-1, index) *
                                Math.sin(-index * Math.PI * alpha) * (1 - alpha) / (phi * phi);
            }
            sumD2WV = sumD2WVPart1*(1 - alpha) * (1 - alpha) / (phi * phi)+sumD2WVPart2*(1 - alpha) / (phi * phi);
        }
        return sumD2WV;
    }
    
    public double calDWV(double vPower, double logZ, double alpha,  int DjkL, int DjkU, 
                              double phi) {
        double sumDWV = 0;
        if (vPower < 2) {
            for (int index=DjkL; index <= DjkU; index++)
                sumDWV += Math.exp(calDLogWV(vPower, logZ, alpha, index))*(alpha-1)/phi;
        } else {
            for (int index=DjkL; index<=DjkU; index++)
                sumDWV += Math.exp(calDLogWV(vPower, logZ, alpha, index))*Math.pow(-1, index)*Math.sin(-index*Math.PI*alpha)*(alpha-1)/phi;
        }
        return sumDWV;
    }

    public double calDWV(double vPower, double logZ, double alpha, int DjkL, int DjkU,
                         double phi, double logDWVMax) {
        double sumDWV = 0;
        if (vPower < 2) {
            for (int index=DjkL; index <= DjkU; index++)
                sumDWV += Math.exp(calDLogWV(vPower, logZ, alpha, index)-logDWVMax); // * (alpha - 1) / phi;
            sumDWV = Math.exp(Math.log(sumDWV)+logDWVMax)*(alpha-1)/phi;    
        } else {
            for (int index=DjkL; index<=DjkU; index++)
                sumDWV += Math.exp(calDLogWV(vPower, logZ, alpha, index)-logDWVMax)*Math.pow(-1, index)*Math.sin(-index*Math.PI*alpha);//*(alpha-1)/phi;
            if (sumDWV > 0)
                sumDWV = Math.exp(Math.log(sumDWV)+logDWVMax)*(alpha-1)/phi;
            else
                sumDWV = sumDWV*Math.exp(logDWVMax)*(alpha-1)/phi;
        }
        return sumDWV;
    }
    
    public double calWVSum(double vPower, double logZ, double alpha, int jkL, int jkU, double resp) {
        double aYPhi = 0;
        if (vPower < 2) {
            for (int index=jkL; index <= jkU; index++)
                aYPhi += Math.exp(calLogWV(vPower, logZ, alpha, index));
            return aYPhi / resp;
        } else {
            for (int index=jkL; index<=jkU; index++)
                aYPhi += Math.exp(calLogWV(vPower, logZ, alpha, index))*Math.pow(-1, index)*Math.sin(-index*Math.PI*alpha);
            return aYPhi / (resp * Math.PI);
        }
    }

    public double calWVSum2(double vPower, double logZ, double alpha, int jkL, int jkU, double resp, double logWVMax) {
        double aYPhi = 0;
        if (vPower < 2) {
            for (int index=jkL; index <= jkU; index++)
                aYPhi += Math.exp(calLogWV(vPower, logZ, alpha, index)-logWVMax);
            return aYPhi*Math.exp(logWVMax) / resp;
        } else {
            for (int index=jkL; index<=jkU; index++) {
                Log.info("index "+index+" value: "+Math.exp(calLogWV(vPower, logZ, alpha, index) - logWVMax) * 
                        Math.pow(-1, index) * Math.sin(-index * Math.PI * alpha));
                aYPhi += Math.exp(calLogWV(vPower, logZ, alpha, index) - logWVMax) * Math.pow(-1, index) * Math.sin(-index * Math.PI * alpha);
            }
            return aYPhi*Math.exp(logWVMax) / (resp * Math.PI);
        }
    }
    
    public void assertCorrectBounds(double jkL, double jkU, double DjkL, double DjkU, double D2jkL, double D2jkU, 
                                    double logZ, double alpha, 
                                    double vPower, double logEpsilon, int jkMax) {
        // check jkL, jkU
        double logWVMax = calLogWV(vPower, logZ, alpha, jkMax);
        if (jkL > 1)
            assertCorrectLowerBound(calLogWV(vPower, logZ, alpha, (int) jkL)-logWVMax, 
                    calLogWV(vPower, logZ, alpha, (int) jkL+1)-logWVMax, logEpsilon);
        if (jkU > 1)
            assertCorrectUpperBound(calLogWV(vPower, logZ, alpha, (int) jkU)-logWVMax,
                calLogWV(vPower, logZ, alpha, (int) jkU-1)-logWVMax, logEpsilon);
        
        // check DjkL, DjkU
        double dlogWVMax = calDLogWV(vPower, logZ, alpha, jkMax);
        if (DjkL > 1)
            assertCorrectLowerBound(calDLogWV(vPower, logZ, alpha, (int) DjkL)-dlogWVMax,
                    calDLogWV(vPower, logZ, alpha, (int) DjkL+1)-dlogWVMax, logEpsilon);
        if (DjkU > 1)
            assertCorrectUpperBound(calDLogWV(vPower, logZ, alpha, (int) DjkU)-dlogWVMax,
                    calDLogWV(vPower, logZ, alpha, (int) DjkU-1)-dlogWVMax, logEpsilon);
        // check DjkL, DjkU for d2
        double d2logWVMax = calD2LogWV(vPower, logZ, alpha, jkMax);
        if (D2jkL > 1)
            assertCorrectLowerBound(calD2LogWV(vPower, logZ, alpha, (int) D2jkL)-d2logWVMax,
                    calD2LogWV(vPower, logZ, alpha, (int) D2jkL+1)-d2logWVMax, logEpsilon);
        if (D2jkU > 1) {
            assertCorrectUpperBound(calD2LogWV(vPower, logZ, alpha, (int) D2jkU) - d2logWVMax,
                    calD2LogWV(vPower, logZ, alpha, (int) D2jkU - 1) - d2logWVMax, logEpsilon);
        }
        
    }
    
    public void assertCorrectLowerBound(double currVal, double nextVal, double logEpsilon) {
        Assert.assertTrue(currVal < logEpsilon && nextVal >= logEpsilon);
    }
    
    public void assertCorrectUpperBound(double currVal, double preVal, double logEpsilon) {
        Assert.assertTrue(currVal < logEpsilon && preVal >= logEpsilon);
    }

    /***
     * calculate one serie element without 1/y or 1/(pi*y)
     */
    public double calLogWV(double vPower, double logZ, double alpha, int index) {
        if (vPower < 2) {
            return index*logZ-logGamma(1+index)-logGamma(-alpha*index);
        } else {
            return index*logZ+logGamma(1+alpha*index)-logGamma(1+index);
        }
    }
    
    public double calDLogWV(double vPower, double logZ, double alpha, int index) {
        if (vPower < 2) {
            return Math.log(index)+index*logZ-logGamma(1+index)-logGamma(-alpha*index);
        } else {
            return Math.log(index)+index*logZ+logGamma(1+alpha*index)-logGamma(1+index);
        }
    }
    
    public double calD2LogWV(double vPower, double logZ, double alpha, int index) {
        if (vPower < 2) {
            return 2*Math.log(index)+index*logZ-logGamma(1+index)-logGamma(-alpha*index);
        } else {
            return 2*Math.log(index)+index*logZ+logGamma(1+alpha*index)-logGamma(1+index);
        }
    }
    
    public void assertConstColGeneration(Frame train, GLMModel.GLMParameters params) {
        params._train = train._key;
        params._compute_p_values = true;
        params._dispersion_parameter_method = pearson;
        params._family = GLMModel.GLMParameters.Family.tweedie;
        params._lambda = new double[]{0.0};
        GLMModel glmML = new GLM(params).trainModel().get();
        Scope.track_generic(glmML);
        DataInfo dinfo = new DataInfo(params.train().clone(), null, 1,
                params._use_all_factor_levels || params._lambda_search, params._standardize ?
                DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
                params.missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.Skip,
                params.imputeMissing(), params.makeImputer(), false, false, false,
                false, params.interactionSpec());
        TweedieMLDispersionOnly mlDisp = new TweedieMLDispersionOnly(train, params, glmML, glmML.beta(), dinfo);
        compareConstFrame(params, mlDisp);
        mlDisp.cleanUp();
    }
    
    public void compareConstFrame(GLMModel.GLMParameters params, TweedieMLDispersionOnly mlDisp) {
        Frame infoFrame = mlDisp._infoFrame;
        Scope.track(infoFrame);
        String[] constColNames = mlDisp._constFrameNames;
        int numRows = (int) infoFrame.numRows();
        int numCols = constColNames.length;
        double[] manualConstRow = new double[numCols];
        double[] constRow = new double[numCols];

        int offset = mlDisp._weightPresent ? 3 : 2;
        for (int rowIndex = 0; rowIndex < numRows; rowIndex++) {
            manuallyGenerateConst(manualConstRow, mlDisp, params, rowIndex);
            extractFrame2Array(constRow, infoFrame, offset, rowIndex);
            Assert.assertTrue(TestUtil.equalTwoArrays(manualConstRow, constRow, 1e-6));
        }
    }
    
    public void extractFrame2Array(double[] constRow, Frame infoFrame, int colOffset, int rowInd) {
        int numCols = constRow.length;
        for (int colInd = 0; colInd < numCols; colInd++)
            constRow[colInd] = infoFrame.vec(colInd+colOffset).at(rowInd);
    }
    
    public void manuallyGenerateConst(double[] manualConstRow, TweedieMLDispersionOnly mlDisp,
                                      GLMModel.GLMParameters params, int rowInd) {
        Frame respMu = mlDisp._infoFrame;
        Scope.track(respMu);
        double resp = respMu.vec(0).at(rowInd);
        double mu = respMu.vec(1).at(rowInd);
        double weight = mlDisp._weightPresent ? respMu.vec(2).at(rowInd) : 1;
        double p = params._tweedie_variance_power;
        double alpha = (2.0-p)/(1.0-p);
        if (!Double.isNaN(resp)) {
            // calculate jMaxConst
            if (resp != 0)
                manualConstRow[0] = p<2 ? weight*Math.pow(resp, 2-p)/(2-p) : weight*Math.pow(resp, 2-p)/(p-2);
            else
                manualConstRow[0] = Double.NaN;
            // calculate zConst
            manualConstRow[1] = resp == 0 ? 0 : Math.pow(weight, 1-alpha)*Math.pow(resp, -alpha)* 
                    Math.pow(p-1, alpha)/(2-p);
            if (p > 2)
                manualConstRow[1] *= -1;
            // calculate part2Const of ll
            if (resp==0.0) {
                manualConstRow[2] = -weight*Math.pow(mu, 2-p)/(2-p);
            } else {
                manualConstRow[2] = weight*resp*Math.pow(mu, 1-p)/(1-p)-weight*Math.pow(mu, 2-p)/(2-p);
            }
            // calculate oneOverY
            manualConstRow[3] = resp==0 ? Double.NaN : Math.log(1.0/resp);
            // calculate oneOverPiY
            manualConstRow[4] = resp==0 ? Double.NaN : Math.log(1.0/(resp*Math.PI));
            // calculate firstOrderDerivConst
            manualConstRow[5] = Math.pow(weight, 2)*resp*Math.pow(mu, 1-p)/(p-1)+Math.pow(weight, 2)*Math.pow(mu, 2-p)/(2-p);
            // calculate secondOrderDerivConst
            manualConstRow[6] = -2*Math.pow(weight, 3)*resp*Math.pow(mu, 1-p)/(p-1)-2*Math.pow(weight, 3)*Math.pow(mu, 2-p)/(2-p);
        } else {
            Arrays.fill(manualConstRow, Double.NaN);
        }
    }
}
