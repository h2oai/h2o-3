package ai.h2o.automl.dummy;

import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsBinomial;
import water.Key;

import java.util.Random;
import java.util.function.Function;

public class DummyModel extends Model<DummyModel, DummyModel.DummyModelParameters, DummyModel.DummyModelOutput>{

    private static Random RNG = new Random(1);

    public static class DummyModelParameters extends Model.Parameters {

        public transient Function<double[], double[]> _predict;
        public String[] _tags = new String[0];

        @Override
        public String algoName() {
            return "dummy";
        }

        @Override
        public String fullName() {
            return "Dummy";
        }

        @Override
        public String javaName() {
            return DummyModel.class.getName();
        }

        @Override
        public long progressUnits() {
            return 1;
        }
    }

    public static class DummyModelOutput extends Model.Output {
        boolean _supervised = true;

        @Override
        public boolean isSupervised() {
            return _supervised;
        }
    }


    public DummyModel(String key) {
        super(Key.make(key), new DummyModelParameters(), new DummyModelOutput());
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
    }

    @Override
    protected double[] score0(double[] data, double[] preds) {
        return _parms._predict == null ? preds : _parms._predict.apply(data);
    }
}
