package water;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelMetrics;
import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
import org.junit.Assert;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.ComparisonUtils;
import water.util.fp.Function;
import water.util.fp.Function2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public abstract class MetricTest extends TestUtil {
    static class CalculateMetricsViaIndependentBuilderTask extends MRTask<CalculateMetricsViaIndependentBuilderTask> {

        private ModelMetrics.IndependentMetricBuilder _metricBuilder;
        private byte[] _mojoData;
        private ModelMetrics _resultMetrics = null;
        private int _nPredictions;
        private int _nActual;
        private boolean _isOffsetCol;
        private boolean _isWeightCol;

        public ModelMetrics getMetrics(){
            return _resultMetrics;
        }

        CalculateMetricsViaIndependentBuilderTask(
                byte[] mojoData,
                int nPredictions,
                int nActual,
                boolean isOffsetCol,
                boolean isWeightCol) {
            _mojoData = mojoData;
            _nPredictions = nPredictions;
            _nActual = nActual;
            _isOffsetCol = isOffsetCol;
            _isWeightCol = isWeightCol;
        }

        private MojoReaderBackend getReaderBackend(byte[] mojoData) throws IOException {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(mojoData);
            return MojoReaderBackendFactory.createReaderBackend(inputStream, MojoReaderBackendFactory.CachingStrategy.MEMORY);

        }
        
        @Override
        protected void setupLocal() {
            try {
                MojoModel mojoModel = ModelMojoReader.readFrom(getReaderBackend(_mojoData), true);
                _metricBuilder = (ModelMetrics.IndependentMetricBuilder)ModelMojoReader.readMetricBuilder(mojoModel, getReaderBackend(_mojoData));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void map( Chunk[] chunks) {
            super.map(chunks);
            for(int rowId = 0; rowId < chunks[0].len(); rowId++) {
                int colId = 0;
                double[] predictions = new double[_nPredictions];
                for (int i = 0; i < _nPredictions; i++) {
                    predictions[i] = chunks[colId].atd(rowId);
                    colId++;
                }
                double[] actualValues = new double[_nActual];
                for (int i = 0; i < _nActual; i++) {
                    actualValues[i] = chunks[colId].atd(rowId);
                    colId++;
                }
                double offset;
                if (_isOffsetCol) {
                    offset = chunks[colId].atd(rowId);
                    colId++;
                } else {
                    offset = 0.0d;
                }
                double weight;
                if (_isWeightCol) {
                    weight = chunks[colId].atd(rowId);
                    colId++;
                } else {
                    weight = 1.0d;
                }
                _metricBuilder.perRow(predictions, actualValues, weight, offset);
            }
        }

        @Override
        public void reduce(final CalculateMetricsViaIndependentBuilderTask other) {
            super.reduce(other);
            _metricBuilder.reduce(other._metricBuilder);
        }

        @Override
        protected void postGlobal() {
            super.postGlobal();
            _metricBuilder.postGlobal();
            _resultMetrics = _metricBuilder.makeModelMetrics();
        }
    }

    protected Frame dropNA(Frame frame, int filteredColumn) {
        MRTask task = new MRTask() {
            @Override
            public void map(Chunk[] cs, NewChunk[] nc) {
                for (int rowId = 0;  rowId < cs[filteredColumn].len(); rowId++) {
                    if(!cs[filteredColumn].isNA(rowId)) {
                        for(int colId = 0;  colId < cs.length; colId++) {
                            nc[colId].addNum(cs[colId].atd(rowId));
                        }
                    }
                }
            }
        };
        task.doAll(frame.types(), frame);
        Frame outputFrame  = task.outputFrame(Key.make(), frame.names(), frame.domains());
        DKV.put(outputFrame);
        return outputFrame;
    }
    
    protected void addRandomColumn(Frame frame, String columnName) {
        Frame random = TestUtil.generateRealOnly(1, (int) frame.numRows(), 0, 42);
        random.setNames(new String[]{columnName});
        DKV.put(random);
        Scope.track(random);
        frame.add(random);
    }
    
 

    protected ModelMetrics calculateMetricsViaIndependentBuilder(
            Model model, 
            Frame frame, 
            Function2<Frame, Model, Vec[]> actualVectorsGetter) {

        Frame predictions = Scope.track(model.score(frame));
        Vec[] predictionVectors = predictions.vecs();
        Vec[] actualVectors = actualVectorsGetter.apply(frame, model);
        Vec[] offsetCol = model._parms._offset_column == null ? new Vec[0] : new Vec[] { frame.vec(model._parms._offset_column) };
        Vec[] weightCol = model._parms._weights_column == null ? new Vec[0] : new Vec[] { frame.vec(model._parms._weights_column) };
        
        int vecSize = actualVectors.length + predictionVectors.length + offsetCol.length + weightCol.length;
        Vec[] vectorsForCalculation = Arrays.copyOf(predictionVectors, vecSize);
        System.arraycopy(actualVectors, 0, vectorsForCalculation, predictionVectors.length, actualVectors.length);
        System.arraycopy(offsetCol, 0, vectorsForCalculation, predictionVectors.length + actualVectors.length, offsetCol.length);
        System.arraycopy(weightCol, 0, vectorsForCalculation, predictionVectors.length + actualVectors.length + offsetCol.length, weightCol.length);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        model.getMojo().writeTo(outputStream);
        byte[] mojoData = outputStream.toByteArray();
        
        CalculateMetricsViaIndependentBuilderTask calculationTask = new CalculateMetricsViaIndependentBuilderTask(
            mojoData,
            predictionVectors.length,
            actualVectors.length,
            offsetCol.length > 0,
            weightCol.length > 0);
        calculationTask.doAll(vectorsForCalculation);
        ModelMetrics modelMetrics = calculationTask.getMetrics();
        return modelMetrics;
    }

    protected void testIndependentlyCalculatedMetrics(
            Model model,
            Function2<Frame, Model, Vec[]> actualVectorsGetter,
            Frame trainingFrame,
            Frame validationFrame,
            double tolerance) {
        if (trainingFrame != null) {
            ModelMetrics expectedTrainingMetrics = model._output._training_metrics;
            ModelMetrics trainingMetrics = calculateMetricsViaIndependentBuilder(model, trainingFrame, actualVectorsGetter);
            Assert.assertNotNull(trainingMetrics);
            Assert.assertTrue(
                    "Unexpected type of training metrics",
                    expectedTrainingMetrics.getClass() == trainingMetrics.getClass());
            ComparisonUtils.MetricComparator trainingMetricComparator = new ComparisonUtils.RelativeToleranceMetricComparator(tolerance);
            expectedTrainingMetrics.isEqualUpToTolerance(trainingMetricComparator, trainingMetrics);
            Assert.assertTrue(
                    "Training metrics are not equal upto proportional tolerance " + tolerance + ". " + trainingMetricComparator,
                    trainingMetricComparator.isEqual());
        }

        if (validationFrame != null) {
            ModelMetrics expectedValidationMetrics = model._output._validation_metrics;
            ModelMetrics validationMetrics = calculateMetricsViaIndependentBuilder(model, validationFrame, actualVectorsGetter);
            Assert.assertNotNull(validationFrame);
            Assert.assertTrue(
                    "Unexpected type of validation metrics",
                    expectedValidationMetrics.getClass() == validationMetrics.getClass());
            ComparisonUtils.MetricComparator validationMetricComparator = new ComparisonUtils.RelativeToleranceMetricComparator(tolerance);
            expectedValidationMetrics.isEqualUpToTolerance(validationMetricComparator, validationMetrics);
            Assert.assertTrue(
                    "Validation metrics are not equal upto proportional tolerance "+ tolerance + ". " + validationMetricComparator,
                    validationMetricComparator.isEqual());
        }
    }

    protected void testIndependentlyCalculatedMetrics(
            Frame dataset,
            Model.Parameters parameters,
            Function<Model.Parameters, ModelBuilder> algorithmConstructor,
            Function2<Frame, Model, Vec[]> actualVectorsGetter,
            double tolerance,
            boolean ignoreTrainingMetrics) {
        Scope.enter();
        try {
            Frames frames = split(dataset, 0.2);
            Frame train = Scope.track(frames.train);
            Frame valid = Scope.track(frames.test);

            parameters._train = train._key;
            parameters._valid = valid._key;
            parameters._seed = 42L;

            ModelBuilder modelBuilder = algorithmConstructor.apply(parameters);
            Model model = (Model)Scope.track_generic(modelBuilder.trainModel().get());
            Frame trainForMetrics = ignoreTrainingMetrics ? null : train;
            testIndependentlyCalculatedMetrics(model, actualVectorsGetter, trainForMetrics, valid, tolerance);
        } finally {
            Scope.exit();
        }
    }

    protected void testIndependentlyCalculatedSupervisedMetrics(
            Frame dataset,
            Model.Parameters parameters,
            Function<Model.Parameters, ModelBuilder> algorithmConstructor,
            double tolerance) {
        testIndependentlyCalculatedSupervisedMetrics(dataset, parameters, algorithmConstructor, tolerance, false);
    }

    protected void testIndependentlyCalculatedSupervisedMetrics(
            Frame dataset,
            Model.Parameters parameters,
            Function<Model.Parameters, ModelBuilder> algorithmConstructor, 
            double tolerance,
            boolean ignoreTrainingMetrics) {
        Function2<Frame, Model, Vec[]> actualVectorsGetter = (frame, model) -> new Vec[]{frame.vec(parameters._response_column)};
        testIndependentlyCalculatedMetrics(dataset, parameters, algorithmConstructor, actualVectorsGetter, tolerance, ignoreTrainingMetrics);
    }
    
}
