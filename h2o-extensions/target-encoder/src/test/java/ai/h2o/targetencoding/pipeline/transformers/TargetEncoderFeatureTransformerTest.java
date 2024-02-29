package ai.h2o.targetencoding.pipeline.transformers;

import ai.h2o.targetencoding.TargetEncoderModel.DataLeakageHandlingStrategy;
import ai.h2o.targetencoding.TargetEncoderModel.TargetEncoderParameters;
import hex.Model;
import hex.Model.Parameters.CategoricalEncodingScheme;
import hex.ModelBuilder;
import hex.genmodel.MojoModel;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import hex.pipeline.Pipeline;
import hex.pipeline.PipelineModel;
import hex.tree.gbm.GBMModel;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.Scope;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static water.TestUtil.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncoderFeatureTransformerTest {
    
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
            
            TargetEncoderParameters teParams = makeTEParams(train, DataLeakageHandlingStrategy.KFold, false, false);
            
            PipelineModel pModel = buildPipeline(train, null, teParams, CategoricalEncodingScheme.AUTO);
            Scope.track_generic(pModel);
            
            int expectedCVModels = 3;  //3 folds -> 3 cv models
            assertEquals(expectedCVModels, pModel._output._cross_validation_models.length);
            //assertions on the pipeline model (build on a clean frame)
            assertFalse(ArrayUtils.contains(pModel._output._names, ENCODED));
            assertTrue(ArrayUtils.contains(pModel._output._names, TO_ENCODE));
            assertTrue(ArrayUtils.contains(pModel._output._names, NOT_ENCODED));
            // assertions on the estimator model (build on an encoded frame)
            Model eModel = pModel._output.getEstimatorModel();
            assertTrue(ArrayUtils.contains(eModel._output._names, ENCODED));
            assertFalse(ArrayUtils.contains(eModel._output._names, TO_ENCODE));
            assertTrue(ArrayUtils.contains(eModel._output._names, NOT_ENCODED));
          
            Frame preds = pModel.score(valid);
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

            TargetEncoderParameters teParams = makeTEParams(train, DataLeakageHandlingStrategy.None, false, false);

            PipelineModel pModel = buildPipeline(train, valid, teParams, CategoricalEncodingScheme.AUTO);
            Scope.track_generic(pModel);

            //assertions on the pipeline model (build on a clean frame)
            assertFalse(ArrayUtils.contains(pModel._output._names, ENCODED));
            assertTrue(ArrayUtils.contains(pModel._output._names, TO_ENCODE));
            assertTrue(ArrayUtils.contains(pModel._output._names, NOT_ENCODED));
            // assertions on the estimator model (build on an encoded frame)
            Model eModel = pModel._output.getEstimatorModel();
            assertTrue(ArrayUtils.contains(eModel._output._names, ENCODED));
            assertFalse(ArrayUtils.contains(eModel._output._names, TO_ENCODE));
            assertTrue(ArrayUtils.contains(eModel._output._names, NOT_ENCODED));

            Frame preds = pModel.score(valid);
            Scope.track(preds);
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

    private TargetEncoderParameters makeTEParams(Frame train, DataLeakageHandlingStrategy strategy, boolean encodeAll, boolean keepOriginalCategoricalPredictors) {
        TargetEncoderParameters params = new TargetEncoderParameters();
        params._keep_original_categorical_columns = keepOriginalCategoricalPredictors;
        params._train = train._key;
        params._response_column = TARGET;
        params._fold_column = ArrayUtils.contains(train.names(), FOLDC) ? FOLDC : null;
        params._ignored_columns = encodeAll ? null : ignoredColumns(train, TO_ENCODE, TARGET, FOLDC);
        params._data_leakage_handling = strategy;
        params._noise = 0;
        params._seed = 42;
        return params;
    }

    private PipelineModel buildPipeline(Frame train, Frame valid, TargetEncoderParameters teParams, CategoricalEncodingScheme categoricalEncoding) {
        GBMModel.GBMParameters eparams = new GBMModel.GBMParameters();
        eparams._min_rows = 1;
        eparams._max_depth = 1;
        eparams._categorical_encoding = categoricalEncoding;

        if (ArrayUtils.contains(train.names(), FOLDC)) {
            eparams._keep_cross_validation_models = true;
            eparams._keep_cross_validation_predictions = true;
        }

        PipelineModel.PipelineParameters pparams = new PipelineModel.PipelineParameters();
        TargetEncoderFeatureTransformer teTrans = new TargetEncoderFeatureTransformer(teParams).init();
        pparams.setTransformers(teTrans);
        pparams._estimatorParams = eparams;
        pparams._seed = 987;
        pparams._train = train._key;
        pparams._valid = valid == null ? null : valid._key;
        pparams._response_column = TARGET;
        pparams._fold_column = ArrayUtils.contains(train.names(), FOLDC) ? FOLDC : null;
        Pipeline pipeline = ModelBuilder.make(pparams);
        PipelineModel pmodel = Scope.track_generic(pipeline.trainModel().get());
        return pmodel;
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

            Model model = buildPipeline(train, null, null, CategoricalEncodingScheme.OneHotExplicit);
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
