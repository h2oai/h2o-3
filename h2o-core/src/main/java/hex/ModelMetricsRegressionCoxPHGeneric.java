package hex;

import water.fvec.Frame;

public class ModelMetricsRegressionCoxPHGeneric extends ModelMetricsRegressionGeneric {

    public final double _concordance;
    public final long _concordant;
    public final long _discordant;
    public final long _tied_y;

    public ModelMetricsRegressionCoxPHGeneric(Model model, Frame frame, long nobs, double mse, double sigma, double mae, double rmsle, 
                                              double meanResidualDeviance, CustomMetric customMetric,
                                              double concordance, long concordant, long discordant, long tied_y,
                                              String description) {
        super(model, frame, nobs, mse, sigma, mae, rmsle, meanResidualDeviance, customMetric, description);
        _concordance = concordance;
        _concordant = concordant;
        _discordant = discordant;
        _tied_y = tied_y;
    }

}
