package hex.tree.uplift;

import hex.*;
import hex.tree.CompressedForest;
import hex.tree.SharedTreeModel;
import hex.tree.SharedTreeModelWithContributions;
import hex.tree.SharedTreePojoWriter;
import hex.util.EffectiveParametersUtils;
import water.Key;

public class UpliftDRFModel extends SharedTreeModel<UpliftDRFModel, UpliftDRFModel.UpliftDRFParameters, UpliftDRFModel.UpliftDRFOutput> {

    public static class UpliftDRFParameters extends SharedTreeModel.SharedTreeParameters {
        public String algoName() { return "UpliftDRF"; }
        public String fullName() { return "Uplift Distributed Random Forest"; }
        public String javaName() { return UpliftDRFModel.class.getName(); }
        

        public enum UpliftMetricType { AUTO, KL, ChiSquared, Euclidean }
        public UpliftMetricType _uplift_metric = UpliftMetricType.AUTO;

        public int _mtries = -2; //number of columns to use per split. default depeonds on the algorithm and problem (classification/regression)

        public UpliftDRFParameters() {
            super();
            // Set Uplift DRF specific defaults (can differ from SharedTreeModel's defaults)
            _max_depth = 20;
            _min_rows = 1;
            _treatment_column = "treatment";
        }

        @Override
        public long progressUnits() {
            return _ntrees*2;
        }
    }

    public static class UpliftDRFOutput extends SharedTreeModelWithContributions.SharedTreeOutput {
        
        public double[] _defaultAuucThresholds; // thresholds for AUUC to calculate metrics
        
        public UpliftDRFOutput( UpliftDRF b) { super(b); }

        @Override
        public ModelCategory getModelCategory() {
            return ModelCategory.BinomialUplift;
        }

        @Override
        public boolean isBinomialClassifier() {
            return true;
        }

        public void setDefaultAuucThresholds(double[] defaultAuucThresholds) {
            this._defaultAuucThresholds = defaultAuucThresholds;
        }
    }

    public UpliftDRFModel(Key<UpliftDRFModel> selfKey, UpliftDRFParameters parms, UpliftDRFOutput output ) {
        super(selfKey, parms, output);
    }

    @Override
    public void initActualParamValues() {
        super.initActualParamValues();
        EffectiveParametersUtils.initHistogramType(_parms);
        EffectiveParametersUtils.initCategoricalEncoding(_parms, Parameters.CategoricalEncodingScheme.Enum);
        EffectiveParametersUtils.initUpliftMetric(_parms);
    }

    @Override public boolean binomialOpt() { return false; }
    
    /** Bulk scoring API for one row.  Chunks are all compatible with the model,
     *  and expect the last Chunks are for the final distribution and prediction.
     *  Default method is to just load the data into the tmp array, then call
     *  subclass scoring logic. */
    @Override protected double[] score0(double[] data, double[] preds, double offset, int ntrees) {
        super.score0(data, preds, offset, ntrees);
        int N = _output._ntrees;
        preds[1] /= N;
        preds[2] /= N;
        preds[0] = preds[1] - preds[2];
        return preds;
    }

    @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
        return new ModelMetricsBinomialUplift.MetricBuilderBinomialUplift(domain, _output._defaultAuucThresholds);
    }

    @Override
    public UpliftDrfMojoWriter getMojo() {
        return new UpliftDrfMojoWriter(this);
    }

    @Override
    protected SharedTreePojoWriter makeTreePojoWriter() {
        CompressedForest compressedForest = new CompressedForest(_output._treeKeys, _output._domains);
        CompressedForest.LocalCompressedForest localCompressedForest = compressedForest.fetch();
        return new UpliftDrfPojoWriter(this, localCompressedForest._trees);
    }
}
