package hex.knn;

import hex.*;
import water.DKV;
import water.H2O;
import water.Key;
import water.Scope;
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
        //public KNNDistance _distance;
        public DistanceType _distance;
        public boolean _compute_metrics;

        @Override
        public long progressUnits() {
            return 0;
        }
    }

    public static class KNNOutput extends Model.Output {

        public KNNOutput(KNN b) {
            super(b);
        }
        Key<Frame> _distances_key;
        
        @Override
        public ModelCategory getModelCategory() {
            if (nclasses() > 2) {
                return ModelCategory.Multinomial;
            } else {
                return ModelCategory.Binomial;
            }
        }

        public void setDistancesKey(Key<Frame> _distances_key) {
            this._distances_key = _distances_key;
        }

        public Frame getDistances(){
            return DKV.get(_distances_key).get();
        }

        public Key<Frame> getDistancesKey() {
            return _distances_key;
        }
    }

    public KNNModel(Key<KNNModel> selfKey, KNNModel.KNNParameters parms, KNNModel.KNNOutput output) {
        super(selfKey, parms, output);
    }

    @Override
    public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        switch(_output.getModelCategory()) {
            case Binomial:
                return new ModelMetricsBinomial.MetricBuilderBinomial(domain);
            case Multinomial:
                return new ModelMetricsMultinomial.MetricBuilderMultinomial(_output.nclasses(), domain, _parms._auc_type);
            default: throw H2O.unimpl("Invalid ModelCategory " + _output.getModelCategory());
        }
    }

    @Override
    protected double[] score0(double[] data, double[] preds) {
        Frame train = _parms._train.get(); 
        int idIndex = train.find(_parms._id_column);
        int responseIndex = train.find(_parms._response_column);
        byte idType = train.types()[idIndex];
        preds = new KNNScoringTask(data, _parms._k, _output.nclasses(), KNNDistanceFactory.createDistance(_parms._distance), idIndex, idType, 
                                    responseIndex).doAll(train).score();
        Scope.untrack(train);
        return preds;
    }
}
