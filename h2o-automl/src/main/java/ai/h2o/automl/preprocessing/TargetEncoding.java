package ai.h2o.automl.preprocessing;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.AutoMLBuildSpec.AutoMLBuildControl;
import ai.h2o.automl.AutoMLBuildSpec.AutoMLInput;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderModel;
import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import ai.h2o.targetencoding.TargetEncoderPreprocessor;
import hex.Model;
import hex.Model.Parameters.FoldAssignmentScheme;
import hex.ModelPreprocessor;
import water.DKV;
import water.Iced;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.ast.prims.advmath.AstKFold;
import water.util.ArrayUtils;

import java.util.*;

public class TargetEncoding implements PreprocessingStep {
    
    static String TE_FOLD_COLUMN_SUFFIX = "_te_fold";
    private static final Completer NOOP = () -> {};
    
    private AutoML _aml;
    private TargetEncoderPreprocessor _tePreprocessor;
    private TargetEncoderModel _teModel;
    private final List<Completer> _disposables = new ArrayList<>();
    private int _cardinalityThreshold = 0;  // the minimal cardinality for a column to be TE encoded. 

    public TargetEncoding(AutoML aml) {
        _aml = aml;
    }

    @Override
    public String getType() {
        return PreprocessingStepDefinition.Type.TargetEncoding.name();
    }

    @Override
    public void prepare() {
        Frame amlTrain = _aml.getTrainingFrame();
        Set<String> teColumns = selectColumnsToEncode(amlTrain);
        if (teColumns.isEmpty()) return;

        AutoMLInput amlInput = _aml.getBuildSpec().input_spec;
        AutoMLBuildControl amlBuild = _aml.getBuildSpec().build_control;
        TargetEncoderParameters params = new TargetEncoderParameters();
        params._train = amlTrain._key;
        params._response_column = amlInput.response_column;
        params._keep_original_categorical_columns = false;
        params._blending = true;
        params._noise = 0;
        params._seed = amlBuild.stopping_criteria.seed();
        
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
                addFoldColumn(train, params._fold_column, foldColumn, params._train.toString());
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
    public Completer apply(Model.Parameters params) {
        if (_tePreprocessor == null) return NOOP;
        
        params._preprocessors = (Key<ModelPreprocessor>[])ArrayUtils.append(params._preprocessors, _tePreprocessor._key);
        
        Frame train = new Frame(params.train());
        String foldColumn = _teModel._parms._fold_column;
        boolean addFoldColumn = foldColumn != null && train.find(foldColumn) < 0;
        if (addFoldColumn) {
            addFoldColumn(train, foldColumn, _teModel._parms._train.get().vec(foldColumn), params._train.toString());
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

    public void setCardinalityThreshold(int cardinalityThreshold) {
        _cardinalityThreshold = cardinalityThreshold;
    }

    private Set<String> selectColumnsToEncode(Frame fr) {
        Set<String> encode = new HashSet<>();
        for (int i = 0; i < fr.names().length; i++) {
            if (fr.vec(i).cardinality() >= _cardinalityThreshold) encode.add(fr.name(i));
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

    private static void addFoldColumn(Frame fr, String name, Vec foldColumn, String keyPrefix) {
        fr.add(name, foldColumn);
        if (fr._key == null) 
            fr._key = keyPrefix == null ? Key.make() : Key.make(keyPrefix+"_"+Key.rand());
        DKV.put(fr);
    }

    private static Vec createFoldColumn(Frame fr,
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
