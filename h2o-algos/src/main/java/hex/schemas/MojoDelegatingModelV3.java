package hex.schemas;

import hex.mojo.MojoDelegatingModel;
import hex.mojo.MojoDelegatingModelOutput;
import hex.mojo.MojoDelegatingModelParameters;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class MojoDelegatingModelV3 extends ModelSchemaV3<MojoDelegatingModel, MojoDelegatingModelV3, MojoDelegatingModelParameters, MojoDelegatingV3.MojoDelegatingParametersV3, MojoDelegatingModelOutput, MojoDelegatingModelV3.MojoDelegatingModelOutputV3> {

    @Override
    public MojoDelegatingV3.MojoDelegatingParametersV3 createParametersSchema() {
        return new MojoDelegatingV3.MojoDelegatingParametersV3();
    }

    @Override
    public MojoDelegatingModelOutputV3 createOutputSchema() {
        return new MojoDelegatingModelOutputV3();
    }

    public static final class MojoDelegatingModelOutputV3 extends ModelOutputSchemaV3<MojoDelegatingModelOutput, MojoDelegatingModelOutputV3>{
        
    }
}
