package hex.schemas;

import hex.ModelBuilder;
import water.Key;
import water.api.API;
import water.api.JobV2;
import water.api.ModelParametersSchema;
import water.api.Schema;
import water.util.DocGen;

import java.util.Properties;

abstract public class ModelBuilderSchema<B extends ModelBuilder, S extends Schema<B,S>, P extends ModelParametersSchema> extends Schema<B,S> {
  // Input fields
  @API(help="Model builder parameters.")
  P parameters;

  // Output fields
  @API(help = "Job Key")
  Key job;

  /** Factory method to create the model-specific parameters schema. */
  abstract public P createParametersSchema();

  public S fillFromParms(Properties parms) {
    this.parameters = createParametersSchema();
    this.parameters.fillFromParms(parms);
    return (S)this;
  }

  // Generic filling from the impl
  @Override public S fillFromImpl(B builder) {
    job = builder._key;

    parameters = createParametersSchema();
    parameters.fillFromImpl(builder._parms);
    return (S)this;
  }

  @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
    ab.title("Model Building Started");
    String url = JobV2.link(job);
    return ab.href("Poll",url,url);
  }

}
