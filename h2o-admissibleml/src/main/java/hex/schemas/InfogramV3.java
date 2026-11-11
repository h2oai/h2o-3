package hex.schemas;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import hex.Infogram.Infogram;
import hex.Infogram.InfogramModel;
import hex.Model;
import hex.ModelBuilder;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLMModel;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;
import water.api.API;
import water.api.EnumValuesProvider;
import water.api.SchemaServer;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;

import java.util.*;

import static hex.util.DistributionUtils.distributionToFamily;

public class InfogramV3 extends ModelBuilderSchema<Infogram, InfogramV3, InfogramV3.InfogramParametersV3> {
  public static final class InfogramParametersV3 extends ModelParametersSchemaV3<InfogramModel.InfogramParameters, InfogramParametersV3> {
    public static final String[] fields = new String[] {
            "model_id",
            "training_frame",
            "validation_frame",
            "seed",
            "keep_cross_validation_models",
            "keep_cross_validation_predictions",
            "keep_cross_validation_fold_assignment",
            "nfolds",
            "fold_assignment",
            "fold_column",
            "response_column",
            "ignored_columns",
            "ignore_const_cols",
            "score_each_iteration",
            "offset_column",
            "weights_column",
            "standardize",
            "distribution",
            "plug_values",
            "max_iterations",
            "stopping_rounds",
            "stopping_metric",
            "stopping_tolerance",
            "balance_classes",
            "class_sampling_factors",
            "max_after_balance_size",
            "max_runtime_secs",
            "custom_metric_func",
            "auc_type",
            // new parameters for INFOGRAMs only
            "algorithm", // choose algo and parameter to generate infogram
            "algorithm_params",
            "protected_columns",
            "total_information_threshold",
            "net_information_threshold",
            "relevance_index_threshold",
            "safety_index_threshold",
            "data_fraction",
            "top_n_features"
    };

    @API(help = "Seed for pseudo random number generator (if applicable).", gridable = true)
    public long seed;

    // Input fields
    @API(help = "Standardize numeric columns to have zero mean and unit variance.", level = API.Level.critical)
    public boolean standardize;

    @API(help = "Plug Values (a single row frame containing values that will be used to impute missing values of the" +
            " training/validation frame, use with conjunction missing_values_handling = PlugValues).", 
            direction = API.Direction.INPUT)
    public KeyV3.FrameKeyV3 plug_values;

    @API(help = "Maximum number of iterations.", level = API.Level.secondary)
    public int max_iterations;

    @API(help = "Prior probability for y==1. To be used only for logistic regression iff the data has been sampled " +
            "and the mean of response does not reflect reality.", level = API.Level.expert)
    public double prior;

    /**
     * For imbalanced data, balance training data class counts via
     * over/under-sampling. This can result in improved predictive accuracy.
     */
    @API(help = "Balance training data class counts via over/under-sampling (for imbalanced data).", 
            level = API.Level.secondary, direction = API.Direction.INOUT)
    public boolean balance_classes;

    /**
     * Desired over/under-sampling ratios per class (lexicographic order).
     * Only when balance_classes is enabled.
     * If not specified, they will be automatically computed to obtain class balance during training.
     */
    @API(help = "Desired over/under-sampling ratios per class (in lexicographic order). If not specified, sampling" +
            " factors will be automatically computed to obtain class balance during training. Requires " +
            "balance_classes.", level = API.Level.expert, direction = API.Direction.INOUT)
    public float[] class_sampling_factors;

    /**
     * When classes are balanced, limit the resulting dataset size to the
     * specified multiple of the original dataset size.
     */
    @API(help = "Maximum relative size of the training data after balancing class counts (can be less than 1.0). " +
            "Requires balance_classes.", /* dmin=1e-3, */ level = API.Level.expert, direction = API.Direction.INOUT)
    public float max_after_balance_size;
    
    //Infogram fields
    @API(level = API.Level.critical, direction = API.Direction.INOUT,
            valuesProvider = InfogramAlrogithmProvider.class,
            help = "Type of machine learning algorithm used to build the infogram. Options include "
                    + "'AUTO' (gbm), "
                    + "'deeplearning' (Deep Learning with default parameters), "
                    + "'drf' (Random Forest with default parameters), "
                    + "'gbm' (GBM with default parameters), "
                    + "'glm' (GLM with default parameters), "
                    + "or 'xgboost' (if available, XGBoost with default parameters)."
    )
    
    public InfogramModel.InfogramParameters.Algorithm algorithm;

    @API(help = "Customized parameters for the machine learning algorithm specified in the algorithm parameter.",
            level = API.Level.expert, gridable=true)
    public String algorithm_params;

    @API(help = "Columns that contain features that are sensitive and need to be protected (legally, or otherwise), " + 
            "if applicable. These features (e.g. race, gender, etc) should not drive the prediction of the response.",
            level = API.Level.secondary, gridable=true)
    public String[] protected_columns;    

    @API(help = "A number between 0 and 1 representing a threshold for total information.  " + 
            "For a specific feature, if the total information is higher than this threshold, and the corresponding " + 
            "net information is also higher than the threshold ``net_information_threshold``, that feature will be " + 
            "considered admissible. The total information is the x-axis of the Core Infogram. ",
            level = API.Level.secondary, gridable = true)
    public double total_information_threshold;       

    @API(help = "A number between 0 and 1 representing a threshold for net information.  For a " + 
            "specific feature, if the net information is higher than this threshold, and the corresponding total " + 
            "information is also higher than the total_information_threshold, that feature will be considered admissible. " + 
            "The net information is the y-axis of the Core Infogram.",
            level = API.Level.secondary, gridable = true)
    public double net_information_threshold; 

    @API(help = "A number between 0 and 1 representing a threshold for the relevance index.  This is " + 
            "only used when ``protected_columns`` is set by the user.  For a specific feature, if the relevance index " + 
            "value is higher than this threshold, and the corresponding safety index is also higher than the " + 
            "safety_index_threshold``, that feature will be considered admissible.  The relevance index is the x-axis " + 
            "of the Fair Infogram.", 
            level = API.Level.secondary, gridable = true)
    public double relevance_index_threshold;    

    @API(help = "A number between 0 and 1 representing a threshold for the safety index.  This is " + 
            "only used when protected_columns is set by the user.  For a specific feature, if the safety index value " + 
            "is higher than this threshold, and the corresponding relevance index is also higher than the " + 
            "relevance_index_threshold, that feature will be considered admissible.  The safety index is the y-axis of " + 
            "the Fair Infogram.",
            level = API.Level.secondary, gridable = true)
    public double safety_index_threshold;

    @API(help = "The fraction of training frame to use to build the infogram model. Any value greater " + 
            "than 0 and less than or equal to 1.0 is acceptable.",
            level = API.Level.secondary, gridable = true)
    public double data_fraction;

    @API(help = "An integer specifying the number of columns to evaluate in the infogram.  The columns are ranked by " + 
            "variable importance, and the top N are evaluated.",
            level = API.Level.secondary, gridable = true)
    public int top_n_features;

      
    public InfogramModel.InfogramParameters fillImpl(InfogramModel.InfogramParameters impl) {
      super.fillImpl(impl);
      if (algorithm_params != null && !algorithm_params.isEmpty()) {
        Properties p = generateProperties(algorithm_params);
        ParamNParamSchema schemaParams = generateParamsSchema(algorithm);
        schemaParams._paramsSchema.init_meta();
        impl._infogram_algorithm_parameters = (Model.Parameters) schemaParams._paramsSchema
                .fillFromImpl(schemaParams._params)
                .fillFromParms(p, true)
                .createAndFillImpl();
        super.fillImpl(impl);
      }
      return impl;
    }
    
    public static void generateModelParams(InfogramModel.InfogramParameters parms, Properties p, 
                                                ArrayList<String> excludeList) {
      ModelParametersSchemaV3 paramsSchema;
      Model.Parameters params;
      switch (parms._algorithm) {
        case glm:
          paramsSchema = new GLMV3.GLMParametersV3();
          params = new GLMModel.GLMParameters();
          excludeList.add("_distribution");
          ((GLMModel.GLMParameters) params)._family = distributionToFamily(parms._distribution);
          break;
        case AUTO: // auto defaults to GBM
        case gbm:
          paramsSchema = new GBMV3.GBMParametersV3();
          params = new GBMModel.GBMParameters();
          if (!excludeList.contains("_stopping_tolerance")) {
            params._stopping_tolerance = 0.01;  // set default to 0.01
            excludeList.add("_stopping_tolerance");
          }
          break;
        case drf:
          paramsSchema = new DRFV3.DRFParametersV3();
          params = new DRFModel.DRFParameters();
          if (!excludeList.contains("_stopping_tolerance")) {
            params._stopping_tolerance = 0.01;  // set default to 0.01
            excludeList.add("_stopping_tolerance");
          }
          break;
        case deeplearning:
          paramsSchema = new DeepLearningV3.DeepLearningParametersV3();
          params = new DeepLearningModel.DeepLearningParameters();
          break;
        case xgboost:
          params = ModelBuilder.makeParameters("XGBoost");
          paramsSchema = (ModelParametersSchemaV3<?, ?>) SchemaServer.schema(params);
          break;
        default:
          throw new UnsupportedOperationException("Unknown algo: " + parms._algorithm);
      }

      paramsSchema.init_meta();
      parms._infogram_algorithm_parameters =  (Model.Parameters) paramsSchema
              .fillFromImpl(params)
              .fillFromParms(p, true)
              .createAndFillImpl();
    }
    
    Properties generateProperties(String algoParms) {
      Properties p = new Properties();
      HashMap<String, String[]> map = new Gson().fromJson(algoParms, new TypeToken<HashMap<String, String[]>>() {
      }.getType());
      for (Map.Entry<String, String[]> param : map.entrySet()) {
        String[] paramVal = param.getValue();
        if (paramVal.length == 1) {
          p.setProperty(param.getKey(), paramVal[0]);
        } else {
          p.setProperty(param.getKey(), Arrays.toString(paramVal));
        }
      }
      return p;
    }
    
    private class ParamNParamSchema {
      private ModelParametersSchemaV3 _paramsSchema;
      private Model.Parameters _params;
      
      public ParamNParamSchema(ModelParametersSchemaV3 schema, Model.Parameters params) {
        _paramsSchema = schema;
        _params = params;
      }
    }

    ParamNParamSchema generateParamsSchema(InfogramModel.InfogramParameters.Algorithm chosenAlgo) {
      ModelParametersSchemaV3<?, ?> paramsSchema;
      Model.Parameters params;
      switch (chosenAlgo) {
        case AUTO:
        case glm:
          paramsSchema = new GLMV3.GLMParametersV3();
          params = new GLMModel.GLMParameters();
          ((GLMModel.GLMParameters) params)._family = GLMModel.GLMParameters.Family.AUTO;
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
        case xgboost:
          params = ModelBuilder.makeParameters("XGBoost");
          paramsSchema = (ModelParametersSchemaV3<?, ?>) SchemaServer.schema(params);
          break;
        default:
          throw new UnsupportedOperationException("Unknown given algo: " + chosenAlgo);
      }
      return new ParamNParamSchema(paramsSchema, params);
    }
  }

  public static final class InfogramAlrogithmProvider extends EnumValuesProvider<InfogramModel.InfogramParameters.Algorithm> {
    public InfogramAlrogithmProvider() { super(InfogramModel.InfogramParameters.Algorithm.class); }
  }
}
