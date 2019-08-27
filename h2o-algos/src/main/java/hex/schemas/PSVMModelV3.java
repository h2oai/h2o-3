package hex.schemas;

import hex.psvm.PSVMModel;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.ModelOutputSchemaV3;
import water.api.schemas3.ModelSchemaV3;

public class PSVMModelV3 extends ModelSchemaV3<PSVMModel, PSVMModelV3, PSVMModel.PSVMParameters, PSVMV3.PSVMParametersV3, PSVMModel.PSVMModelOutput, PSVMModelV3.PSVMModelOutputV3> {

  public static final class PSVMModelOutputV3 extends ModelOutputSchemaV3<PSVMModel.PSVMModelOutput, PSVMModelOutputV3> {
    @API(help = "Total number of support vectors")
    public long svs_count;
    @API(help = "Number of bounded support vectors")
    public long bsv_count;
    @API(help = "rho")
    public double rho;
    @API(help = "Weights of support vectors")
    public KeyV3.FrameKeyV3 alpha_key;
  } // PSVMModelOutputV3

  //==========================
  // Custom adapters go here
  public PSVMV3.PSVMParametersV3 createParametersSchema() { return new PSVMV3.PSVMParametersV3(); }
  public PSVMModelOutputV3 createOutputSchema() { return new PSVMModelOutputV3(); }

  // Version&Schema-specific filling into the impl
  @Override public PSVMModel createImpl() {
    PSVMModel.PSVMParameters parms = parameters.createImpl();
    return new PSVMModel( model_id.key(), parms, null);
  }

}
