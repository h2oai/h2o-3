package hex.genmodel;

import com.google.gson.JsonObject;

public interface IMetricBuilderFactory<TMojoModel extends MojoModel> {
    IMetricBuilder createBuilder(TMojoModel mojoModel, JsonObject extraInfo);
}
