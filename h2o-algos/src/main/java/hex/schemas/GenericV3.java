package hex.schemas;

import hex.generic.Generic;
import hex.generic.GenericModelParameters;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelParameterSchemaV3;
import water.api.schemas3.ModelParametersSchemaV3;

public class GenericV3 extends ModelBuilderSchema<Generic, GenericV3, GenericV3.GenericParametersV3> {

    public static final class GenericParametersV3 extends ModelParametersSchemaV3<GenericModelParameters, GenericParametersV3> {
        public static final String[] fields = new String[]{
                "model_id",
                "model_key",
                "path"
        };

        @API(required = false, level = API.Level.critical, help = "Path to file with self-contained model archive.")
        public String path;

        @API(required = false, direction = API.Direction.INOUT, level = API.Level.critical, help = "Key to the self-contained model archive already uploaded to H2O.")
        public KeyV3.FrameKeyV3 model_key;
        
        public transient ModelParameterSchemaV3[] additionalParameters;

        @Override
        protected ModelParameterSchemaV3[] getAdditionalParameters() {
            return additionalParameters;
        }

        @Override
        protected GenericParametersV3 fillFromImpl(GenericModelParameters impl, String[] fieldsToSkip) {
            final GenericParametersV3 genericParametersV3 = super.fillFromImpl(impl, fieldsToSkip);
            genericParametersV3.additionalParameters = impl._modelParameters;
            
            return genericParametersV3;
        }

        @Override
        public GenericParametersV3 fillFromImpl(GenericModelParameters impl) {
            final GenericParametersV3 genericParametersV3 = super.fillFromImpl(impl);
            genericParametersV3.additionalParameters = impl._modelParameters;
            return genericParametersV3;
        }
    }
    
    
}
