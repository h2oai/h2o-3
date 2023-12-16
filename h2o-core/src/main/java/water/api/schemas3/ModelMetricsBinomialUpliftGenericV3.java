package water.api.schemas3;

import hex.ModelMetricsBinomialUpliftGeneric;

public class ModelMetricsBinomialUpliftGenericV3<I extends ModelMetricsBinomialUpliftGeneric, S extends ModelMetricsBinomialUpliftGenericV3<I, S>>
        extends ModelMetricsBinomialUpliftV3<I, S> {

    @Override
    public S fillFromImpl(ModelMetricsBinomialUpliftGeneric modelMetrics) {
        super.fillFromImpl(modelMetrics);
        this.AUUC = modelMetrics._auuc.auuc();
        this.auuc_normalized = modelMetrics._auuc.auucNormalized();
        this.ate = modelMetrics.ate();
        this.att = modelMetrics.att();
        this.atc = modelMetrics.atc();
        this.qini = modelMetrics.qini();
        
        if (modelMetrics._auuc_table != null) { // Possibly overwrites whatever has been set in the ModelMetricsBinomialV3
            this.auuc_table = new TwoDimTableV3().fillFromImpl(modelMetrics._auuc_table);
        }
        if (modelMetrics._aecu_table != null) { // Possibly overwrites whatever has been set in the ModelMetricsBinomialV3
            this.aecu_table = new TwoDimTableV3().fillFromImpl(modelMetrics._aecu_table);
        }
        if (modelMetrics._thresholds_and_metric_scores != null) { // Possibly overwrites whatever has been set in the ModelMetricsBinomialV3
            this.thresholds_and_metric_scores = new TwoDimTableV3().fillFromImpl(modelMetrics._thresholds_and_metric_scores);
        }
        return (S) this;
    }
}
