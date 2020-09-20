package ai.h2o.targetencoding;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import hex.Model;
import hex.Model.Parameters.CategoricalEncodingScheme;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.targetencoder.TargetEncoderMojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
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
import water.util.RandomUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static water.TestUtil.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncoderPreprocessorTest {
    
    private static String TO_ENCODE = "categorical";
    private static String ENCODED = "categorical_te";
    private static String NOT_ENCODED = "noTE";
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
//            teModel = spy(teModel);
            TargetEncoderPreprocessor tePreproc = new TargetEncoderPreprocessor(teModel);
            Scope.track_generic(tePreproc);
            
            Model model = buildModel(train, null, tePreproc, CategoricalEncodingScheme.AUTO);
            Scope.track_generic(model);
            
            int expectedCVModels = 3;  //3 folds -> 3 cv models
            assertEquals(expectedCVModels, model._output._cross_validation_models.length);
            assertTrue(ArrayUtils.contains(model._output._names, ENCODED));
            assertFalse(ArrayUtils.contains(model._output._names, TO_ENCODE));
            assertTrue(ArrayUtils.contains(model._output._names, NOT_ENCODED));
            
//            verify(teModel, times(expectedCVModels)).transformTraining(any(), anyInt());
//            verify(teModel, times(1)).transformTraining(any(), 2); //the last CV model
//            verify(teModel, times(expectedCVModels+1)).transformTraining(any()); //main model+3 valid
//            verify(teModel, times(1)).transformTraining(train); //main model
            
            Frame preds = model.score(valid);
            Scope.track(preds);
//            verify(teModel, times(1)).transform(valid);
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
            TargetEncoderPreprocessor tePreproc = new TargetEncoderPreprocessor(teModel);
            Scope.track_generic(tePreproc);

            Model model = buildModel(train, valid, tePreproc, CategoricalEncodingScheme.AUTO);
            Scope.track_generic(model);

            assertTrue(ArrayUtils.contains(model._output._names, ENCODED));
            assertFalse(ArrayUtils.contains(model._output._names, TO_ENCODE));
            assertTrue(ArrayUtils.contains(model._output._names, NOT_ENCODED));

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
            TargetEncoderPreprocessor tePreproc = new TargetEncoderPreprocessor(teModel);
            Scope.track_generic(tePreproc);

            Model model = buildModel(train, null, tePreproc, CategoricalEncodingScheme.AUTO);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            try(ZipFile zf = new ZipFile(mojoFile)) { 
                ZipEntry preprocessor = zf.getEntry("preprocessing/preprocessor_0/model.ini");
                assertNotNull(preprocessor);
            }

            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._preprocessors);
            assertEquals(1, mojoModel._preprocessors.length);
            assertTrue(mojoModel._preprocessors[0] instanceof TargetEncoderMojoModel);
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
            TargetEncoderPreprocessor tePreproc = new TargetEncoderPreprocessor(teModel);
            Scope.track_generic(tePreproc);

            Model model = buildModel(train, valid, tePreproc, CategoricalEncodingScheme.AUTO);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._preprocessors);
            assertEquals(1, mojoModel._preprocessors.length);
            assertTrue(mojoModel._preprocessors[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                BinomialModelPrediction mojoPredictions = modelWrapper.predictBinomial(asRowData(row));
                assertEquals(predictions.numCols(), mojoPredictions.classProbabilities.length+1);
                assertEquals(predictions.vec("predict").at(0), mojoPredictions.labelIndex, 1e-8);
                assertEquals(predictions.vec(1).at(0), mojoPredictions.classProbabilities[0], 1e-8);
                assertEquals(predictions.vec(2).at(0), mojoPredictions.classProbabilities[1], 1e-8);
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
            TargetEncoderPreprocessor tePreproc = new TargetEncoderPreprocessor(teModel);
            Scope.track_generic(tePreproc);

            Model model = buildModel(train, null, tePreproc, CategoricalEncodingScheme.Enum);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._preprocessors);
            assertEquals(1, mojoModel._preprocessors.length);
            assertTrue(mojoModel._preprocessors[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                BinomialModelPrediction mojoPredictions = modelWrapper.predictBinomial(asRowData(row));
                assertEquals(predictions.numCols(), mojoPredictions.classProbabilities.length+1);
                assertEquals(predictions.vec("predict").at(0), mojoPredictions.labelIndex, 1e-8);
                assertEquals(predictions.vec(1).at(0), mojoPredictions.classProbabilities[0], 1e-8);
                assertEquals(predictions.vec(2).at(0), mojoPredictions.classProbabilities[1], 1e-8);
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
            TargetEncoderPreprocessor tePreproc = new TargetEncoderPreprocessor(teModel);
            Scope.track_generic(tePreproc);

            Model model = buildModel(train, null, tePreproc, CategoricalEncodingScheme.OneHotExplicit);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._preprocessors);
            assertEquals(1, mojoModel._preprocessors.length);
            assertTrue(mojoModel._preprocessors[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                BinomialModelPrediction mojoPredictions = modelWrapper.predictBinomial(asRowData(row));
                assertEquals(predictions.numCols(), mojoPredictions.classProbabilities.length+1);
                assertEquals(predictions.vec("predict").at(0), mojoPredictions.labelIndex, 1e-8);
                assertEquals(predictions.vec(1).at(0), mojoPredictions.classProbabilities[0], 1e-8);
                assertEquals(predictions.vec(2).at(0), mojoPredictions.classProbabilities[1], 1e-8);
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
            TargetEncoderPreprocessor tePreproc = new TargetEncoderPreprocessor(teModel);
            Scope.track_generic(tePreproc);

            Model model = buildModel(train, null, tePreproc, CategoricalEncodingScheme.Enum);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._preprocessors);
            assertEquals(1, mojoModel._preprocessors.length);
            assertTrue(mojoModel._preprocessors[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                BinomialModelPrediction mojoPredictions = modelWrapper.predictBinomial(asRowData(row));
                assertEquals(predictions.numCols(), mojoPredictions.classProbabilities.length+1);
                assertEquals(predictions.vec("predict").at(0), mojoPredictions.labelIndex, 1e-8);
                assertEquals(predictions.vec(1).at(0), mojoPredictions.classProbabilities[0], 1e-8);
                assertEquals(predictions.vec(2).at(0), mojoPredictions.classProbabilities[1], 1e-8);
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
            TargetEncoderPreprocessor tePreproc = new TargetEncoderPreprocessor(teModel);
            Scope.track_generic(tePreproc);

            Model model = buildModel(train, null, tePreproc, CategoricalEncodingScheme.OneHotExplicit);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._preprocessors);
            assertEquals(1, mojoModel._preprocessors.length);
            assertTrue(mojoModel._preprocessors[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                BinomialModelPrediction mojoPredictions = modelWrapper.predictBinomial(asRowData(row));
                assertEquals(predictions.numCols(), mojoPredictions.classProbabilities.length+1);
                assertEquals(predictions.vec("predict").at(0), mojoPredictions.labelIndex, 1e-8);
                assertEquals(predictions.vec(1).at(0), mojoPredictions.classProbabilities[0], 1e-8);
                assertEquals(predictions.vec(2).at(0), mojoPredictions.classProbabilities[1], 1e-8);
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
            TargetEncoderPreprocessor tePreproc = new TargetEncoderPreprocessor(teModel);
            Scope.track_generic(tePreproc);

            Model model = buildModel(train, null, tePreproc, CategoricalEncodingScheme.Enum);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._preprocessors);
            assertEquals(1, mojoModel._preprocessors.length);
            assertTrue(mojoModel._preprocessors[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                BinomialModelPrediction mojoPredictions = modelWrapper.predictBinomial(asRowData(row));
                assertEquals(predictions.numCols(), mojoPredictions.classProbabilities.length+1);
                assertEquals(predictions.vec("predict").at(0), mojoPredictions.labelIndex, 1e-8);
                assertEquals(predictions.vec(1).at(0), mojoPredictions.classProbabilities[0], 1e-8);
                assertEquals(predictions.vec(2).at(0), mojoPredictions.classProbabilities[1], 1e-8);
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
            TargetEncoderPreprocessor tePreproc = new TargetEncoderPreprocessor(teModel);
            Scope.track_generic(tePreproc);

            Model model = buildModel(train, null, tePreproc, CategoricalEncodingScheme.OneHotExplicit);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._preprocessors);
            assertEquals(1, mojoModel._preprocessors.length);
            assertTrue(mojoModel._preprocessors[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                BinomialModelPrediction mojoPredictions = modelWrapper.predictBinomial(asRowData(row));
                assertEquals(predictions.numCols(), mojoPredictions.classProbabilities.length+1);
                assertEquals(predictions.vec("predict").at(0), mojoPredictions.labelIndex, 1e-8);
                assertEquals(predictions.vec(1).at(0), mojoPredictions.classProbabilities[0], 1e-8);
                assertEquals(predictions.vec(2).at(0), mojoPredictions.classProbabilities[1], 1e-8);
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
            TargetEncoderPreprocessor tePreproc = new TargetEncoderPreprocessor(teModel);
            Scope.track_generic(tePreproc);

            Model model = buildModel(train, null, tePreproc, CategoricalEncodingScheme.Enum);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._preprocessors);
            assertEquals(1, mojoModel._preprocessors.length);
            assertTrue(mojoModel._preprocessors[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                BinomialModelPrediction mojoPredictions = modelWrapper.predictBinomial(asRowData(row));
                assertEquals(predictions.numCols(), mojoPredictions.classProbabilities.length+1);
                assertEquals(predictions.vec("predict").at(0), mojoPredictions.labelIndex, 1e-8);
                assertEquals(predictions.vec(1).at(0), mojoPredictions.classProbabilities[0], 1e-8);
                assertEquals(predictions.vec(2).at(0), mojoPredictions.classProbabilities[1], 1e-8);
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
            TargetEncoderPreprocessor tePreproc = new TargetEncoderPreprocessor(teModel);
            Scope.track_generic(tePreproc);

            Model model = buildModel(train, null, tePreproc, CategoricalEncodingScheme.OneHotExplicit);
            Scope.track_generic(model);

            File mojoFile = folder.newFile(model._key+".zip");
            try (FileOutputStream modelOutput = new FileOutputStream(mojoFile)) {
                model.getMojo().writeTo(modelOutput);
            }
            MojoModel mojoModel = MojoModel.load(mojoFile.getPath());
            assertNotNull(mojoModel._preprocessors);
            assertEquals(1, mojoModel._preprocessors.length);
            assertTrue(mojoModel._preprocessors[0] instanceof TargetEncoderMojoModel);

            for(int i=0; i<50; i++) {
                Map<String, ?> row = makeRow(i);
                System.out.println(row);
                Frame predictions = Scope.track(model.score(Scope.track(asFrame(row))));

                EasyPredictModelWrapper modelWrapper = new EasyPredictModelWrapper(new EasyPredictModelWrapper.Config()
                        .setConvertUnknownCategoricalLevelsToNa(true)
                        .setModel(mojoModel));
                BinomialModelPrediction mojoPredictions = modelWrapper.predictBinomial(asRowData(row));
                assertEquals(predictions.numCols(), mojoPredictions.classProbabilities.length+1);
                assertEquals(predictions.vec("predict").at(0), mojoPredictions.labelIndex, 1e-8);
                assertEquals(predictions.vec(1).at(0), mojoPredictions.classProbabilities[0], 1e-8);
                assertEquals(predictions.vec(2).at(0), mojoPredictions.classProbabilities[1], 1e-8);
            }
        } finally {
            Scope.exit();
        }
    }


    private Frame makeTrainFrame(boolean withFoldColumn) {
        TestFrameBuilder builder = new TestFrameBuilder()
                .withName("trainFrame")
                .withColNames(withFoldColumn
                        ? new String[] {"numerical", TO_ENCODE, NOT_ENCODED, TARGET, "foldc"}
                        : new String[] {"numerical", TO_ENCODE, NOT_ENCODED, TARGET})
                .withVecTypes(withFoldColumn
                        ? new byte[] {Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM}
                        : new byte[] {Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_CAT})
                .withChunkLayout(6, 6)
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
                .withColNames("numerical", TO_ENCODE, NOT_ENCODED, TARGET)
                .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
                .withDataForCol(0, ar(0, 1, 2, 3, 2, 1, 0))
                .withDataForCol(1, ar("a", "b", "c", "b", "a", "b", "c"))
                .withDataForCol(2, ar("a", "b", "c", "b", "a", "b", "c"))
                .withDataForCol(3, ar("N", "Y", "Y", "N", "N", "Y", "Y"))
                .build();
    }
    
    private Map<String, ?> makeRow(int seed) {
        Random rnd = new Random(seed);
        Map<String, Object> row = new HashMap();
        row.put("numerical", (double)rnd.nextInt(5));
        row.put(TO_ENCODE, new String[] { "a", "b", "c", "d" }[rnd.nextInt(4)]);
        row.put(NOT_ENCODED, new String[] { "a", "b", "c", "d" }[rnd.nextInt(4)]);
        return row;
    }

    private TargetEncoderModel trainTE(Frame train, DataLeakageHandlingStrategy strategy, boolean encodeAll, boolean keepOriginalCategoricalPredictors) {
        TargetEncoderParameters params = new TargetEncoderParameters();
        params._keep_original_categorical_columns = keepOriginalCategoricalPredictors;
        params._train = train._key;
        params._response_column = TARGET;
        params._fold_column = ArrayUtils.contains(train.names(), FOLDC) ? FOLDC : null;
        params._ignored_columns = encodeAll ? null : ignoredColumns(train, TO_ENCODE, TARGET, FOLDC);
        params._data_leakage_handling = strategy;
        params._noise = 0;
        params._seed = 42;

        TargetEncoder te = new TargetEncoder(params);
        return te.trainModel().get();
    }

    private Model buildModel(Frame train, Frame valid, TargetEncoderPreprocessor preprocessor, CategoricalEncodingScheme categoricalEncoding) {
        GBMModel.GBMParameters params = new GBMModel.GBMParameters();
        params._seed = 987;
        params._train = train._key;
        params._valid = valid == null ? null : valid._key;
        params._response_column = TARGET;
        params._preprocessors = preprocessor == null ? null : new Key[] {preprocessor._key};
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
                BinomialModelPrediction mojoPredictions = modelWrapper.predictBinomial(asRowData(row));
                assertEquals(predictions.numCols(), mojoPredictions.classProbabilities.length+1);
                assertEquals(predictions.vec("predict").at(0), mojoPredictions.labelIndex, 1e-8);
                assertEquals(predictions.vec(1).at(0), mojoPredictions.classProbabilities[0], 1e-8);
                assertEquals(predictions.vec(2).at(0), mojoPredictions.classProbabilities[1], 1e-8);
            }
        } finally {
            Scope.exit();
        }
    }

}
