package hex.schemas;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import hex.Infogram.Infogram;
import hex.Infogram.InfogramModel;
import hex.Model;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLMModel;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;
import hex.tree.xgboost.XGBoostModel;
import water.api.API;
import water.api.EnumValuesProvider;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParametersSchemaV3;
import static hex.util.DistributionUtils.distributionToFamily;
import java.util.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

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
            "net_information_threshold",
            "total_information_threshold",
            "safety_index_threshold",
            "relevance_index_threshold",
            "data_fraction",
            "top_n_features",
            "compute_p_values"
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
    
    @API(level = API.Level.critical, direction = API.Direction.INOUT,
            valuesProvider = InfogramAlrogithmProvider.class,
            help = "Type of algorithm to use to build infogram. Options include "
                    + "'AUTO' (gbm), "
                    + "'deeplearning' (Deep Learning with default parameters), "
                    + "'drf' (Random Forest with default parameters), "
                    + "'gbm' (GBM with default parameters), "
                    + "'glm' (GLM with default parameters), "
                    + "or 'xgboost' (if available, XGBoost with default parameters)."
    )
    
    public InfogramModel.InfogramParameters.Algorithm algorithm;

    @API(help = "Parameters specified to the chosen algorithm can be passed to infogram using algorithm_params.",
            level = API.Level.expert, gridable=true)
    public String algorithm_params;

    @API(help = "Predictors that are to be excluded from model due to them being discriminatory or inappropriate for" +
            " whatever reason.", level = API.Level.secondary, gridable=true)
    public String[] protected_columns;

    @API(help = "Conditional information for core infogram threshold between 0 and 1 that is used to decide whether a " +
            "predictor's conditional information is high enough to be chosen into the admissible feature set.  " +
            "Default to -1 which will be set to 0.1 eventually.",
            level = API.Level.secondary, gridable = true)
    public double net_information_threshold;

    @API(help = "Conditional information for fair infogram threshold between 0 and 1 that is used to decide whether a" +
            " predictor's conditional information is high enough to be chosen into the admissible feature set.  " +
            "Default to -1 which will be set to 0.1 eventually.",
            level = API.Level.secondary, gridable = true)
    public double safety_index_threshold;

    @API(help = "Relevance threshold for fair infogram between 0 and 1 that is used to decide whether a predictor's" +
            " relevance level is high enough to be chosen into the admissible feature set.  Default to -1 which will" +
            " be set to 0.1 eventually.", 
            level = API.Level.secondary, gridable = true)
    public double relevance_index_threshold;

    @API(help = "Relevance threshold for core infogram between 0 and 1 that is used to decide whether a predictor's" +
            " relevance level is high enough to be chosen into the admissible feature set.  Defaults to -1 which will" +
            " be set to 0.1 eventually.",
            level = API.Level.secondary, gridable = true)
    public double total_information_threshold;

    @API(help = "Fraction of training frame to use to build the infogram model.  Defaults to 1.0.",
            level = API.Level.secondary, gridable = true)
    public double data_fraction;

    @API(help = "Number of top n variables to consider based on the variable importance.  Defaults to 0.0 which is to consider" +
            " all predictors.",
            level = API.Level.secondary, gridable = true)
    public int top_n_features;
    
    @API(help = "If true will calculate the p-value. Default to false.",
            level = API.Level.secondary, gridable = false)

    public boolean compute_p_values;  // for GLM
      
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
          paramsSchema = new XGBoostV3.XGBoostParametersV3();
          params = new XGBoostModel.XGBoostParameters();
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
      ModelParametersSchemaV3 paramsSchema;
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
          paramsSchema = new XGBoostV3.XGBoostParametersV3();
          params = new XGBoostModel.XGBoostParameters();
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
