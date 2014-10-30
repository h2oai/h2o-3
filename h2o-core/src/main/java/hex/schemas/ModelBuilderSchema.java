package hex.schemas;

import hex.ModelBuilder;
import hex.ModelBuilder.ValidationMessage;
import water.AutoBuffer;
import water.Key;
import water.api.API;
import water.api.JobV2;
import water.api.ModelParametersSchema;
import water.api.ModelParametersSchema.ValidationMessageBase;
import water.api.ModelParametersSchema.ValidationMessageV2;
import water.api.Schema;
import water.util.DocGen;

import java.util.Properties;

public abstract class ModelBuilderSchema<B extends ModelBuilder, S extends ModelBuilderSchema<B,S,P>, P extends ModelParametersSchema> extends Schema<B,S> {
  // Input fields
  @API(help="Model builder parameters.")
  public P parameters;

  // Output fields
  @API(help = "Job Key", direction = API.Direction.OUTPUT)
  Key job;

  @API(help="Parameter validation messages", direction=API.Direction.OUTPUT)
  public ValidationMessageBase validation_messages[];

  @API(help="Count of parameter validation errors", direction=API.Direction.OUTPUT)
  public int validation_error_count;

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
    this.validation_messages = new ValidationMessageBase[builder._messages.length];
    int i = 0;
    for( ValidationMessage vm : builder._messages )
      this.validation_messages[i++] = new ValidationMessageV2().fillFromImpl(vm); // TODO: version
    this.validation_error_count = builder.error_count();
    parameters = createParametersSchema();
    parameters.fillFromImpl(builder._parms);
    return (S)this;
  }

  @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
    ab.title(this.getClass().getSimpleName()+" Started");
    String url = JobV2.link(job);
    return ab.href("Poll",url,url);
  }

  // TODO: Drop this writeJSON_impl and use the default one.
  // TODO: Pull out the help text & metadata into the ParameterSchema for the front-end to display.
  @Override
  public AutoBuffer writeJSON_impl( AutoBuffer ab ) {
    ab.putJSONStr("job", (null == job ? null : job.toString())); // TODO: is currently null, but probably should never be. . .
    ab.put1(',');
    ab.putJSONA("validation_messages", validation_messages);
    ab.put1(',');
    ab.putJSON4("validation_error_count", validation_error_count);
    ab.put1(',');

    // Builds ModelParameterSchemaV2 objects for each field, and then calls writeJSON on the array
    ModelParametersSchema.writeParametersJSON(ab, parameters, createParametersSchema());
    return ab;
  }

}
