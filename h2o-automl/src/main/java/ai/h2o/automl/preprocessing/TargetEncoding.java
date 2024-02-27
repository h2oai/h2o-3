package ai.h2o.automl.preprocessing;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec.AutoMLBuildControl;
import ai.h2o.automl.AutoMLBuildSpec.AutoMLInput;
import ai.h2o.automl.events.EventLogEntry.Stage;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderModel;
import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import ai.h2o.targetencoding.TargetEncoderPreprocessor;
import hex.Model;
import hex.Model.Parameters.FoldAssignmentScheme;
import hex.ModelPreprocessor;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.ast.prims.advmath.AstKFold;
import water.util.ArrayUtils;

import java.util.*;
import java.util.function.Predicate;

public class TargetEncoding implements PreprocessingStep {
    
    public static String CONFIG_ENABLED = "target_encoding_enabled";
    public static String CONFIG_PREPARE_CV_ONLY = "target_encoding_prepare_cv_only";
    
    static String TE_FOLD_COLUMN_SUFFIX = "_te_fold";
    private static final Completer NOOP = () -> {};
    
    private AutoML _aml;
    private TargetEncoderPreprocessor _tePreprocessor;
    private TargetEncoderModel _teModel;
    private final List<Completer> _disposables = new ArrayList<>();

    private TargetEncoderParameters _defaultParams;
    private boolean _encodeAllColumns = false; // if true, bypass all restrictions in columns selection.
    private int _columnCardinalityThreshold = 25;  // the minimal cardinality for a column to be TE encoded. 

    public TargetEncoding(AutoML aml) {
        _aml = aml;
    }

    @Override
    public String getType() {
        return PreprocessingStepDefinition.Type.TargetEncoding.name();
    }

    @Override
    public void prepare() {
        AutoMLInput amlInput = _aml.getBuildSpec().input_spec;
        AutoMLBuildControl amlBuild = _aml.getBuildSpec().build_control;
        Frame amlTrain = _aml.getTrainingFrame();
        
        TargetEncoderParameters params = (TargetEncoderParameters) getDefaultParams().clone();
        params._train = amlTrain._key;
        params._response_column = amlInput.response_column;
        params._seed = amlBuild.stopping_criteria.seed();
        
        Set<String> teColumns = selectColumnsToEncode(amlTrain, params);
        if (teColumns.isEmpty()) return;
        
        _aml.eventLog().warn(Stage.FeatureCreation,
                "Target Encoding integration in AutoML is in an experimental stage, the models obtained with this feature can not yet be downloaded as MOJO for production.");

        
        if (_aml.isCVEnabled()) {
            params._data_leakage_handling = DataLeakageHandlingStrategy.KFold;
            params._fold_column = amlInput.fold_column;
            if (params._fold_column == null) {
                //generate fold column
                Frame train = new Frame(params.train());
                Vec foldColumn = createFoldColumn(
                        params.train(), 
                        FoldAssignmentScheme.Modulo,
                        amlBuild.nfolds,
                        params._response_column,
                        params._seed
                );
                DKV.put(foldColumn);
                params._fold_column = params._response_column+TE_FOLD_COLUMN_SUFFIX;
                train.add(params._fold_column, foldColumn);
                register(train, params._train.toString(), true);
                params._train = train._key;
                _disposables.add(() -> {
                    foldColumn.remove();
                    DKV.remove(train._key);
                });
            }
        }
        String[] keep = params.getNonPredictors();
        params._ignored_columns = Arrays.stream(amlTrain.names())
                .filter(col -> !teColumns.contains(col) && !ArrayUtils.contains(keep, col))
                .toArray(String[]::new);

        TargetEncoder te = new TargetEncoder(params, _aml.makeKey(getType(), null, false));
        _teModel = te.trainModel().get();
        _tePreprocessor = new TargetEncoderPreprocessor(_teModel);
    }

    @Override
    public Completer apply(Model.Parameters params, PreprocessingConfig config) {
        if (_tePreprocessor == null || !config.get(CONFIG_ENABLED, true)) return NOOP;
        
        if (!config.get(CONFIG_PREPARE_CV_ONLY, false))
            params._preprocessors = (Key<ModelPreprocessor>[])ArrayUtils.append(params._preprocessors, _tePreprocessor._key);
        
        Frame train = new Frame(params.train());
        String foldColumn = _teModel._parms._fold_column;
        boolean addFoldColumn = foldColumn != null && train.find(foldColumn) < 0;
        if (addFoldColumn) {
            train.add(foldColumn,  _teModel._parms._train.get().vec(foldColumn));
            register(train, params._train.toString(), true);
            params._train = train._key;
            params._fold_column = foldColumn;
            params._nfolds = 0; // to avoid confusion or errors
            params._fold_assignment = FoldAssignmentScheme.AUTO; // to avoid confusion or errors
        }
        
        return () -> {
            //revert train changes
            if (addFoldColumn) {
                DKV.remove(train._key);
            }
        };
    }

    @Override
    public void dispose() {
        for (Completer disposable : _disposables) disposable.run();
    }

    @Override
    public void remove() {
        if (_tePreprocessor != null) {
            _tePreprocessor.remove(true);
            _tePreprocessor = null;
            _teModel = null;
        }
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

    TargetEncoderPreprocessor getTEPreprocessor() {
        return _tePreprocessor;
    }

    TargetEncoderModel getTEModel() {
        return _teModel;
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
