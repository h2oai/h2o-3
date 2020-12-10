package hex.schemas;

import com.google.gson.reflect.TypeToken;
import hex.ensemble.Metalearner.Algorithm;
import hex.ensemble.StackedEnsemble;
import hex.ensemble.StackedEnsembleModel;
import hex.tree.gbm.GBMModel;
import hex.tree.drf.DRFModel;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLMModel;
import hex.Model;

import water.DKV;
import water.Key;
import water.Value;
import water.api.API;
import water.api.EnumValuesProvider;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;
import water.api.schemas3.FrameV3;

import com.google.gson.Gson;
import water.fvec.Frame;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;



public class StackedEnsembleV99 extends ModelBuilderSchema<StackedEnsemble,StackedEnsembleV99,StackedEnsembleV99.StackedEnsembleParametersV99> {
  public static final class StackedEnsembleParametersV99 extends ModelParametersSchemaV3<StackedEnsembleModel.StackedEnsembleParameters, StackedEnsembleParametersV99> {
    static public String[] fields = new String[] {
      "model_id",
      "training_frame",
      "response_column",
      "validation_frame",
      "blending_frame",
      "base_models",
      "metalearner_algorithm",
      "metalearner_nfolds",
      "metalearner_fold_assignment",
      "metalearner_fold_column",
      "metalearner_params",
      "max_runtime_secs",
      "weights_column",
      "offset_column",
      "seed",
      "score_training_samples",
      "keep_levelone_frame",
      "export_checkpoints_dir", 
      "auc_type"     
    };

    public static class AlgorithmValuesProvider extends EnumValuesProvider<Algorithm> {
      public AlgorithmValuesProvider() {
        super(Algorithm.class);
      }
    }

    // Base models
    @API(level = API.Level.critical, direction = API.Direction.INOUT,
            help = "List of models or grids (or their ids) to ensemble/stack together. Grids are expanded to individual models. "
                    + "If not using blending frame, then models must have been cross-validated using nfolds > 1, and folds must be identical across models.", required = true)
    public KeyV3 base_models[];

    
    // Metalearner algorithm
    @API(level = API.Level.critical, direction = API.Direction.INOUT, 
            valuesProvider = AlgorithmValuesProvider.class,
            help = "Type of algorithm to use as the metalearner. Options include "
                    + "'AUTO' (GLM with non negative weights; if validation_frame is present, a lambda search is performed), "
                    + "'deeplearning' (Deep Learning with default parameters), "
                    + "'drf' (Random Forest with default parameters), "
                    + "'gbm' (GBM with default parameters), "
                    + "'glm' (GLM with default parameters), "
                    + "'naivebayes' (NaiveBayes with default parameters), "
                    + "or 'xgboost' (if available, XGBoost with default parameters)."
    )
    public Algorithm metalearner_algorithm;

    // For ensemble metalearner cross-validation
    @API(level = API.Level.critical, direction = API.Direction.INOUT, 
            help = "Number of folds for K-fold cross-validation of the metalearner algorithm (0 to disable or >= 2).")
    public int metalearner_nfolds;

    // For ensemble metalearner cross-validation
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, 
            values = {"AUTO", "Random", "Modulo", "Stratified"},
            help = "Cross-validation fold assignment scheme for metalearner cross-validation.  Defaults to AUTO (which is currently set to Random)." + 
                  " The 'Stratified' option will stratify the folds based on the response variable, for classification problems.")
    public Model.Parameters.FoldAssignmentScheme metalearner_fold_assignment;

    // For ensemble metalearner cross-validation
    @API(level = API.Level.secondary, direction = API.Direction.INOUT, 
            is_member_of_frames = {"training_frame"},
            //is_mutually_exclusive_with = {"ignored_columns", "response_column", "weights_column", "offset_column"},
            is_mutually_exclusive_with = {"ignored_columns", "response_column"},
            help = "Column with cross-validation fold index assignment per observation for cross-validation of the metalearner.")
    public FrameV3.ColSpecifierV3 metalearner_fold_column;

    @API(level = API.Level.secondary,
            help = "Keep level one frame used for metalearner training.")
    public boolean keep_levelone_frame;

    @API(help = "Parameters for metalearner algorithm", direction = API.Direction.INOUT)
    public String metalearner_params;
    
    @API(help="Frame used to compute the predictions that serve as the training frame for the metalearner (triggers blending mode if provided)", direction = API.Direction.INOUT)
    public KeyV3.FrameKeyV3 blending_frame;

    @API(help = "Seed for random numbers; passed through to the metalearner algorithm. Defaults to -1 (time-based random number)", gridable = true)
    public long seed;

    @API(help = "Specify the number of training set samples for scoring. The value must be >= 0. To use all training samples, enter 0.",
            level = API.Level.secondary,
            direction = API.Direction.INOUT)
    public long score_training_samples;

    @Override
    public StackedEnsembleParametersV99 fillFromImpl(StackedEnsembleModel.StackedEnsembleParameters impl) {
      super.fillFromImpl(impl);
      
      if (impl._blending!= null) {
        Value v = DKV.get(impl._blending);
        if (v != null) {
          blending_frame = new KeyV3.FrameKeyV3(((Frame) v.get())._key);
        }
      }
      return this;
    }

    public StackedEnsembleModel.StackedEnsembleParameters fillImpl(StackedEnsembleModel.StackedEnsembleParameters impl) {
      super.fillImpl(impl);
      impl._blending = (this.blending_frame == null) ? null : Key.<Frame>make(this.blending_frame.name);
      
      if (metalearner_params != null && !metalearner_params.isEmpty()) {
        Properties p = new Properties();
        
        HashMap<String, String[]> map = new Gson().fromJson(metalearner_params, new TypeToken<HashMap<String, String[]>>() {
        }.getType());
        for (Map.Entry<String, String[]> param : map.entrySet()) {
          String[] paramVal = param.getValue();
          if (paramVal.length == 1) {
            p.setProperty(param.getKey(), paramVal[0]);
          } else {
            p.setProperty(param.getKey(), Arrays.toString(paramVal));
          }
        }
        
        ModelParametersSchemaV3 paramsSchema;
        Model.Parameters params;
        switch (metalearner_algorithm) {
          case AUTO:
          case glm:
            paramsSchema = new GLMV3.GLMParametersV3();
            params = new GLMModel.GLMParameters();
            // FIXME: This is here because there is no Family.AUTO. It enables us to know if the user specified family or not.
            // FIXME: Family.AUTO will be implemented in https://0xdata.atlassian.net/projects/PUBDEV/issues/PUBDEV-7444
            ((GLMModel.GLMParameters) params)._family = null;
            break;
          case gbm:
            paramsSchema = new GBMV3.GBMParametersV3();
            params = new GBMModel.GBMParameters();
            break;
          case drf:
            paramsSchema = new DRFV3.DRFParametersV3();
            params = new DRFModel.DRFParameters();
            break;
          case deeplearning:
            paramsSchema = new DeepLearningV3.DeepLearningParametersV3();
            params = new DeepLearningModel.DeepLearningParameters();
            break;
          default:
            throw new UnsupportedOperationException("Unknown meta-learner algo: " + metalearner_algorithm);
        }
        
        paramsSchema.init_meta();
        impl._metalearner_parameters = (Model.Parameters) paramsSchema
            .fillFromImpl(params)
            .fillFromParms(p, true)
            .createAndFillImpl();
        super.fillImpl(impl);
      }
      return impl;
    }
  }
}
