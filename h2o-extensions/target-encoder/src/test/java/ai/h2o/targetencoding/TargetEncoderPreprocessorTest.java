package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import hex.Model;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import static water.TestUtil.ar;
import static water.TestUtil.ignoredColumns;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncoderPreprocessorTest {
    
    private static String TARGET = "target";
    private static String FOLDC = "foldc";
    
    @Test
    public void test_model_building_with_CV_and_TE_KFold_strategy() {
        try {
            Scope.enter();
            Frame train = makeTrainFrame(true);
//            Frame valid = makeValidFrame();
            
            TargetEncoderModel teModel = trainTE(train, DataLeakageHandlingStrategy.KFold);
            Scope.track_generic(teModel);
            TargetEncoderPreprocessor tePreproc = new TargetEncoderPreprocessor(teModel);
            Scope.track_generic(tePreproc);
            
            Model model = buildModel(train, null, tePreproc);
            Scope.track_generic(model);
            
        } finally {
            Scope.exit();
        }
    }
    
    
    @Test
    public void test_model_building_without_CV_and_with_TE_None_strategy() {
        try {
            Scope.enter();
            
        } finally {
            Scope.exit();
        }
    }
    
    private Frame makeTrainFrame(boolean withFoldColumn) {
        TestFrameBuilder builder = new TestFrameBuilder()
                .withName("trainFrame")
                .withColNames(withFoldColumn
                        ? new String[] {"numerical", "categorical", "noTE", TARGET, "foldc"}
                        : new String[] {"numerical", "categorical", "noTE", TARGET})
                .withVecTypes(withFoldColumn
                        ? new byte[] {Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM}
                        : new byte[] {Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_CAT})
//                .withChunkLayout(6, 6)
                .withDataForCol(0, ar(3, 3, 3, 2, 2, 2, 1, 1, 1, 0, 0, 0))
                .withDataForCol(1, ar("a", "b", "c", "a", "a", "b", "b", "c", "c", "a", "b", "c"))
                .withDataForCol(2, ar("a", "b", "c", "a", "a", "b", "b", "c", "c", "a", "b", "c"))
                .withDataForCol(3, ar("N", "Y", "N", "N", "Y", "Y", "N", "N", "N", "Y", "Y", "Y"));
        if (withFoldColumn)
            builder.withDataForCol(4, ar(1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3));
        return builder.build();
    }
    
    private Frame makeValidFrame() {
        return new TestFrameBuilder()
                .withName("validFrame")
                .withColNames("categorical", TARGET)
                .withVecTypes(Vec.T_CAT, Vec.T_CAT)
                .withDataForCol(0, ar("a", "b", "a", "b", "a"))
                .withDataForCol(1, ar("N", "Y", "Y", "N", "Y"))
                .withChunkLayout(3, 2)
                .build();
    }
    
    private TargetEncoderModel trainTE(Frame train, DataLeakageHandlingStrategy strategy) {
        TargetEncoderParameters params = new TargetEncoderParameters();
        params._keep_original_features = false;
        params._train = train._key;
        params._response_column = TARGET;
        params._fold_column = ArrayUtils.contains(train.names(), FOLDC) ? FOLDC : null;
        params._ignored_columns = ignoredColumns(train, "categorical", TARGET, FOLDC);
        params._data_leakage_handling = strategy;
        params._noise = 0;
        params._seed = 42;
        
        TargetEncoder te = new TargetEncoder(params);
        return te.trainModel().get();
    }
    
    private Model buildModel(Frame train, Frame valid, TargetEncoderPreprocessor preprocessor) {
        GBMModel.GBMParameters params = new GBMModel.GBMParameters();
        params._seed = 987;
        params._train = train._key;
        params._valid = valid == null ? null : valid._key;
        params._response_column = TARGET;
        params._preprocessors = new Key[] {preprocessor._key};
        params._min_rows = 1;
        
        if (ArrayUtils.contains(train.names(), FOLDC)) {
            params._fold_column = FOLDC;
            params._keep_cross_validation_models = true;
            params._keep_cross_validation_predictions = true;
        }

        GBM gbm = new GBM(params);
        GBMModel model = gbm.trainModel().get();
        return model;
    }
    
}
