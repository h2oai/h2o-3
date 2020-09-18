package hex.schemas;
import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderModel;
import water.api.API;
import water.api.EnumValuesProvider;
import water.api.schemas3.ModelParametersSchemaV3;

import java.util.ArrayList;
import java.util.List;

public class TargetEncoderV3 extends ModelBuilderSchema<TargetEncoder, TargetEncoderV3, TargetEncoderV3.TargetEncoderParametersV3> {
  public static class TargetEncoderParametersV3 extends ModelParametersSchemaV3<TargetEncoderModel.TargetEncoderParameters, TargetEncoderParametersV3> {

    @API(help = "If true, the original non-encoded categorical features will remain in the result frame.",
            level = API.Level.critical)
    public boolean keep_original_categorical_columns;

    @API(help = "If true, enables blending of posterior probabilities (computed for a given categorical value) " +
            "with prior probabilities (computed on the entire set). " +
            "This allows to mitigate the effect of categorical values with small cardinality. " +
            "The blending effect can be tuned using the `inflection_point` and `smoothing` parameters.",
            level = API.Level.secondary)
    public boolean blending;

    @API(help = "Inflection point of the sigmoid used to blend probabilities (see `blending` parameter). " +
            "For a given categorical value, if it appears less that `inflection_point` in a data sample, " +
            "then the influence of the posterior probability will be smaller than the prior.",
            level = API.Level.secondary)
    public double inflection_point;

    @API(help = "Smoothing factor corresponds to the inverse of the slope at the inflection point " +
            "on the sigmoid used to blend probabilities (see `blending` parameter). " +
            "If smoothing tends towards 0, then the sigmoid used for blending turns into a Heaviside step function.",
            level = API.Level.secondary)
    public double smoothing;

    @API(help = "Data leakage handling strategy used to generate the encoding. Supported options are:\n" +
            "1) \"none\" (default) - no holdout, using the entire training frame.\n" +
            "2) \"leave_one_out\" - current row's response value is subtracted from the per-level frequencies pre-calculated on the entire training frame.\n" +
            "3) \"k_fold\" - encodings for a fold are generated based on out-of-fold data.\n",
            valuesProvider = DataLeakageHandlingStrategyProvider.class, 
            level = API.Level.secondary)
    public DataLeakageHandlingStrategy data_leakage_handling;
    
    @API(help = "The amount of noise to add to the encoded column. " +
            "Use 0 to disable noise, and -1 (=AUTO) to let the algorithm determine a reasonable amount of noise.",
            direction = API.Direction.INPUT, gridable = true, level = API.Level.expert)
    public double noise;
    
    @API(help = "Seed used to generate the noise. By default, the seed is chosen randomly.", 
            direction = API.Direction.INPUT, level = API.Level.expert)
    public long seed;
  
    @Override
    public String[] fields() {
      final List<String> params = new ArrayList<>();
      params.add("model_id");
      params.add("training_frame");
      params.add("fold_column");
      params.add("response_column");
      params.add("ignored_columns");
      params.addAll(extractDeclaredApiParameters(getClass()));

      return params.toArray(new String[0]);
    }
  }
  
  public static final class DataLeakageHandlingStrategyProvider extends EnumValuesProvider<DataLeakageHandlingStrategy> {
    public DataLeakageHandlingStrategyProvider() { super(DataLeakageHandlingStrategy.class); }
  }
}
