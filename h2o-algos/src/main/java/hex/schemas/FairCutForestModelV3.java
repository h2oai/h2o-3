package hex.schemas;

import hex.tree.isoforfaircut.FairCutForestModel;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class FairCutForestModelV3 extends ModelSchemaV3<FairCutForestModel,
        FairCutForestModelV3,
        FairCutForestModel.FairCutForestParameters,
        FairCutForestV3.FairCutForestParametersV3,
        FairCutForestModel.FairCutForestOutput,
        FairCutForestModelV3.FairCutForestModelOutputV3> {

    public static final class FairCutForestModelOutputV3 extends ModelOutputSchemaV3<FairCutForestModel.FairCutForestOutput, FairCutForestModelOutputV3> {
        // nothing
    }

    public FairCutForestV3.FairCutForestParametersV3 createParametersSchema() { return new FairCutForestV3.FairCutForestParametersV3(); }
    public FairCutForestModelOutputV3 createOutputSchema() { return new FairCutForestModelOutputV3(); }

    //==========================
    // Custom adapters go here

    // Version&Schema-specific filling into the impl
    @Override public FairCutForestModel createImpl() {
        FairCutForestV3.FairCutForestParametersV3 p = this.parameters;
        FairCutForestModel.FairCutForestParameters parms = p.createImpl();
        return new FairCutForestModel( model_id.key(), parms, new FairCutForestModel.FairCutForestOutput(null) );
    }
}
