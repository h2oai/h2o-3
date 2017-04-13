package hex.mli.loco;

import hex.Model;
import hex.ModelCategory;
import water.MRTask;
import water.ParallelizationTask;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.DKV;
import water.H2O;
import water.util.Log;
import water.Iced;
import water.Job;
import water.rapids.ast.prims.reducers.AstMedian;
import hex.quantile.QuantileModel.CombineMethod;

/**
 * Leave One Covariate Out (LOCO)
 *
 * Calculates row-wise variable importance's by re-scoring a trained supervised model and measuring the impact of setting
 * each variable to missing or it’s most central value(mean or median & mode for categorical's)
 *
 */
public class LeaveOneCovarOut extends Iced {

    /**
     * Conduct Leave One Covariate Out (LOCO) given a model, frame, job, and replacement value
     * @param m Supervised H2O model
     * @param fr H2O Frame to score
     * @param job Job to keep track of in terms of progress
     * @param replaceVal Value to replace column by when conducting LOCO ("mean" or "median"). Default is null
     * @return  An H2OFrame displaying the base prediction (model scored with all predictors) and the difference in predictions
     *          when variables are dropped/replaced. The difference displayed is the base prediction substracted from
     *          the new prediction (when a variable is dropped/replaced with mean/median/mode) for binomial classification
     *          and regression problems. For multinomial problems, the sum of the absolute value of differences across classes
     *          is calculated per column dropped/replaced.
     */
    public static Frame leaveOneCovarOut(Model m, Frame fr, Job job, String replaceVal){

        Frame locoAnalysisFrame = new Frame();
        if(m._output.getModelCategory() != ModelCategory.Multinomial) {
            locoAnalysisFrame.add("base_pred", getBasepredictions(m, fr)[0]);
        } else {
            locoAnalysisFrame.add(new Frame(getBasepredictions(m, fr)));
            locoAnalysisFrame._names[0] = "base_pred";
        }

        String[] predictors = m._output._names;

        LeaveOneCovariateOutDriver[] tasks = new LeaveOneCovariateOutDriver[predictors.length-1];

        for(int i = 0; i < tasks.length; i++){
            tasks[i] = new LeaveOneCovariateOutDriver(locoAnalysisFrame,fr,m,predictors[i],replaceVal);
        }

        ParallelizationTask locoCollector = new ParallelizationTask<>(tasks, job);
        long start = System.currentTimeMillis();
        Log.info("Starting Leave One Covariate Out (LOCO) analysis for model " + m._key + " and frame " + fr._key);
        H2O.submitTask(locoCollector).join();

        if(m._output.getModelCategory() == ModelCategory.Multinomial){
            int[] colsToRemove = new int[locoAnalysisFrame.numCols()-1];
            for(int i =0; i<colsToRemove.length; i++){
                colsToRemove[i] = i+1;
            }
            locoAnalysisFrame.remove(colsToRemove);
        }

        for (int i = 0; i < tasks.length; i++) {
            locoAnalysisFrame.add("rc_" + tasks[i]._predictor, tasks[i]._result[0]);
        }
        Log.info("Finished Leave One Covariate Out (LOCO) analysis for model " + m._key + " and frame " + fr._key +
                " in " + (System.currentTimeMillis()-start)/1000. + " seconds for " + (predictors.length-1) + " columns");

        return locoAnalysisFrame;

    }

    private static class LeaveOneCovariateOutDriver extends H2O.H2OCountedCompleter<LeaveOneCovariateOutDriver>{

        private final Frame _locoFrame;
        private final Frame _frame;
        private final Model _model;
        private final String _predictor;
        private final String _replaceVal;
        Vec[] _result;

        public LeaveOneCovariateOutDriver(Frame locoFrame, Frame fr, Model m, String predictor, String replaceVal){
            _locoFrame = locoFrame;
            _frame = fr;
            _model = m;
            _predictor = predictor;
            _replaceVal = replaceVal;
        }

        @Override
        public void compute2() {
            if(_model._output.getModelCategory() == ModelCategory.Multinomial){
                Vec[] predTmp = getNewPredictions(_model,_frame,_predictor,_replaceVal);
                Frame tmpFrame = new Frame().add(_locoFrame).add(new Frame(predTmp));
                _result = new MultiDiffTask(_model._output.nclasses()).doAll(Vec.T_NUM, tmpFrame).outputFrame().vecs();
                for (Vec v : predTmp) v.remove();
            } else {
                _result = getNewPredictions(_model, _frame, _predictor,_replaceVal);
                new DiffTask().doAll(_locoFrame.vec(0), _result[0]);
            }
            Log.info("Completed Leave One Covariate Out (LOCO) Analysis for column: " + _predictor);
            tryComplete();
        }

    }

    /**
     * Get base predictions given a model and frame. "Base predictions" are predictions based on all features in the
     * model
     * @param m An H2O supervised model
     * @param fr A Frame to score on
     * @return An array of Vecs containing predictions
     */
    private static Vec[] getBasepredictions(Model m, Frame fr){

        Frame basePredsFr = m.score(fr,null,null,false);

        if(m._output.getModelCategory() == ModelCategory.Binomial) {
            Vec basePreds = basePredsFr.remove(2);
            basePredsFr.delete();
            return new Vec[] {basePreds};
        }else if(m._output.getModelCategory() == ModelCategory.Multinomial){
            Vec[] basePredsVecs = basePredsFr.vecs();
            DKV.remove(basePredsFr._key);
            return basePredsVecs;
        } else {
            Vec basePreds = basePredsFr.remove(0);
            basePredsFr.delete();
            return new Vec[] {basePreds};
        }

    }

    /**
     * Get new predictions based on dropping/replacing a column with mean, median, or mode
     * @param m An H2O supervised model
     * @param fr A Frame to score on
     * @param colToDrop Column to modify/drop before prediction
     * @param valToReplace Value to replace colToDrop by (Default is null)
     * @return
     */
    private static Vec[] getNewPredictions(Model m, Frame fr, String colToDrop, String valToReplace) {
        Frame workerFrame = new Frame(fr);
        Vec vecToReplace = fr.vec(colToDrop);
        Vec replacementVec = null;
        if(valToReplace == null){
            replacementVec = vecToReplace.makeCon(Double.NaN);
        } else if(valToReplace.equals("mean")){
            if(vecToReplace.isCategorical()){
                Vec tmpVec = vecToReplace.makeCon(vecToReplace.mode());
                replacementVec = tmpVec.toCategoricalVec(); //Can only get mode for categoricals
                tmpVec.remove();
            } else {
                replacementVec = vecToReplace.makeCon(vecToReplace.mean());
            }
        } else if(valToReplace.equals("median")){
            if(vecToReplace.isCategorical()){
                Vec tmpVec = vecToReplace.makeCon(vecToReplace.mode());
                replacementVec = tmpVec.toCategoricalVec();  //Can only get mode for categoricals
                tmpVec.remove();
            } else {
                Frame tmpFr = new Frame(vecToReplace);
                double median = AstMedian.median(tmpFr, CombineMethod.AVERAGE);
                replacementVec = vecToReplace.makeCon(median);
            }
        } else {
            throw new H2OIllegalArgumentException("Invalid value to replace columns in LOCO. Got " + valToReplace);
        }
        int vecToDropIdx = fr.find(colToDrop);
        workerFrame.replace(vecToDropIdx,replacementVec);
        DKV.put(workerFrame);
        Frame modifiedPredictionsFr = m.score(workerFrame,null,null,false);
        try {
            if (m._output.getModelCategory() == ModelCategory.Binomial) {
                Vec modifiedPrediction = modifiedPredictionsFr.remove(2);
                modifiedPredictionsFr.delete();
                return new Vec[] {modifiedPrediction};
            } else if(m._output.getModelCategory() == ModelCategory.Multinomial){
                Vec[] vecs = modifiedPredictionsFr.vecs();
                DKV.remove(modifiedPredictionsFr._key);
                return vecs;
            } else {
                Vec modifiedPrediction = modifiedPredictionsFr.remove(0);
                modifiedPredictionsFr.delete();
                return new Vec[] {modifiedPrediction};
            }
        } finally{
            DKV.remove(workerFrame._key);
            replacementVec.remove();
        }

    }

    private static class DiffTask extends MRTask<DiffTask>{
        @Override public void map(Chunk[] c) {
            Chunk _basePred = c[0];
            for(int chnk = 1; chnk < c.length; chnk++){
                for(int row = 0; row < c[0]._len; row++){
                    c[chnk].set(row, c[chnk].atd(row) - _basePred.atd(row));
                }
            }
        }
    }

    private static class MultiDiffTask extends MRTask<MultiDiffTask>{

        private final int _numClasses;

        public MultiDiffTask(int numClasses){
            _numClasses = numClasses;
        }

        @Override
        public void map(Chunk[] cs, NewChunk nc) {
            for (int i = 0; i < cs[0]._len; i++) {
                double d = 0;
                for (int j = 1; j < _numClasses+1; j++) {
                    double val = cs[j + _numClasses+1].atd(i) - cs[j].atd(i);
                    d += Math.abs(val);
                }
                nc.addNum(d);
            }
        }
    }
}


