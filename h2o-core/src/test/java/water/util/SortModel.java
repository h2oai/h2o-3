package water.util;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import org.junit.Ignore;
import water.H2O;
import water.Key;

@Ignore
public class SortModel extends Model<SortModel, SortModel.SortParameters, SortModel.SortOutput> {

    public static class SortParameters extends Model.Parameters {
        public String algoName() {
            return "Sort";
        }

        public String fullName() {
            return "Sort";
        }

        public String javaName() {
            return SortModel.class.getName();
        }

        public int _nModelsInParallel = -1;
        
        @Override
        public long progressUnits() {
            return 1;
        }
    }

    public static class SortOutput extends Model.Output {

        public SortOutput(Sort b) {
            super(b);
        }

        @Override
        public ModelCategory getModelCategory() {
            return ModelCategory.Unknown;
        }
    }

    SortModel(Key<SortModel> selfKey, SortParameters parms, SortOutput output) {
        super(selfKey, parms, output);
    }

    @Override
    public ModelMetrics.MetricBuilder<?> makeMetricBuilder(String[] domain) {
        throw H2O.unimpl("No Model Metrics for ExampleModel.");
    }

    @Override
    protected double[] score0(double[] data/*ncols*/, double[] preds/*nclasses+1*/) {
        throw H2O.unimpl();
    }

}
