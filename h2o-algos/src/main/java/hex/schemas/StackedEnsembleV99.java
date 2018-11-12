package hex.schemas;

import hex.ensemble.StackedEnsemble;
import hex.StackedEnsembleModel;
import hex.tree.gbm.GBMModel;
import hex.tree.drf.DRFModel;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLMModel;
import hex.Model;

import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;
import water.api.schemas3.FrameV3;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

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
    public StackedEnsembleModel.StackedEnsembleParameters.MetalearnerAlgorithm metalearner_algorithm;

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

    @API(help = "Seed for random numbers; passed through to the metalearner algorithm. Defaults to -1 (time-based random number)", gridable = true)
    public long seed;

    public StackedEnsembleModel.StackedEnsembleParameters fillImpl(StackedEnsembleModel.StackedEnsembleParameters impl) {
      super.fillImpl(impl);
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
        switch (metalearner_algorithm) {
          case AUTO:
            GLMV3.GLMParametersV3 paramsAuto = new GLMV3.GLMParametersV3();
            paramsAuto.init_meta();
            paramsAuto.fillFromImpl(new GLMModel.GLMParameters());
            paramsAuto.fillFromParms(p, true);
            GLMModel.GLMParameters autoParams = paramsAuto.createAndFillImpl();
            impl._metalearner_parameters = autoParams;
            super.fillImpl(impl);
            break;
          case gbm:
            GBMV3.GBMParametersV3 paramsGBM = new GBMV3.GBMParametersV3();
            paramsGBM.init_meta();
            paramsGBM.fillFromImpl(new GBMModel.GBMParameters());
            paramsGBM.fillFromParms(p, true);
            GBMModel.GBMParameters gbmParams = paramsGBM.createAndFillImpl();
            impl._metalearner_parameters = gbmParams;
            super.fillImpl(impl);
            break;
          case drf:
            DRFV3.DRFParametersV3 paramsDRF = new DRFV3.DRFParametersV3();
            paramsDRF.init_meta();
            paramsDRF.fillFromImpl(new DRFModel.DRFParameters());
            paramsDRF.fillFromParms(p, true);
            DRFModel.DRFParameters drfParams = paramsDRF.createAndFillImpl();
            impl._metalearner_parameters = drfParams;
            super.fillImpl(impl);
            break;
          case glm:
            GLMV3.GLMParametersV3 paramsGLM = new GLMV3.GLMParametersV3();
            paramsGLM.init_meta();
            paramsGLM.fillFromImpl(new GLMModel.GLMParameters());
            paramsGLM.fillFromParms(p, true);
            GLMModel.GLMParameters glmParams = paramsGLM.createAndFillImpl();
            impl._metalearner_parameters = glmParams;
            super.fillImpl(impl);
            break;
          case deeplearning:
            DeepLearningV3.DeepLearningParametersV3 paramsDL = new DeepLearningV3.DeepLearningParametersV3();
            paramsDL.init_meta();
            paramsDL.fillFromImpl(new DeepLearningModel.DeepLearningParameters());
            paramsDL.fillFromParms(p, true);
            DeepLearningModel.DeepLearningParameters dlParams = paramsDL.createAndFillImpl();
            impl._metalearner_parameters = dlParams;
            super.fillImpl(impl);
            break;
          default:
            throw new UnsupportedOperationException("Unknown meta-learner algo: " + metalearner_algorithm);
        }
      }
      return impl;
    }
  }
}
