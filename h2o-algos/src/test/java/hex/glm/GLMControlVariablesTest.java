package hex.glm;

import hex.genmodel.utils.DistributionFamily;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import java.util.Arrays;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMControlVariablesTest extends TestUtil {

    private Frame scoreManualWithCoefficients(double[][] coefficients, Frame data, String frameName){
        return scoreManualWithCoefficients(coefficients, data, frameName, null);
    }

    /**
     double[] eta = _eta;
     final double[][] bm = _useControlVals ? _control_beta_multinomial : _beta_multinomial;
     double sumExp = 0;
     double maxRow = 0;
     for (int c = 0; c < bm.length; ++c) {
        eta[c] = r.innerProduct(bm[c]) + o;
        if(eta[c] > maxRow)
            maxRow = eta[c];
     }
     for (int c = 0; c < bm.length; ++c)
        sumExp += eta[c] = Math.exp(eta[c]-maxRow); // intercept
     sumExp = 1.0 / sumExp;
     for (int c = 0; c < bm.length; ++c)
        preds[c + 1] = eta[c] * sumExp;
     preds[0] = ArrayUtils.maxIndex(eta);
     */
    private Frame scoreManualWithCoefficients(double[][] coefficients, Frame data, String frameName, int[] controlVariablesIdxs){
        Vec predictions = Vec.makeZero(data.numRows(), Vec.T_NUM);
        Vec[] classPredictions = new Vec[coefficients.length];
        for (int c = 0; c < coefficients.length; c++){
            classPredictions[c] = Vec.makeZero(data.numRows(), Vec.T_NUM);
        }
        for (int i = 0; i < data.numRows(); i++) {
            double sumExp = 0;
            double maxRow = 0;
            double[] preds = new double[coefficients.length];
            double[] eta = new double[coefficients.length];
            for (int c = 0; c < coefficients.length; ++c) {
                for (int j = 0; j < data.numCols(); j++) {
                    if (controlVariablesIdxs == null || Arrays.binarySearch(controlVariablesIdxs, j) < 0) {
                        eta[c] += data.vec(j).at(i) * coefficients[c][j];
                    }
                }
                if (eta[c] > maxRow) {
                    maxRow = eta[c];
                }
            }
            for (int c = 0; c < coefficients.length; ++c) {
                    sumExp += eta[c] = Math.exp(eta[c] - maxRow); // intercept
            }
            sumExp = 1.0 / sumExp;
            for (int c = 0; c < coefficients.length; ++c) {
                    preds[c] = eta[c] * sumExp;
            }
            predictions.set(i, ArrayUtils.maxIndex(eta));
            for (int c = 0; c < coefficients.length; c++){
                classPredictions[c].set(i, preds[c]);
            }
        }
        return new Frame(Key.<Frame>make(frameName),new String[]{"predict", "0", "1", "2"},new Vec[]{predictions, classPredictions[0], classPredictions[1], classPredictions[2]});
    }
    
    public double innerProduct(double[] vec, int numStart, int[] binIds, int[] numVals, int nNums, int[] numIds, boolean intercept){
        double res = 0;
        int off = 0;
        for (int i = 0; i < binIds.length; ++i)
            res += vec[off+binIds[i]];
        if (numIds == null) {
            for (int i = 0; i < numVals.length; ++i)
                res += numVals[i] * vec[numStart + i];
        } else {
            for (int i = 0; i < nNums; ++i)
                res += numVals[i] * vec[off+numIds[i]];
        }
        if(intercept)
            res += vec[vec.length-1];
        return res;
    }
    
    public double toProb(double pred){
        return 1.0 / (Math.exp(-pred) + 1.0);
    }

    @Test
    public void testBasicDataMultinomial(){
        /* Test against multinomial GLM from glmnet library in R 
         
        cat1 <- factor(c(1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0))
        cat2 <- factor(c(1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0))
        res <- factor(c(1,2,0,0,2,1,0,1,0,1,2,1,1,2,1,0,2,0,2,0,2,0,1,2,1,1))
        data <- data.frame(cat1, cat2, res)

        library(glmnet)
        X <- model.matrix(~.,data[c("cat1", "cat2")])[,-1]

        fit <- glmnet(X, data$res, family = "multinomial", type.multinomial = "grouped", lambda = 0, alpha=0.5)
        coef(fit, s=0)
 
        preds <- predict(fit, X)

        $`0`
        3 x 1 sparse Matrix of class "dgCMatrix"
                             1
        (Intercept) -0.1505452
        cat11       -0.0935533
        cat21        0.2917359

        $`1`
        3 x 1 sparse Matrix of class "dgCMatrix"
                              1
        (Intercept)  0.03661836
        cat11        0.26101689
        cat21       -0.06250493

        $`2`
        3 x 1 sparse Matrix of class "dgCMatrix"
                             1
        (Intercept)  0.1139269
        cat11       -0.1674636
        cat21       -0.2292310
         */

        Frame train = null;
        GLMModel glm = null;
        GLMModel glmControl = null;
        Frame preds = null;
        Frame predsControl = null;
        Frame predsR = null;
        try {
            Scope.enter();

            Vec cat1 = Vec.makeVec(new long[]{1,1,1,0,0,1,1,0,0,1,0,1,0,1,1,1,0,0,0,0,1,1,1,1,0,0},new String[]{"0","1"},Vec.newKey());
            Vec cat2 = Vec.makeVec(new long[]{1,0,1,0,0,0,0,1,1,0,1,0,0,1,0,1,0,0,1,1,0,0,1,0,1,0},new String[]{"0","1"},Vec.newKey());
            Vec res = Vec.makeVec(new double[]{1,2,0,0,2,1,0,1,0,1,2,1,1,2,1,0,2,0,2,0,2,0,1,2,1,1}, new String[]{"0","1", "2"},Vec.newKey());
            train = new Frame(Key.<Frame>make("train"),new String[]{"cat1", "cat2", "y"},new Vec[]{cat1, cat2,res});
            DKV.put(train);

            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._train = train._key;
            params._standardize = true;
            params._lambda = new double[]{0};
            params._objective_epsilon = 1e-6;
            params._beta_epsilon = 1e-5;
            params._intercept = true;
            params._response_column = "y";
            params._lambda_search = false;
            params._distribution = DistributionFamily.multinomial;
            glm = new GLM(params).trainModel().get();
            preds = glm.score(train);
            //System.out.println(preds.toTwoDimTable().toString());

            //System.out.println(glm._output._variable_importances);
            double[][] coefficientsH2o = glm._output.getNormBetaMultinomial();
            

            params._control_variables = new String[]{"cat1"};
            glmControl = new GLM(params).trainModel().get();
            predsControl = glmControl.score(train);
            //System.out.println(predsControl.toTwoDimTable().toString());
            //System.out.println(glmControl._output._variable_importances);
            
            double[][] coefficientsControlH2o = glmControl._output.getNormBetaMultinomial();


            //double[] coefR0 = new double[]{0.2917359, -0.1505452, -0.0935533};
            //double[] coefR1 = new double[]{-0.06250493, 0.03661836, 0.26101689};
            //double[] coefR2 = new double[]{-0.2292310, 0.1139269, -0.1674636};

            double[] coefR0 = new double[]{-0.0935533, 0.2917359, -0.1505452};
            double[] coefR1 = new double[]{0.26101689, -0.06250493, 0.03661836};
            double[] coefR2 = new double[]{-0.1674636, -0.2292310, 0.1139269};
            
            double[][] coefficientsR = new double[][]{coefR0, coefR1, coefR2};
            
            double[] coefH2O0 = coefficientsH2o[0];
            double[] coefH2O1 = coefficientsH2o[1];
            double[] coefH2O2 = coefficientsH2o[2];

            System.out.println("Coefficients before normalization");
            System.out.println(Arrays.toString(coefH2O0) + Arrays.toString(coefH2O1) + Arrays.toString(coefH2O2));

            double[][] normCoefficientsH2O = normalizeValues(coefficientsH2o);
            double[] coefH2O0Norm = normCoefficientsH2O[0];
            double[] coefH2O1Norm = normCoefficientsH2O[1];
            double[] coefH2O2Norm = normCoefficientsH2O[2];

            System.out.println("Coefficients h2o normalized");
            System.out.println(Arrays.toString(coefH2O0Norm) + Arrays.toString(coefH2O1Norm) + Arrays.toString(coefH2O2Norm));
            System.out.println("Coefficients R");
            System.out.println(Arrays.toString(coefR0) + Arrays.toString(coefR1) + Arrays.toString(coefR2));
            
            Vec predsRVec = Vec.makeVec(new double[]{2, 2, 2, 3, 3, 2, 2, 1, 1, 2, 1, 2, 3, 2, 2, 2, 3, 3, 1, 1, 2, 2, 2, 2, 1, 3},Vec.newKey());
            Vec preds0 = Vec.makeVec(new double[]{0.04763741, -0.24409854, 0.04763741, -0.15054523, -0.15054523, -0.24409854, -0.24409854, 0.14119072, 0.14119072, -0.24409854, 0.14119072, -0.24409854, -0.15054523, 0.04763741, -0.24409854, 0.04763741, -0.15054523, -0.15054523, 0.14119072, 0.14119072, -0.24409854, -0.24409854, 0.04763741, -0.24409854, 0.14119072, -0.15054523},Vec.newKey());
            Vec preds1 = Vec.makeVec(new double[]{0.23513032, 0.29763525, 0.23513032, 0.03661836, 0.03661836, 0.29763525, 0.29763525, -0.02588657, -0.02588657, 0.29763525, -0.02588657, 0.29763525, 0.03661836, 0.23513032, 0.29763525, 0.23513032, 0.03661836, 0.03661836, -0.02588657, -0.02588657, 0.29763525, 0.29763525, 0.23513032, 0.29763525, -0.02588657, 0.03661836},Vec.newKey());
            Vec preds2 = Vec.makeVec(new double[]{-0.28276773, -0.05353671, -0.28276773, 0.11392687, 0.11392687, -0.05353671, -0.05353671, -0.11530414, -0.11530414, -0.05353671, -0.11530414, -0.05353671, 0.11392687, -0.28276773, -0.05353671, -0.28276773, 0.11392687, 0.11392687, -0.11530414, -0.11530414, -0.05353671, -0.05353671, -0.28276773, -0.05353671, -0.11530414, 0.11392687},Vec.newKey());
            predsR = new Frame(Key.<Frame>make("predsR"),new String[]{"predict", "0", "1", "2"},new Vec[]{predsRVec, preds0, preds1, preds2});

            //coefficientsH2o = new double[][]{coefH2O0, coefH2O1, coefH2O2};
            //System.out.println("Coefficients control h2o");
            //System.out.println(glmControl.coefficients().toString());
            
            Frame manualPredsR = scoreManualWithCoefficients(coefficientsR, train, "manualPredsR");
            Frame manualPredsH2o = scoreManualWithCoefficients(coefficientsH2o, train, "manualPredsH2o");
            Frame manualPredsH2oNormalized = scoreManualWithCoefficients(normCoefficientsH2O, train, "manualPredsH2oNormalized");
            Frame manualPredsControl = scoreManualWithCoefficients(coefficientsControlH2o, train, "manualPredsControl", new int[]{0});
            Frame manualPredsRControl = scoreManualWithCoefficients(coefficientsR, train, "manualPredsR", new int[]{0});
            
            double tol = 1e-3;
            long numRows = preds.numRows();
            for (long i = 0; i < numRows; i++) {
                long h2oPredict = preds.vec(0).at8(i);
                double h2o0 = preds.vec(1).at(i);
                double h2o1 = preds.vec(2).at(i);
                double h2o2 = preds.vec(3).at(i);
                
                double manualH2oPredict = manualPredsH2o.vec(0).at8(i);
                double manualH2o0 = manualPredsH2o.vec(1).at(i);
                double manualH2o1 = manualPredsH2o.vec(2).at(i);
                double manualH2o2 = manualPredsH2o.vec(3).at(i);

                double manualH2oPredictNorm = manualPredsH2oNormalized.vec(0).at8(i);
                double manualH2oNorm0 = manualPredsH2oNormalized.vec(1).at(i);
                double manualH2oNorm1 = manualPredsH2oNormalized.vec(2).at(i);
                double manualH2oNorm2 = manualPredsH2oNormalized.vec(3).at(i);
                
                long rPredict = predsR.vec(0).at8(i) - 1;
                double r0 = predsR.vec(1).at(i);
                double r1 = predsR.vec(2).at(i);
                double r2 = predsR.vec(3).at(i);
                
                double manualRPredict = manualPredsR.vec(0).at8(i);
                double manualR0 = manualPredsR.vec(1).at(i);
                double manualR1 = manualPredsR.vec(2).at(i);
                double manualR2 = manualPredsR.vec(3).at(i);
                
                
                long h2oControlPredict = predsControl.vec(0).at8(i);
                double h2oControl0 = predsControl.vec(1).at(i);
                double h2oControl1 = predsControl.vec(2).at(i);
                double h2oControl2 = predsControl.vec(3).at(i);
                
                double manualH2oControlPredict = manualPredsControl.vec(0).at(i);
                double manualH2oControl0 = manualPredsControl.vec(1).at(i);
                double manualH2oControl1 = manualPredsControl.vec(2).at(i);
                double manualH2oControl2 = manualPredsControl.vec(3).at(i);

                double manualRControlPredict = manualPredsRControl.vec(0).at(i);
                double manualRControl0 = manualPredsRControl.vec(1).at(i);
                double manualRControl1 = manualPredsRControl.vec(2).at(i);
                double manualRControl2 = manualPredsRControl.vec(3).at(i);


                System.out.println(i+"\nh2o predict: \n"+h2oPredict+" 0: "+h2o0+" 1: "+h2o1+" 2: "+h2o2+
                        "\nh2o manual predict: \n"+manualH2oPredict+" 0: "+manualH2o0+" 1: "+manualH2o1+" 2: "+manualH2o2+
                        "\nh2o manual normalized predict: \n"+manualH2oPredictNorm+" 0: "+manualH2oNorm0+" 1: "+manualH2oNorm1+" 2: "+manualH2oNorm2+
                        "\nR predict: \n"+rPredict+" 0: "+r0+" 1: "+r1+" 2: "+r2+
                        "\nR manual predict: \n"+manualRPredict+" 0: "+manualR0+" 1: "+manualR1+" 2: "+manualR2+
                        "\nh2o control predict: \n"+h2oControlPredict+" 0: "+h2oControl0+" 1: "+h2oControl1+" 2: "+h2oControl2+
                        "\nh2o manual control predict: \n"+manualH2oControlPredict+" 0: "+manualH2oControl0+" 1: "+manualH2oControl1+" 2: "+manualH2oControl2+
                        "\nR manual control predict: \n"+manualRControlPredict+" 0: "+manualRControl0+" 1: "+manualRControl1+" 2: "+manualRControl2+"\n");

                // glm score calculation check
                Assert.assertEquals(h2o0, manualH2o0, tol);
                Assert.assertEquals(manualH2oPredictNorm, manualRPredict, tol);
                Assert.assertEquals(manualH2oNorm0, manualR0, tol);
                Assert.assertEquals(manualH2oNorm1, manualR1, tol);
                Assert.assertEquals(manualH2oNorm2, manualR2, tol);
                //Assert.assertEquals(r0, manualR0, tol);

                // control values calculation check
                Assert.assertEquals(h2oControl0, manualH2oControl0, tol);
                //bAssert.assertEquals(h2oControl0, manualRControl0, tol);
            }
            
        } finally {
            if (train != null) train.remove();
            if (glm != null) glm.remove();
            if (glmControl != null) glmControl.remove();
            if (preds != null) preds.remove();
            if (predsControl != null) predsControl.remove();
            if (predsR != null) predsR.remove();
            Scope.exit();
        }
    }
    
    private double[][] normalizeValues(double[][] vals){
        double[][] normVals = new double[vals.length][vals[0].length];
        for (int i = 0; i < vals.length; i++) {
            double mean = 0;
            for (int j = 0; j < vals[i].length; j++) {
                mean += vals[j][i];
            }
            mean = mean/vals.length;
            for (int j = 0; j < vals[i].length; j++) {
                normVals[j][i] = vals[j][i] - mean;
            }
        }
        return normVals;
    }
    
}
