package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import hex.Model;
import hex.Model.Parameters.CategoricalEncodingScheme;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.targetencoder.TargetEncoderMojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.MultinomialModelPrediction;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;
import static water.TestUtil.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncoderTransformerTest {
    
    private static String[] TO_ENCODE = {"cat1", "cat2"};
    private static String[] ENCODED = {
            "cat1_N_te", "cat1_Y_te", 
            "cat2_N_te", "cat2_Y_te", 
            "cat1:cat2_N_te", "cat1:cat2_Y_te"
    };
    private static String[] NOT_ENCODED = {"noTE"};
    private static String[] NUMERICAL = {"num1", "num2"};
    private static String TARGET = "target";
    private static String FOLDC = "foldc";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    
    @Test
    public void test_model_building_with_CV_and_TE_KFold_strategy() {
        // just ensuring that the flow works without major issue as it is impossible to use Mockito with objects stored in DKV.
        try {
            Scope.enter();
            Frame train = makeTrainFrame(true);
            Frame valid = makeValidFrame();
            
            TargetEncoderModel teModel = trainTE(train, DataLeakageHandlingStrategy.KFold, false, false);
            Scope.track_generic(teModel);
            TargetEncoderTransformer teTrans = new TargetEncoderTransformer(teModel);
            Scope.track_generic(teTrans);
            
            Model model = buildModel(train, null, teTrans, CategoricalEncodingScheme.AUTO);
            Scope.track_generic(model);
            
            int expectedCVModels = 3;  //3 folds -> 3 cv models
            assertEquals(expectedCVModels, model._output._cross_validation_models.length);
            for (String col: ENCODED) assertTrue(ArrayUtils.contains(model._output._names, col));
            for (String col: TO_ENCODE) assertFalse(ArrayUtils.contains(model._output._names, col));
            for (String col: NOT_ENCODED) assertTrue(ArrayUtils.contains(model._output._names, col));
            
            Frame preds = model.score(valid);
            Scope.track(preds);
        } finally {
            Scope.exit();
        }
    }
    
    
    @Test
    public void test_model_building_without_CV_and_with_TE_None_strategy() {
        try {
            Scope.enter();
            Frame train = makeTrainFrame(false);
            Frame valid = makeValidFrame();

            TargetEncoderModel teModel = trainTE(train, DataLeakageHandlingStrategy.None, false, false);
            Scope.track_generic(teModel);
            TargetEncoderTransformer teTrans = new TargetEncoderTransformer(teModel);
            Scope.track_generic(teTrans);

            Model model = buildModel(train, valid, teTrans, CategoricalEncodingScheme.AUTO);
            Scope.track_generic(model);

            for (String col: ENCODED) assertTrue(ArrayUtils.contains(model._output._names, col));
            for (String col: TO_ENCODE) assertFalse(ArrayUtils.contains(model._output._names, col));
            for (String col: NOT_ENCODED) assertTrue(ArrayUtils.contains(model._output._names, col));

            Frame preds = model.score(valid);
            Scope.track(preds);
        } finally {
            Scope.exit();
        }
    }
    
    @Test public void test_model_mojo_includes_TE_preprocessor() throws Exception {
        try {
            Scope.enter();
            Frame train = makeTrainFrame(true);

            TargetEncoderModel teModel = trainTE(train, DataLeakageHandlingStrategy.KFold, false, false);
            Scope.track_generic(teModel);
            TargetEncoderTransformer teTrans = new TargetEncoderTransformer(teModel);
            Scope.track_generic(teTrans);

            Model model = buildModel(train, null, teTrans, CategoricalEncodingScheme.AUTO);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            try(ZipFile zf = new ZipFile(mojoFile)) { 
                ZipEntry preprocessor = zf.getEntry("transformers/transformer_0/model.ini");
                assertNotNull(preprocessor);
            }

            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._transformers);
            assertEquals(1, mojoModel._transformers.length);
            assertTrue(mojoModel._transformers[0] instanceof TargetEncoderMojoModel);
        } finally {
            Scope.exit();
        }
    }
    
    
    @Test
    public void test_model_mojo_predictions_consistency_with_TE_encoding_all_categoricals() throws Exception {
        try {
            Scope.enter();
            Frame train = makeTrainFrame(true);
            Frame valid = makeValidFrame();

            TargetEncoderModel teModel = trainTE(train, DataLeakageHandlingStrategy.KFold, true, false);
            Scope.track_generic(teModel);
            TargetEncoderTransformer teTrans = new TargetEncoderTransformer(teModel);
            Scope.track_generic(teTrans);

            Model model = buildModel(train, valid, teTrans, CategoricalEncodingScheme.AUTO);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._transformers);
            assertEquals(1, mojoModel._transformers.length);
            assertTrue(mojoModel._transformers[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                MultinomialModelPrediction mojoPredictions = modelWrapper.predictMultinomial(asRowData(row));
                comparePredictions(predictions, mojoPredictions);
            }
        } finally {
            Scope.exit();
        }

    }
    
    @Test
    public void test_model_mojo_predictions_consistency_with_TE_KFold_encoding_only_some_categoricals_rest_is_enum_encoded() throws Exception {
        try {
            Scope.enter();
            Frame train = makeTrainFrame(true);

            TargetEncoderModel teModel = trainTE(train, DataLeakageHandlingStrategy.KFold, false, false);
            Scope.track_generic(teModel);
            TargetEncoderTransformer teTrans = new TargetEncoderTransformer(teModel);
            Scope.track_generic(teTrans);

            Model model = buildModel(train, null, teTrans, CategoricalEncodingScheme.Enum);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._transformers);
            assertEquals(1, mojoModel._transformers.length);
            assertTrue(mojoModel._transformers[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                MultinomialModelPrediction mojoPredictions = modelWrapper.predictMultinomial(asRowData(row));
                comparePredictions(predictions, mojoPredictions);
            }
        } finally {
            Scope.exit();
        }
    }

    @Test @Ignore("PUBDEV-7775")
    public void test_model_mojo_predictions_consistency_with_TE_KFold_encoding_only_some_categoricals_rest_is_one_hot_encoded() throws Exception {
        try {
            Scope.enter();
            Frame train = makeTrainFrame(true);

            TargetEncoderModel teModel = trainTE(train, DataLeakageHandlingStrategy.KFold, false, false);
            Scope.track_generic(teModel);
            TargetEncoderTransformer teTrans = new TargetEncoderTransformer(teModel);
            Scope.track_generic(teTrans);

            Model model = buildModel(train, null, teTrans, CategoricalEncodingScheme.OneHotExplicit);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._transformers);
            assertEquals(1, mojoModel._transformers.length);
            assertTrue(mojoModel._transformers[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                MultinomialModelPrediction mojoPredictions = modelWrapper.predictMultinomial(asRowData(row));
                comparePredictions(predictions, mojoPredictions);
            }
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void test_model_mojo_predictions_consistency_with_TE_KFold_not_removing_original_columns_then_enum_encoded() throws Exception {
        try {
            Scope.enter();
            Frame train = makeTrainFrame(true);

            TargetEncoderModel teModel = trainTE(train, DataLeakageHandlingStrategy.KFold, false, true);
            Scope.track_generic(teModel);
            TargetEncoderTransformer teTrans = new TargetEncoderTransformer(teModel);
            Scope.track_generic(teTrans);

            Model model = buildModel(train, null, teTrans, CategoricalEncodingScheme.Enum);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._transformers);
            assertEquals(1, mojoModel._transformers.length);
            assertTrue(mojoModel._transformers[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                MultinomialModelPrediction mojoPredictions = modelWrapper.predictMultinomial(asRowData(row));
                comparePredictions(predictions, mojoPredictions);
            }
        } finally {
            Scope.exit();
        }
    }

    @Test @Ignore("PUBDEV-7775")
    public void test_model_mojo_predictions_consistency_with_TE_Kfold_not_removing_original_columns_then_one_hot_encoded() throws Exception {
        try {
            Scope.enter();
            Frame train = makeTrainFrame(true);

            TargetEncoderModel teModel = trainTE(train, DataLeakageHandlingStrategy.KFold, false, true);
            Scope.track_generic(teModel);
            TargetEncoderTransformer teTrans = new TargetEncoderTransformer(teModel);
            Scope.track_generic(teTrans);

            Model model = buildModel(train, null, teTrans, CategoricalEncodingScheme.OneHotExplicit);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._transformers);
            assertEquals(1, mojoModel._transformers.length);
            assertTrue(mojoModel._transformers[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                MultinomialModelPrediction mojoPredictions = modelWrapper.predictMultinomial(asRowData(row));
                comparePredictions(predictions, mojoPredictions);
            }
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void test_model_mojo_predictions_consistency_with_TE_None_encoding_only_some_categoricals_rest_is_enum_encoded() throws Exception {
        try {
            Scope.enter();
            Frame train = makeTrainFrame(false);

            TargetEncoderModel teModel = trainTE(train, DataLeakageHandlingStrategy.None, false, false);
            Scope.track_generic(teModel);
            TargetEncoderTransformer teTrans = new TargetEncoderTransformer(teModel);
            Scope.track_generic(teTrans);

            Model model = buildModel(train, null, teTrans, CategoricalEncodingScheme.Enum);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._transformers);
            assertEquals(1, mojoModel._transformers.length);
            assertTrue(mojoModel._transformers[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                MultinomialModelPrediction mojoPredictions = modelWrapper.predictMultinomial(asRowData(row));
                comparePredictions(predictions, mojoPredictions);
            }
        } finally {
            Scope.exit();
        }
    }

    @Test @Ignore("PUBDEV-7775")
    public void test_model_mojo_predictions_consistency_with_TE_None_encoding_only_some_categoricals_rest_is_one_hot_encoded() throws Exception {
        try {
            Scope.enter();
            Frame train = makeTrainFrame(false);

            TargetEncoderModel teModel = trainTE(train, DataLeakageHandlingStrategy.None, false, false);
            Scope.track_generic(teModel);
            TargetEncoderTransformer teTrans = new TargetEncoderTransformer(teModel);
            Scope.track_generic(teTrans);

            Model model = buildModel(train, null, teTrans, CategoricalEncodingScheme.OneHotExplicit);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._transformers);
            assertEquals(1, mojoModel._transformers.length);
            assertTrue(mojoModel._transformers[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                MultinomialModelPrediction mojoPredictions = modelWrapper.predictMultinomial(asRowData(row));
                comparePredictions(predictions, mojoPredictions);
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void test_model_mojo_predictions_consistency_with_TE_None_not_removing_original_columns_then_enum_encoded() throws Exception {
        try {
            Scope.enter();
            Frame train = makeTrainFrame(false);

            TargetEncoderModel teModel = trainTE(train, DataLeakageHandlingStrategy.None, false, true);
            Scope.track_generic(teModel);
            TargetEncoderTransformer teTrans = new TargetEncoderTransformer(teModel);
            Scope.track_generic(teTrans);

            Model model = buildModel(train, null, teTrans, CategoricalEncodingScheme.Enum);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._transformers);
            assertEquals(1, mojoModel._transformers.length);
            assertTrue(mojoModel._transformers[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                MultinomialModelPrediction mojoPredictions = modelWrapper.predictMultinomial(asRowData(row));
                comparePredictions(predictions, mojoPredictions);
            }
        } finally {
            Scope.exit();
        }
    }

    @Test @Ignore("PUBDEV-7775")
    public void test_model_mojo_predictions_consistency_with_TE_None_not_removing_original_columns_then_one_hot_encoded() throws Exception {
        try {
            Scope.enter();
            Frame train = makeTrainFrame(false);

            TargetEncoderModel teModel = trainTE(train, DataLeakageHandlingStrategy.None, false, true);
            Scope.track_generic(teModel);
            TargetEncoderTransformer teTrans = new TargetEncoderTransformer(teModel);
            Scope.track_generic(teTrans);

            Model model = buildModel(train, null, teTrans, CategoricalEncodingScheme.OneHotExplicit);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._transformers);
            assertEquals(1, mojoModel._transformers.length);
            assertTrue(mojoModel._transformers[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                MultinomialModelPrediction mojoPredictions = modelWrapper.predictMultinomial(asRowData(row));
                comparePredictions(predictions, mojoPredictions);
            }
        } finally {
            Scope.exit();
        }
    }


    private Frame makeTrainFrame(boolean withFoldColumn) {
        TestFrameBuilder builder = new TestFrameBuilder()
                .withName("trainFrame")
                .withColNames(withFoldColumn
                        ? new String[] {NUMERICAL[0], TO_ENCODE[0], NOT_ENCODED[0], TO_ENCODE[1], NUMERICAL[1], TARGET, FOLDC}
                        : new String[] {NUMERICAL[0], TO_ENCODE[0], NOT_ENCODED[0], TO_ENCODE[1], NUMERICAL[1], TARGET})
                .withVecTypes(withFoldColumn
                        ? new byte[] {Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT, Vec.T_NUM}
                        : new byte[] {Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT})
                .withChunkLayout(6, 6)
                .withDataForCol(0, ar(3, 3, 3, 2, 2, 2, 1, 1, 1, 0, 0, 0))
                .withDataForCol(1, ar("a", "b", "c", "a", "a", "b", "b", "c", "c", "a", "b", "c"))
                .withDataForCol(2, ar("a", "b", "c", "a", "a", "b", "b", "c", "c", "a", "b", "c"))
                .withDataForCol(3, ar("c", "b", "a", "c", "c", "b", "b", "a", "a", "c", "b", "a"))
                .withDataForCol(4, ard(0.3, 0.3, 0.3, 0.2, 0.2, 0.2, 0.1, 0.1, 0.1, 0.0, 0.0, 0.0))
                .withDataForCol(5, ar("N", "Y", "N", "M", "Y", "Y", "N", "M", "N", "Y", "Y", "M")); // M like maybe :)
        if (withFoldColumn)
            builder.withDataForCol(6, ar(1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3));
        return builder.build();
    }

    private Frame makeValidFrame() {
        return new TestFrameBuilder()
                .withName("validFrame")
                .withColNames(NUMERICAL[0], TO_ENCODE[0], NOT_ENCODED[0], TO_ENCODE[1], NUMERICAL[1], TARGET)
                .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_CAT)
                .withDataForCol(0, ar(0, 1, 2, 3, 4, 3, 2, 1, 0))
                .withDataForCol(1, ar("a", "b", "c", "d", "c", "b", "a", "b", "c"))
                .withDataForCol(2, ar("a", "b", "c", "b", "c", "a", "b", "c", "b"))
                .withDataForCol(3, ar("c", "b", "a", "b", "c", "d", "c", "b", "a"))
                .withDataForCol(4, ard(0.0, 0.1, 0.2, 0.3, 0.2, 0.1, 0.0, 0.1, 0.2))
                .withDataForCol(5, ar("N", "Y", "M", "N", "N", "Y", "Y", "M", "N")) 
                .build();
    }
    
    private Map<String, ?> makeRow(int seed) {
        Random rnd = new Random(seed);
        Map<String, Object> row = new HashMap();
        row.put(NUMERICAL[0], (double)rnd.nextInt(5));
        row.put(TO_ENCODE[0], new String[] { "a", "b", "c", "d", "e" }[rnd.nextInt(5)]);
        row.put(NOT_ENCODED[0], new String[] { "a", "b", "c", "d", "e" }[rnd.nextInt(5)]);
        row.put(TO_ENCODE[1], new String[] { "a", "b", "c", "d", "e" }[rnd.nextInt(5)]);
        row.put(NUMERICAL[1], (double)rnd.nextInt(5)/10);
        return row;
    }

    private TargetEncoderModel trainTE(Frame train, DataLeakageHandlingStrategy strategy, boolean encodeAll, boolean keepOriginalCategoricalPredictors) {
        TargetEncoderParameters params = new TargetEncoderParameters();
        params._keep_original_categorical_columns= keepOriginalCategoricalPredictors;
        params._train = train._key;
        params._response_column = TARGET;
        params._fold_column = ArrayUtils.contains(train.names(), FOLDC) ? FOLDC : null;
//        params._ignored_columns = encodeAll ? null : ignoredColumns(train, TO_ENCODE, TARGET, FOLDC);
        params._columns_to_encode = ArrayUtils.append(
                Arrays.stream(TO_ENCODE).map(col -> new String[] {col}).toArray(String[][]::new),
                new String[][] {TO_ENCODE}  // adding a grouping
        );
        params._data_leakage_handling = strategy;
        params._noise = 0;
        params._seed = 42;

        TargetEncoder te = new TargetEncoder(params);
        return te.trainModel().get();
    }

    private Model buildModel(Frame train, Frame valid, TargetEncoderTransformer preprocessor, CategoricalEncodingScheme categoricalEncoding) {
        GBMModel.GBMParameters params = new GBMModel.GBMParameters();
        params._seed = 987;
        params._train = train._key;
        params._valid = valid == null ? null : valid._key;
        params._response_column = TARGET;
        params._dataTransformers = preprocessor == null ? null : new Key[] {preprocessor._key};
        params._min_rows = 1;
        params._max_depth = 1;
        params._categorical_encoding = categoricalEncoding;

        if (ArrayUtils.contains(train.names(), FOLDC)) {
            params._fold_column = FOLDC;
            params._keep_cross_validation_models = true;
            params._keep_cross_validation_predictions = true;
        }

        GBM gbm = new GBM(params);
        GBMModel model = gbm.trainModel().get();
        return model;
    }

    private void comparePredictions(Frame inMemoryPredictions, MultinomialModelPrediction mojoPredictions) {
        assertEquals(inMemoryPredictions.numCols(), mojoPredictions.classProbabilities.length+1);
        assertEquals(inMemoryPredictions.vec("predict").at(0), mojoPredictions.labelIndex, 1e-8);
        assertEquals(inMemoryPredictions.vec(1).at(0), mojoPredictions.classProbabilities[0], 1e-8);
        assertEquals(inMemoryPredictions.vec(2).at(0), mojoPredictions.classProbabilities[1], 1e-8);
        assertEquals(inMemoryPredictions.vec(3).at(0), mojoPredictions.classProbabilities[2], 1e-8);
    }

    private RowData asRowData(Map<String,?> data) {
        RowData row = new RowData();
        row.putAll(data);
        return row;
    }

    private Frame asFrame(Map<String,?> data) {
        String[] columns = data.keySet().toArray(new String[0]);
        int[] types = Stream.of(columns)
                .mapToInt(c -> data.get(c) instanceof Number ? Vec.T_NUM : Vec.T_CAT)
                .toArray();

        TestFrameBuilder builder = new TestFrameBuilder()
                .withColNames(columns)
                .withVecTypes(ArrayUtils.toByteArray(types));
        for (int i=0; i<columns.length; i++) {
            Object v = data.get(columns[i]);
            if (v instanceof Number) {
                builder.withDataForCol(i, new double[] {((Number)v).doubleValue()});
            } else {
                builder.withDataForCol(i, new String[] {(String)v});
            }
        }
        return builder.build();
    }
    
    
    @Test @Ignore
    public void test_pubdev_7775() throws Exception {
        try {
            Scope.enter();
            Frame train = makeTrainFrame(true); //without the fold column, the test pass: reordering issue

            Model model = buildModel(train, null, null, CategoricalEncodingScheme.OneHotExplicit);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                MultinomialModelPrediction mojoPredictions = modelWrapper.predictMultinomial(asRowData(row));
                comparePredictions(predictions, mojoPredictions);
            }
        } finally {
            Scope.exit();
        }
    }

}
