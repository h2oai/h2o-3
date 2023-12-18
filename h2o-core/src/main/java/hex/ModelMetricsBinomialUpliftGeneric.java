package hex;

import water.fvec.Frame;
import water.util.TwoDimTable;

public class ModelMetricsBinomialUpliftGeneric extends ModelMetricsBinomialUplift {

    
    public final TwoDimTable _thresholds_and_metric_scores;
    public final TwoDimTable _auuc_table;
    public final TwoDimTable _aecu_table;
    
    public ModelMetricsBinomialUpliftGeneric(Model model, Frame frame, long nobs, String[] domain, double ate, double att, double atc, double sigma, AUUC auuc, CustomMetric customMetric, TwoDimTable thresholds_and_metric_scores, TwoDimTable auuc_table, TwoDimTable aecu_table, final String description) {
        super(model, frame, nobs, domain, ate, att, atc, sigma, auuc, customMetric);
        _thresholds_and_metric_scores = thresholds_and_metric_scores;
        _auuc_table = auuc_table;
        _aecu_table = aecu_table;
        _description = description;
    }
}
