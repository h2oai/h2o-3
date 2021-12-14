package hex.modelselection;

import hex.*;
import hex.glm.GLM;
import hex.glm.GLMModel;
import water.DKV;
import water.H2O;
import water.Key;
import water.Scope;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static hex.genmodel.utils.MathUtils.combinatorial;
import static hex.glm.GLMModel.GLMParameters.Family.AUTO;
import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static hex.modelselection.ModelSelectionModel.ModelSelectionParameters.Mode.allsubsets;
import static hex.modelselection.ModelSelectionModel.ModelSelectionParameters.Mode.maxr;
import static hex.modelselection.ModelSelectionUtils.*;

public class ModelSelection extends ModelBuilder<hex.modelselection.ModelSelectionModel, hex.modelselection.ModelSelectionModel.ModelSelectionParameters, hex.modelselection.ModelSelectionModel.ModelSelectionModelOutput> {
    public String[][] _bestModelPredictors; // store for each predictor number, the best model predictors
    public double[] _bestR2Values;  // store the best R2 values of the best models with fix number of predictors
    DataInfo _dinfo;
    public int _numModelBuilt;      // number of models that are to be built
    public int _numPredictors;
    public String[] _predictorNames;
    public int _glmNFolds = 0;
    Model.Parameters.FoldAssignmentScheme _foldAssignment = null;
    String _foldColumn = null;

    public ModelSelection(boolean startup_once) {
        super(new hex.modelselection.ModelSelectionModel.ModelSelectionParameters(), startup_once);
    }
    
    public ModelSelection(hex.modelselection.ModelSelectionModel.ModelSelectionParameters parms) {
        super(parms);
        init(false);
    }
    
    public ModelSelection(hex.modelselection.ModelSelectionModel.ModelSelectionParameters parms, Key<hex.modelselection.ModelSelectionModel> key) {
        super(parms, key);
        init(false);
    }
    
    @Override
    protected int nModelsInParallel(int folds) {
        return nModelsInParallel(1,2);  // disallow nfold cross-validation
    }

    @Override
    protected ModelSelectionDriver trainModelImpl() {
        return new ModelSelectionDriver();
    }

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{ModelCategory.Regression};
    } // because of r2 usage

    @Override
    public boolean isSupervised() {
        return true;
    }

    @Override
    public boolean haveMojo() {
        return false;
    }

    @Override
    public boolean havePojo() {
        return false;
    }
    
    public void init(boolean expensive) {
        if (_parms._nfolds > 0 || _parms._fold_column != null) {
            _glmNFolds = _parms._nfolds;
            if (_parms._fold_assignment != null) {
                _foldAssignment = _parms._fold_assignment;
                _parms._fold_assignment = null;
            }
            if (_parms._fold_column != null) {
                _foldColumn = _parms._fold_column;
                _parms._fold_column = null;
            }
            _parms._nfolds = 0;
        }
        super.init(expensive);
        if (expensive) {
            initModelSelectionParameters();
            initModelParameters();
        }
    }
    
    private void initModelParameters() {
        _numModelBuilt = calculateModelNumber(_numPredictors, _parms._max_predictor_number);
        _bestR2Values = new double[_parms._max_predictor_number];
        _bestModelPredictors = new String[_parms._max_predictor_number][];
    }

    private void initModelSelectionParameters() {
        if (nclasses() > 1)
            error("response", "ModelSelection only works with regression.");
        
        if (!(AUTO.equals(_parms._family) || gaussian.equals(_parms._family)))
            error("_family", "ModelSelection only supports Gaussian family");
        
        if (_parms._nparallelism < 0) 
            error("nparallelism", "must be >= 0.");
        
        if (_parms._nparallelism == 0)
            _parms._nparallelism = H2O.NUMCPUS;
        
        _predictorNames = extractPredictorNames(_parms, _dinfo, _foldColumn);
        _numPredictors = _predictorNames.length;
        if (_parms._max_predictor_number < 1 || _parms._max_predictor_number > _numPredictors)
            error("max_predictor_number", "max_predictor_number must exceed 0 and be no greater" +
                    " than the number of predictors of the training frame.");
    }
    
    public class ModelSelectionDriver extends Driver {
        public final void buildModel() {
            hex.modelselection.ModelSelectionModel model = null;
            try {
                model = new hex.modelselection.ModelSelectionModel(dest(), _parms, new hex.modelselection.ModelSelectionModel.ModelSelectionModelOutput(ModelSelection.this, _dinfo));
                model.write_lock(_job);
                model._output._best_model_ids = new Key[_parms._max_predictor_number];
                model._output._best_r2_values = new double[_parms._max_predictor_number];
                model._output._best_model_predictors = new String[_parms._max_predictor_number][];
                model._output._coefficient_names = new String[_parms._max_predictor_number][];
                // build glm model with num_predictors and find one with best R2
                if (allsubsets.equals(_parms._mode))
                    buildAllSubsetsModels(model);
                else if (maxr.equals(_parms._mode))
                    buildMaxRModels(model);
                _job.update(0, "Completed GLM model building.  Extracting results now.");
                model.update(_job);
                // copy best R2 and best predictors to model._output
                model._output.generateSummary();
            } finally {
                model.update(_job);
                model.unlock(_job);
            }
        }

        /**
         * Perform variable selection using MaxR implementing the sequential replacement method:
         * 1. forward step: find the initial subset of predictors with highest R2 values.  When subset size = 1, this
         *    means basically choosing the one predictor that generates a model with highest R2.  When subset size > 1,
         *    this means we choose one predictor to add to the subset which generates a model with hightest R2.
         * 2. Replacement step: consider the predictors in subset as pred0, pred1, pred2 (using subset size 3 as example):
         *    a. Keep pred1, pred2 and replace pred0 with remaining predictors, find replacement with highest R2
         *    b. keep pred0, pred2 and replace pred1 with remaining predictors, find replacement with highest R2
         *    c. keeep pred0, pred1 and replace pred2 with remaining predictors, find replacement with highest R2
         *    d. from step 2a, 2b, 2c, choose the predictor subset with highest R2, say the subset is pred0, pred4, pred2
         *    e. Take subset pred0, pred4, pred2 and go through steps a,b,c,d again and only stop when the best R2 does
         *       not improve anymore.
         *       
         * see doc at https://h2oai.atlassian.net/browse/PUBDEV-8444 for details.
         * 
         * @param model
         */
        void buildMaxRModels(ModelSelectionModel model) {
            List<Integer> currSubsetIndices = new ArrayList<>();
            List<String> coefNames = new ArrayList<>(Arrays.asList(_predictorNames));
            List<Integer> validSubset = IntStream.rangeClosed(0, coefNames.size() - 1).boxed().collect(Collectors.toList());
            for (int predNum = 1; predNum <= _parms._max_predictor_number; predNum++) { // perform for each subset size
                GLMModel bestR2Model = forwardStep(currSubsetIndices, coefNames, predNum - 1, validSubset,
                        _parms, _foldColumn, _glmNFolds, _foldAssignment); // forward step
                validSubset.removeAll(currSubsetIndices);
                _job.update(predNum, "Finished building all models with "+predNum+" predictors.");
                if (predNum < _numPredictors) {
                    GLMModel currBestR2Model = replacement(currSubsetIndices, coefNames, bestR2Model.r2(), _parms,
                            _glmNFolds, _foldColumn, validSubset, _foldAssignment);
                    if (currBestR2Model != null)
                        bestR2Model = currBestR2Model;
                }
                Scope.untrack(bestR2Model.getKey());
                DKV.put(bestR2Model);
                model._output.updateBestModels(bestR2Model, predNum - 1);
            }
        }


        
        /***
         * Find the subset of predictors of sizes 1, 2, ..., _parm._max_predictor_number that generate the
         * highest R2 value using brute-force.  Basically for each subset size, all combinations of predictors
         * are considered.  This method is guaranteed to find the best predictor subset at the cost of computation
         * complexity.
         * 
         * @param model
         */
        void buildAllSubsetsModels(ModelSelectionModel model) {
            for (int predNum=1; predNum <= _parms._max_predictor_number; predNum++) {
                int numModels = combinatorial(_numPredictors, predNum);
                // generate the training frames with num_predictor predictors in the frame
                Frame[] trainingFrames = generateTrainingFrames(_parms, predNum, _predictorNames, numModels,
                        _foldColumn);
                // find best GLM Model with highest R2 value
                GLMModel bestModel = buildExtractBestR2Model(trainingFrames,_parms, _glmNFolds, _foldColumn, _foldAssignment);
                Scope.untrack(bestModel.getKey());  // untrack best model
                DKV.put(bestModel);
                // extract R2 and collect the best R2 and the predictors set
                int index = predNum-1;
                model._output.updateBestModels(bestModel, index);
                // remove training frames from DKV
                removeTrainingFrames(trainingFrames);
                _job.update(predNum, "Finished building all models with "+predNum+" predictors.");
            }
        }
        
        @Override
        public void computeImpl() {
            _dinfo = new DataInfo(_train.clone(), _valid, 1, false,
                    DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
                    _parms.missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.Skip,
                    _parms.imputeMissing(), _parms.makeImputer(), false, hasWeightCol(), hasOffsetCol(),
                    hasFoldCol(), null);
            init(true);
            if (error_count() > 0)
                throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(ModelSelection.this);
            _job.update(0, "finished init and ready to build models");
            buildModel();
        }
    }
    
    /**
     * Given the training Frame array, build models for each training frame and return the GLMModel with the best
     * R2 values.
     *
     * @param trainingFrames
     * @return
     */
    public static GLMModel buildExtractBestR2Model(Frame[] trainingFrames,
                                                   ModelSelectionModel.ModelSelectionParameters parms, int glmNFolds,
                                                   String foldColumn, Model.Parameters.FoldAssignmentScheme foldAssignment) {
        GLMModel.GLMParameters[] trainingParams = generateGLMParameters(trainingFrames, parms, glmNFolds,
                foldColumn, foldAssignment);
        // generate the builder;
        GLM[] glmBuilder = buildGLMBuilders(trainingParams);
        // call parallel build
        GLM[] glmResults = ModelBuilderHelper.trainModelsParallel(glmBuilder, parms._nparallelism);
        Stream.of(glmResults).map(GLM::get).forEach(Scope::track_generic);
        // find best GLM Model with highest R2 value
        return findBestModel(glmResults);
    }

    /**
     * Given a predictor subset with indices stored in currSubsetIndices, one more predictor from the coefNames
     * that was not found in currSubsetIndices was added to the subset to form a new Training frame. An array of 
     * training frame are built training frames from all elligible predictors from coefNames.  GLMParameters are
     * built for all TrainingFrames, GLM models are built from those parameters.  The GLM model with the highest 
     * R2 value is returned.  The added predictor which resulted in the highest R2 value will be added to 
     * currSubsetIndices.
     * 
     * see doc at https://h2oai.atlassian.net/browse/PUBDEV-8444 for details.
     *
     * @param currSubsetIndices: stored predictors that are chosen in the subset
     * @param coefNames: predictor names of full training frame
     * @param predPos: index/location of predictor to be added into currSubsetIndices
     * @return GLMModel with highest R2 value
     */
    public static GLMModel forwardStep(List<Integer> currSubsetIndices, List<String> coefNames, int predPos, 
                                       List<Integer> validSubsets, ModelSelectionModel.ModelSelectionParameters parms,
                                       String foldColumn, int glmNFolds, Model.Parameters.FoldAssignmentScheme foldAssignment) {
        String[] predictorNames = coefNames.stream().toArray(String[]::new);
        // generate training frames
        Frame[] trainingFrames = generateMaxRTrainingFrames(parms, predictorNames, foldColumn,
                currSubsetIndices, predPos, validSubsets);
        // find GLM model with best R2 value and return it
        GLMModel bestModel = buildExtractBestR2Model(trainingFrames, parms, glmNFolds, foldColumn, foldAssignment);
        List<String> coefUsed = extraModelColumnNames(coefNames, bestModel);

        for (int predIndex = coefUsed.size()-1; predIndex >= 0; predIndex--) {
            int index = coefNames.indexOf(coefUsed.get(predIndex));
            if (!currSubsetIndices.contains(index)) {
                currSubsetIndices.add(predPos, index);
                break;
            }
        }
        removeTrainingFrames(trainingFrames);
        return bestModel;
    }

    /**
     * consider the predictors in subset as pred0, pred1, pred2 (using subset size 3 as example):
     *    a. Keep pred1, pred2 and replace pred0 with remaining predictors, find replacement with highest R2
     *    b. keep pred0, pred2 and replace pred1 with remaining predictors, find replacement with highest R2
     *    c. keeep pred0, pred1 and replace pred2 with remaining predictors, find replacement with highest R2
     *    d. from step 2a, 2b, 2c, choose the predictor subset with highest R2, say the subset is pred0, pred4, pred2
     *    e. Take subset pred0, pred4, pred2 and go through steps a,b,c,d again and only stop when the best R2 does
     *       not improve anymore.
     *       
     * see doc at https://h2oai.atlassian.net/browse/PUBDEV-8444 for details.
     *            
     * @param currSubsetIndices
     * @param coefNames
     * @return
     */
    public static GLMModel replacement(List<Integer> currSubsetIndices, List<String> coefNames,
                                       double bestR2, ModelSelectionModel.ModelSelectionParameters parms,
                                       int glmNFolds, String foldColumn, List<Integer> validSubset,
                                       Model.Parameters.FoldAssignmentScheme foldAssignment) {
        int currSubsetSize = currSubsetIndices.size();
        int lastBestR2PosIndex = -1;
        GLMModel bestR2Model = null;
        GLMModel[] bestR2Models = new GLMModel[currSubsetSize];
        int[] r2PredPosIndex = new int[currSubsetSize];
        int[][] subsetsCombo = new int[currSubsetSize][];
        List<Integer> originalSubset = new ArrayList<>(currSubsetSize);
        while (true) {
            for (int index = 0; index < currSubsetSize; index++) {  // go through all predictor position in subset
                if (index != lastBestR2PosIndex) {
                    ArrayList<Integer> oneLessSubset = new ArrayList<>(currSubsetIndices);
                    int predIndexRemoved = oneLessSubset.remove(index);
                    validSubset.add(predIndexRemoved);
                    bestR2Models[index] = forwardStep(oneLessSubset, coefNames, index, validSubset, parms,
                            foldColumn, glmNFolds, foldAssignment);
                    subsetsCombo[index] = oneLessSubset.stream().mapToInt(i->i).toArray();
                    r2PredPosIndex[index] = index;
                    validSubset.remove(validSubset.indexOf(predIndexRemoved));
                }
            }
            int bestR2ModelIndex = findBestR2Model(bestR2, bestR2Models);
            if (bestR2ModelIndex < 0)   // done with replacement step
                break;
            else {
                bestR2Model = bestR2Models[bestR2ModelIndex];
                bestR2 = bestR2Model.r2();
                currSubsetIndices = Arrays.stream(subsetsCombo[bestR2ModelIndex]).boxed().collect(Collectors.toList()); 
                lastBestR2PosIndex = r2PredPosIndex[bestR2ModelIndex];
                updateValidSubset(validSubset, originalSubset, currSubsetIndices);
                originalSubset = currSubsetIndices; // copy over new subset
            }
        }
        return bestR2Model;
    }
}
