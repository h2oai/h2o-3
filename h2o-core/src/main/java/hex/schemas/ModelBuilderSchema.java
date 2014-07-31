package hex.schemas;

import hex.ModelBuilder;
import water.AutoBuffer;
import water.H2O;
import water.Key;
import water.api.*;
import water.util.DocGen;

import java.lang.reflect.Field;
import java.util.Properties;

abstract public class ModelBuilderSchema<B extends ModelBuilder, S extends ModelBuilderSchema<B,S, P>, P extends ModelParametersSchema> extends Schema<B,S> {
  // Input fields
  @API(help="Model builder parameters.")
  P parameters;

  // Output fields
  @API(help = "Job Key")
  Key job;

  /** Factory method to create the model-specific parameters schema. */
  abstract public P createParametersSchema();
  abstract public B createImpl();

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
    ab.title("KMeans Started");
    String url = JobV2.link(job);
    return ab.href("Poll",url,url);
  }

  @Override
  public AutoBuffer writeJSON_impl( AutoBuffer ab ) {
    // Build ModelParameterSchemaV2 objects for each field, and the call writeJSON on the array
    String[] fields = parameters.fields();
    ModelParameterSchemaV2[] metadata = new ModelParameterSchemaV2[fields.length];

    String field_name = null;
    try {
      for (int i = 0; i < fields.length; i++) {
        field_name = fields[i];
        Field f = parameters.getClass().getField(field_name);

        // TODO: cache a default parameters schema
        ModelParameterSchemaV2 schema = new ModelParameterSchemaV2(parameters, createParametersSchema(), f);
        metadata[i] = schema;
      }
    } catch (NoSuchFieldException e) {
      throw H2O.fail("Caught exception accessing field: " + field_name + " for schema object: " + parameters + ": " + e.toString());
    }

    ab.putJSONA("parameters", metadata);
    return ab;
  }

}
