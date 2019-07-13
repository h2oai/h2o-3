package hex.schemas;

import hex.generic.GenericModel;
import hex.generic.GenericModelOutput;
import hex.generic.GenericModelParameters;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class GenericModelV3 extends ModelSchemaV3<GenericModel, GenericModelV3, GenericModelParameters, GenericV3.GenericParametersV3, GenericModelOutput, GenericModelV3.GenericModelOutputV3> {

    @Override
    public GenericV3.GenericParametersV3 createParametersSchema() {
        return new GenericV3.GenericParametersV3();
    }

    @Override
    public GenericModelOutputV3 createOutputSchema() {
        return new GenericModelOutputV3();
    }

    public static final class GenericModelOutputV3 extends ModelOutputSchemaV3<GenericModelOutput, GenericModelOutputV3>{
        
    }
}
