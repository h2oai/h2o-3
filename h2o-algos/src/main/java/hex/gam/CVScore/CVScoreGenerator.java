package hex.gam.CVScore;

import hex.DMatrix;
import hex.DataInfo;
import hex.gam.GAM;
import hex.gam.GAMModel;
import hex.glm.ComputationState;
import hex.glm.GLMModel;
import hex.glm.GLMTask;
import hex.gram.Gram;
import hex.util.LinearAlgebraUtils;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.FrameUtils;
import java.util.ArrayList;

import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static hex.glm.GLMUtils.extractAdaptedFrameIndices;

public class CVScoreGenerator extends TestUtil {
    public float getCVScore(double scale){
        float vg = 0.0f;
        try {
            Scope.enter();
            Frame train = parse_test_file("/Users/Karthik/Documents/h2o-fork/h2o-algos/src/test/java/hex/gam/good-dataset.csv");
            Scope.track(train);
            GAMModel.GAMParameters parms = new GAMModel.GAMParameters();
            parms._family = gaussian;
            parms._response_column = "response";
            parms._gam_columns = new String[]{"C1"};
            parms._lambda = new double[]{0};
            parms._alpha = new double[]{0};
            parms._scale = new double[]{scale};
            parms._num_knots = new int[]{200};
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
            Vec[] res = new Vec[Xt.numCols()];
            Key<Vec>[] keys = Vec.VectorGroup.VG_LEN1.addVecs(Xt.numCols());
            String[] col_names = new String[Xt.numCols()];
            for(int i = 0; i < Xt.numCols(); i++) {
                double[] y = new FrameUtils.Vec2ArryTsk((int) Xt.numRows()).doAll(Xt.vec(i)).res;
                chol.solve(y);
                res[i] = Vec.makeVec(y, keys[i]);
                col_names[i] = "Column_" + i;
            }

            Frame B = new Frame(col_names, res);
            Scope.track(B);
            X.add(new Frame(makeZeroOrOneFrame(X.numRows(), B.numCols(), 0, null)));
            new LinearAlgebraUtils.BMulTaskMatrices(B).doAll(X);
            Frame A = X.subframe(X.numCols() - B.numCols(), X.numCols());
            Scope.track(A);
            int trace = 0;
            for(int i = 0; i < Xt.numCols(); i++) {
                trace += A.vec(i).at(i);
            }
            vg = (float)(train.numRows() * train.numRows() * gam.mse()) / (float)((train.numRows() - trace) * (train.numRows() - trace));
        } finally {
            Scope.exit();
            return vg;
        }
    }

    public float[] test(float[] scale) {
        float[] cvScores = new float[scale.length];
        for(int i = 0; i < scale.length; i++) {
            cvScores[i] = getCVScore(scale[i]);
        }
        return cvScores;
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

    public Frame makeZeroOrOneFrame(long rowNumber, int colNumber, int val, String[] columnNames) {
        Vec tempVec = val==0?Vec.makeZero(rowNumber):Vec.makeOne(rowNumber);
        Frame madeFrame = val==0?new Frame(tempVec.makeZeros(colNumber)):new Frame(tempVec.makeOnes(colNumber));
        if (columnNames != null) {
            if (columnNames.length == colNumber)
                madeFrame.setNames(columnNames);
            else
                throw new IllegalArgumentException("Column names length and number of columns in Frame differ.");
        }
        cleanupHGLMMemory(null, null, new Vec[]{tempVec}, null);
        return madeFrame;
    }

    private void cleanupHGLMMemory(DataInfo[] tempdInfo, Frame[] tempFrames, Vec[] tempVectors, Key[] dkvKeys) {
        if (tempdInfo != null) {
            for (int index=0; index < tempdInfo.length; index++)
                if (tempdInfo[index] != null)
                    tempdInfo[index].remove();
        }
        if (tempFrames != null) {
            for (int index = 0; index < tempFrames.length; index++)
                if (tempFrames[index] != null)
                    tempFrames[index].delete();
        }
        if (tempVectors != null) {
            for (int index = 0; index < tempVectors.length; index++)
                if (tempVectors[index] != null)
                    tempVectors[index].remove();
        }
        if (dkvKeys != null) {
            for (int index=0; index < dkvKeys.length; index++) {
                if (dkvKeys[index]!= null)
                    DKV.remove(dkvKeys[index]);
            }
        }
    }
}
