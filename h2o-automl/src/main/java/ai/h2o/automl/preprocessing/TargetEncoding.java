package ai.h2o.automl.preprocessing;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec.AutoMLInput;
import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import ai.h2o.targetencoding.pipeline.transformers.TargetEncoderFeatureTransformer;
import hex.Model.Parameters.FoldAssignmentScheme;
import hex.pipeline.DataTransformer;
import hex.pipeline.transformers.KFoldColumnGenerator;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.ast.prims.advmath.AstKFold;
import water.util.ArrayUtils;

import java.util.*;
import java.util.function.Predicate;

public class TargetEncoding implements PipelineStep {
    
    private AutoML _aml;
    private TargetEncoderParameters _defaultParams;
    private boolean _encodeAllColumns = false; // if true, bypass all restrictions in columns selection.
    private int _columnCardinalityThreshold = 25;  // the minimal cardinality for a column to be TE encoded. 

    public TargetEncoding(AutoML aml) {
        _aml = aml;
    }

    @Override
    public String getType() {
        return PipelineStepDefinition.Type.TargetEncoding.name();
    }

    public void setDefaultParams(TargetEncoderParameters defaultParams) {
        _defaultParams = defaultParams;
    }

    public void setEncodeAllColumns(boolean encodeAllColumns) {
        _encodeAllColumns = encodeAllColumns;
    }

    public void setColumnCardinalityThreshold(int threshold) {
        _columnCardinalityThreshold = threshold;
    }

    private TargetEncoderParameters getDefaultParams() {
        if (_defaultParams != null) return _defaultParams;
        
        _defaultParams = new TargetEncoderParameters();
        _defaultParams._keep_original_categorical_columns = false;
        _defaultParams._blending = true;
        _defaultParams._inflection_point = 5;
        _defaultParams._smoothing = 10;
        _defaultParams._noise = 0;
        
        return _defaultParams;
    }

    private Set<String> selectColumnsToEncode(Frame fr, TargetEncoderParameters params) {
        final Set<String> encode = new HashSet<>();
        if (_encodeAllColumns) {
            encode.addAll(Arrays.asList(fr.names()));
        } else {
            Predicate<Vec> cardinalityLargeEnough = v -> v.cardinality() >= _columnCardinalityThreshold;
            Predicate<Vec> cardinalityNotTooLarge = params._blending
                    ? v -> (double) fr.numRows() / v.cardinality() > params._inflection_point
                    : v -> true;

            for (int i = 0; i < fr.names().length; i++) {
                Vec v = fr.vec(i);
                if (cardinalityLargeEnough.test(v) && cardinalityNotTooLarge.test(v))
                    encode.add(fr.name(i));
            }
        }

        AutoMLInput amlInput = _aml.getBuildSpec().input_spec;
        List<String> nonPredictors = Arrays.asList(
                amlInput.weights_column,
                amlInput.fold_column,
                amlInput.response_column
        );
        encode.removeAll(nonPredictors);
        return encode;
    }

    @Override
    public DataTransformer[] pipelineTransformers() {
      List<DataTransformer> dts = new ArrayList<>();
      TargetEncoderParameters teParams = (TargetEncoderParameters) getDefaultParams().clone();
      Frame train = _aml.getTrainingFrame();
      Set<String> teColumns = selectColumnsToEncode(train, teParams);
      if (teColumns.isEmpty()) return new DataTransformer[0];
      
      String[] keep = teParams.getNonPredictors();
      teParams._ignored_columns = Arrays.stream(train.names())
              .filter(col -> !teColumns.contains(col) && !ArrayUtils.contains(keep, col))
              .toArray(String[]::new);
      if (_aml.isCVEnabled()) {
        dts.add(new KFoldColumnGenerator()
                .name("add_fold_column")
                .description("If cross-validation is enabled, generates (if needed) a fold column used by Target Encoder and for the final estimator")
//                .init()
        );
        teParams._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
      }
      dts.add(new TargetEncoderFeatureTransformer(teParams)
              .name("default_TE")
              .description("Applies Target Encoding to selected categorical features")
              .enableCache()
//              .init()
      );
      return dts.toArray(new DataTransformer[0]);
    }

    @Override
    public Map<String, Object[]> pipelineTransformersHyperParams() {
        Map<String, Object[]> hp = new HashMap<>();
        hp.put("default_TE._enabled", new Boolean[] {Boolean.TRUE, Boolean.FALSE});
        hp.put("default_TE._keep_original_categorical_columns", new Boolean[] {Boolean.TRUE, Boolean.FALSE});
        hp.put("default_TE._blending", new Boolean[] {Boolean.TRUE, Boolean.FALSE});
        return hp;
    }

    private static void register(Frame fr, String keyPrefix, boolean force) {
        Key<Frame> key = fr._key;
        if (key == null || force)
            fr._key = keyPrefix == null ? Key.make() : Key.make(keyPrefix+"_"+Key.rand());
        if (force) DKV.remove(key);
        DKV.put(fr);
    }

    public static Vec createFoldColumn(Frame fr,
                                       FoldAssignmentScheme fold_assignment,
                                       int nfolds,
                                       String responseColumn,
                                       long seed) {
        Vec foldColumn;
        switch (fold_assignment) {
            default:
            case AUTO:
            case Random:
                foldColumn = AstKFold.kfoldColumn(fr.anyVec().makeZero(), nfolds, seed);
                break;
            case Modulo:
                foldColumn = AstKFold.moduloKfoldColumn(fr.anyVec().makeZero(), nfolds);
                break;
            case Stratified:
                foldColumn = AstKFold.stratifiedKFoldColumn(fr.vec(responseColumn), nfolds, seed);
                break;
        }
        return foldColumn;
    }
    
}
