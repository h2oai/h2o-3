package hex.knn;

import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import water.Key;
import water.fvec.Frame;

public class KNNModel extends Model<KNNModel, KNNModel.KNNParameters, KNNModel.KNNOutput> {

    public static class KNNParameters extends Model.Parameters {
        public String algoName() {
            return "KNN";
        }
        public String fullName() {
            return "K-nearest neighbors";
        }
        public String javaName() {
            return KNNModel.class.getName();
        }
        
        public int _k = 3;
        public KNNDistance _distance;

        @Override
        public long progressUnits() {
            return 0;
        }
    }

    public static class KNNOutput extends Model.Output {

        public KNNOutput(KNN b) {
            super(b);
        }
        Frame _distances;
        

        @Override
        public ModelCategory getModelCategory() {
            if (nclasses() > 2) {
                return ModelCategory.Multinomial;
            } else {
                return ModelCategory.Binomial;
            }
        }
    }

    public KNNModel(Key<KNNModel> selfKey, KNNModel.KNNParameters parms, KNNModel.KNNOutput output) {
        super(selfKey, parms, output);
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        return null;
    }

    @Override
    protected double[] score0(double[] data, double[] preds) {
        return new double[0];
    }
}
