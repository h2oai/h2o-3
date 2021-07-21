package water.api.schemas3;

import hex.ModelMetricsRegressionCoxPHGeneric;
import water.api.API;

public class ModelMetricsRegressionCoxPHGenericV3
        extends ModelMetricsRegressionV3<ModelMetricsRegressionCoxPHGeneric, ModelMetricsRegressionCoxPHGenericV3> {

    @API(help="Concordance metric (c-index)", direction=API.Direction.OUTPUT)
    public double _concordance;

    @API(help="Number of concordant pairs", direction=API.Direction.OUTPUT)
    public long _concordant;

    @API(help="Number of discordant pairs.", direction=API.Direction.OUTPUT)
    public long _discordant;

    @API(help="Number of tied pairs", direction=API.Direction.OUTPUT)
    public long _tied_y;

}
