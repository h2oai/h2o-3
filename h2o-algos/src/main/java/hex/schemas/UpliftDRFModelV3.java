package hex.schemas;

import hex.tree.uplift.UpliftDRFModel;

public class UpliftDRFModelV3 extends SharedTreeModelV3<UpliftDRFModel,
            UpliftDRFModelV3,
            UpliftDRFModel.UpliftDRFParameters,
            UpliftDRFV3.UpliftDRFParametersV3,
            UpliftDRFModel.UpliftDRFOutput,
            UpliftDRFModelV3.UpliftDRFModelOutputV3> {

        public static final class UpliftDRFModelOutputV3 extends SharedTreeModelV3.SharedTreeModelOutputV3<UpliftDRFModel.UpliftDRFOutput, UpliftDRFModelOutputV3> {}

        public UpliftDRFV3.UpliftDRFParametersV3 createParametersSchema() { return new UpliftDRFV3.UpliftDRFParametersV3(); }
        public UpliftDRFModelOutputV3 createOutputSchema() { return new UpliftDRFModelOutputV3(); }

        //==========================
        // Custom adapters go here

        // Version&Schema-specific filling into the impl
        @Override public UpliftDRFModel createImpl() {
            UpliftDRFV3.UpliftDRFParametersV3 p = this.parameters;
            UpliftDRFModel.UpliftDRFParameters parms = p.createImpl();
            return new UpliftDRFModel( model_id.key(), parms, new UpliftDRFModel.UpliftDRFOutput(null) );
        }
}
