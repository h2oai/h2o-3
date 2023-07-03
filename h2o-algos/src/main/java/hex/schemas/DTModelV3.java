package hex.schemas;

import hex.tree.dt.DTModel;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class DTModelV3 extends ModelSchemaV3<DTModel,
        DTModelV3,
        DTModel.DTParameters,
        DTV3.DTParametersV3,
        DTModel.DTOutput,
        DTModelV3.DTModelOutputV3> {

    public static final class DTModelOutputV3 extends ModelOutputSchemaV3<DTModel.DTOutput, DTModelOutputV3> {
        // nothing
    }

    public DTV3.DTParametersV3 createParametersSchema() {
        return new DTV3.DTParametersV3();
    }

    public DTModelOutputV3 createOutputSchema() {
        return new DTModelOutputV3();
    }

    //==========================
    // Custom adapters go here

    // Version&Schema-specific filling into the impl
    @Override
    public DTModel createImpl() {
        DTV3.DTParametersV3 p = this.parameters;
        DTModel.DTParameters parms = p.createImpl();
        return new DTModel(model_id.key(), parms, new DTModel.DTOutput(null));
    }
}
