package hex.schemas;

import com.google.gson.reflect.TypeToken;
import hex.StackedEnsembleModel.StackedEnsembleParameters.MetalearnerAlgorithm;
import hex.ensemble.StackedEnsemble;
import hex.StackedEnsembleModel;
import hex.tree.gbm.GBMModel;
import hex.tree.drf.DRFModel;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLMModel;
import hex.Model;

import water.Key;
import water.api.API;
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
      "base_models",
      "metalearner_algorithm",
      "metalearner_nfolds",
      "metalearner_fold_assignment",
      "metalearner_fold_column",
      "keep_levelone_frame",
      "metalearner_params",
      "blending_frame",
      "seed",
      "export_checkpoints_dir"
    };


    // Base models
    @API(level = API.Level.critical,
            help = "List of models (or model ids) to ensemble/stack together. Models must have been cross-validated using nfolds > 1, and folds must be identical across models.", required = true)
    public KeyV3.ModelKeyV3 base_models[];

    
    // Metalearner algorithm
    @API(level = API.Level.critical, direction = API.Direction.INOUT, 
            values = {"AUTO", "glm", "gbm", "drf", "deeplearning"},
            help = "Type of algorithm to use as the metalearner. " +
                    "Options include 'AUTO' (GLM with non negative weights; if validation_frame is present, a lambda search is performed), 'glm' (GLM with default parameters), 'gbm' (GBM with default parameters), " +
                    "'drf' (Random Forest with default parameters), or 'deeplearning' (Deep Learning with default parameters).")
    public MetalearnerAlgorithm metalearner_algorithm;

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
