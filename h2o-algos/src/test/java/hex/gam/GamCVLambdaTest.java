package hex.gam;

import hex.DMatrix;
import hex.DataInfo;
import hex.SplitFrame;
import hex.glm.ComputationState;
import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.glm.GLMTask;
import hex.gram.Gram;
import hex.util.LinearAlgebraUtils;
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
import water.util.FrameUtils;

import java.util.ArrayList;

import static hex.gam.GamTestPiping.getModel;
import static hex.gam.GamTestPiping.massageFrame;
import static hex.gam.MatrixFrameUtils.GamUtils.equalColNames;
import static hex.genmodel.algos.gam.GamUtilsCubicRegression.locateBin;
import static hex.glm.GLMModel.GLMParameters.Family.*;
import static hex.glm.GLMModel.GLMParameters.GLMType.gam;
import static hex.glm.GLMModel.GLMParameters.GLMType.glm;
import static hex.glm.GLMUtils.extractAdaptedFrameIndices;
import static org.junit.Assert.assertEquals;

/***
 * Here I am going to test the following:
 * - model matrix formation with centering
 */
@RunWith(H2ORunner.class)
@CloudSize(1)

public class GamCVLambdaTest extends TestUtil{
    /**
     * This test 
     */
    @Test
    public void testCvLambda(){
        try{
            Scope.enter();
            String[] gamCols = new String[]{"C1"};
            double scale = 3;
            Frame train = Scope.track(parse_test_file("dataset.csv"));
            final GAMModel model = getModel(gaussian,
                    train, "response", gamCols, new double[]{scale}, new double[]{0}, new double[]{0});
            Scope.track_generic(model);
            Frame X = new Frame(model._output._dinfo._adaptedFrame);
            Scope.track(X);
            X.add("colOnes", Vec.makeOne(model._output._dinfo._adaptedFrame.numRows()));
            Frame Xt = DMatrix.transpose(X);
            Scope.track(Xt);
            boolean standardizeQ = true;
            DataInfo dinfo = new DataInfo(train.clone(), null, 1, true, null, DataInfo.TransformType.NONE,
                    true,
                    false,
                    null,
                    false, false, false, false, null);
            //ComputationState.GramXY gram = computeGram();
            //XT.vec(0).at(1) # first column, second row
            //x.vec(1).at(0)
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void test() {
        try {
            Scope.enter();
            Frame train = parse_test_file("/Users/Karthik/Documents/h2o-fork/h2o-algos/src/test/java/hex/gam/dataset.csv");
            Scope.track(train);
            double scale = 1;
            GAMModel.GAMParameters parms = new GAMModel.GAMParameters();
            parms._family = gaussian;
            parms._response_column = "response";
            parms._gam_columns = new String[]{"C1"};
            parms._lambda = new double[]{0};
            parms._alpha = new double[]{0};
            parms._scale = new double[]{scale};
            parms._standardize = true;
            parms._train = train._key;
            parms._savePenaltyMat = true;
            parms._keep_gam_cols = true;
            GAMModel gam = new GAM(parms).trainModel().get();
            Scope.track_generic(gam);
            DKV.put(gam._output._dinfo);
            Vec[] vecs = gam._output._dinfo._adaptedFrame.anyVec().makeDoubles(2, new double[]{0, 0});
            gam._output._dinfo.addResponse(new String[]{"__glm_sumExp", "__glm_maxRow"}, vecs);
            Frame gamCols = DKV.getGet(gam._gamFrameKeysCenter[0]);
            double[][][] penaltyMat = gam._output._penaltyMatrices;
            int[][] gamColIndices = extractAdaptedFrameIndices(gam._output._dinfo._adaptedFrame, gam._output._gamColNames, 0);
            GLMModel.GLMParameters glmParameters = new GLMModel.GLMParameters();
            glmParameters._family = gaussian;
            glmParameters._response_column = "response";
            glmParameters._lambda = new double[]{0};
            glmParameters._alpha = new double[]{0};
            glmParameters._train = train._key;
            glmParameters._link = GLMModel.GLMParameters.Link.identity;

            ComputationState state = new ComputationState(null, glmParameters, gam._output._dinfo, null, 1, penaltyMat, gamColIndices);
            ComputationState.GramXY gram = computeNewGram(gamColIndices, penaltyMat, state, glmParameters, gam._output._dinfo, gam._output._standardized_model_beta, GLMModel.GLMParameters.Solver.IRLSM);
            Gram.Cholesky chol = gram.gram.qrCholesky(new ArrayList<>(), true);
            Frame X = new Frame(gam._output._dinfo._adaptedFrame);
            Scope.track(X);
            Frame Xt = DMatrix.transpose(X);
            Scope.track(Xt);
            double[][] B = new double[(int) Xt.numRows()][Xt.numCols()];
            Vec[] res = new Vec[Xt.numCols()];
            Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(Xt.numCols());
            String[] col_names = new String[Xt.numCols()];
            for(int i = 0; i < Xt.numCols(); i++) {
                double[] y = new FrameUtils.Vec2ArryTsk((int) Xt.numRows()).doAll(Xt.vec(i)).res;
                chol.solve(y);
                res[i] = Vec.makeVec(y, keys[i]);
                col_names[i] = "Column_" + i;
//                for(int j = 0; j < (int) Xt.numRows(); j++) {
//                    B[j][i] = y[j];
//                }
            }
            Frame B_frame = new Frame(col_names, res);
            Scope.track(B_frame);
            
            Frame A = (new LinearAlgebraUtils.BMulTask(null, gam._output._dinfo, B).doAll(B.length, Vec.T_NUM,
                    gam._output._dinfo._adaptedFrame)).outputFrame(Key.make("A"), null, null);
            Scope.track(A);
            int trace = 0;
            for(int i = 0; i < Xt.numCols(); i++) {
                trace += A.vec(i).at(i);
            }
            
            System.out.println();
        } finally {
            Scope.exit();
        }
    }

    public ComputationState.GramXY computeNewGram(int [][] _gamBetaIndices, double[][][] _penaltyMatrix, ComputationState state, GLMModel.GLMParameters _parms, DataInfo activeData, double [] beta, GLMModel.GLMParameters.Solver s){
        double obj_reg = 0.1; // number of rows in dataset
        GLMModel.GLMWeightsFun _glmw = new GLMModel.GLMWeightsFun(_parms);
        GLMTask.GLMIterationTask gt = new GLMTask.GLMIterationTask(null, activeData, _glmw, beta,
                -1).doAll(activeData._adaptedFrame);
        gt.getGram().mul(obj_reg);
        if (_parms._glmType.equals(GLMModel.GLMParameters.GLMType.gam)) { // add contribution from GAM smoothness factor
            Integer[] activeCols=null;
            int[] activeColumns = activeData.activeCols();
            if (activeColumns.length < activeData.fullN()) { // columns are deleted
                activeCols = ArrayUtils.toIntegers(activeColumns, 0, activeColumns.length);
            }
            gt.getGram().addGAMPenalty(activeCols , _penaltyMatrix, _gamBetaIndices);
        }
        ArrayUtils.mult(gt.get_xy(),obj_reg);
        int [] activeCols = activeData.activeCols();
        int [] zeros = gt.getGram().findZeroCols();
        ComputationState.GramXY res;
        if(_parms._family != GLMModel.GLMParameters.Family.multinomial && zeros.length > 0) {
            gt.getGram().dropCols(zeros);
            state.removeCols(zeros);
            res = new ComputationState.GramXY(gt.getGram(), ArrayUtils.removeIds(gt.get_xy(), zeros),null, gt.get_beta() == null?null:ArrayUtils.removeIds(gt.get_beta(), zeros), activeData.activeCols(),null, gt.get_yy(),gt._likelihood);
        } else res = new ComputationState.GramXY(gt.getGram(),gt.get_xy(),null,beta == null?null:beta,activeCols,null,gt.get_yy(),gt._likelihood);

        return res;
    }

    public static GAMModel getModel(GLMModel.GLMParameters.Family family, Frame train, String responseColumn,
                                    String[] gamCols, double[] scale, double[] alpha, double[] lambda) {
        GAMModel gam = null;
        try {
            Scope.enter();
            train = massageFrame(train, family);
            DKV.put(train);
            Scope.track(train);

            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._scale = scale;
            params._family = family;
            params._response_column = responseColumn;
            params._alpha = alpha;
            params._lambda = lambda;
            params._gam_columns = gamCols;
            params._train = train._key;
            params._family = family;
            params._link = GLMModel.GLMParameters.Link.family_default;
            params._keep_gam_cols = true;
            params._solver = GLMModel.GLMParameters.Solver.IRLSM;
            gam = new GAM(params).trainModel().get();
            return gam;
        } finally {
            Scope.exit();
        }
    }
}
