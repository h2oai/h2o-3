package hex.schemas;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import water.AutoBuffer;
import water.H2O;
import water.Job;
import water.Key;
import water.api.*;
import water.api.ValidationMessageBase;
import water.util.*;

import java.lang.reflect.Constructor;
import java.util.Properties;

public class ModelBuilderSchema<B extends ModelBuilder, S extends ModelBuilderSchema<B,S,P>, P extends ModelParametersSchema> extends RequestSchema<B,S> implements SpecifiesHttpResponseCode {
  // NOTE: currently ModelBuilderSchema has its own JSON serializer.
  // If you add more fields here you MUST add them to writeJSON_impl() below.

  public static class IcedHashMapStringModelBuilderSchema extends IcedSortedHashMap<String, ModelBuilderSchema> {}

  // Input fields
  @API(help="Model builder parameters.")
  public P parameters;

  // Output fields
  @API(help="The algo name for this ModelBuilder.", direction=API.Direction.OUTPUT)
  public String algo;

  @API(help="The pretty algo name for this ModelBuilder (e.g., Generalized Linear Model, rather than GLM).", direction=API.Direction.OUTPUT)
  public String algo_full_name;

  @API(help="Model categories this ModelBuilder can build.", values={ "Unknown", "Binomial", "Multinomial", "Regression", "Clustering", "AutoEncoder", "DimReduction" }, direction = API.Direction.OUTPUT)
  public ModelCategory[] can_build;

  @API(help="Should the builder always be visible, be marked as beta, or only visible if the user starts up with the experimental flag?", values = { "Experimental", "Beta", "AlwaysVisible" }, direction = API.Direction.OUTPUT)
  public ModelBuilder.BuilderVisibility visibility;

  @API(help = "Job Key", direction = API.Direction.OUTPUT)
  public JobV3 job;

  @API(help="Parameter validation messages", direction=API.Direction.OUTPUT)
  public ValidationMessageBase messages[];

  @API(help="Count of parameter validation errors", direction=API.Direction.OUTPUT)
  public int error_count;

  @API(help="HTTP status to return for this build.", json = false)
  public int __http_status; // The handler sets this to 400 if we're building and error_count > 0, else 200.

  public ModelBuilderSchema() {
    this.parameters = createParametersSchema();
  }

  public void setHttpStatus(int status) {
    __http_status = status;
  }

  public int httpStatus() {
    return __http_status;
  }

  /** Factory method to create the model-specific parameters schema. */
  final public P createParametersSchema() {
    P impl = null;

    // special case, because ModelBuilderSchema is the top of the tree and is parameterized differently
    if (ModelBuilderSchema.class == this.getClass()) {
      return (P)new ModelParametersSchema();
    }

    try {
      Class<? extends ModelParametersSchema> parameters_class = (Class<? extends ModelParametersSchema>) ReflectionUtils.findActualClassParameter(this.getClass(), 2);
      impl = (P)parameters_class.newInstance();
    }
    catch (Exception e) {
      throw H2O.fail("Caught exception trying to instantiate a builder instance for ModelBuilderSchema: " + this + ": " + e, e);
    }
    return impl;
  }

  public S fillFromParms(Properties parms) {
    this.parameters.fillFromParms(parms);
    return (S)this;
  }

  /** Create the corresponding impl object, as well as its parameters object. */
  @Override final public B createImpl() {
    B impl = null;

      try {
        Class<? extends ModelBuilder> builder_class = (Class<? extends ModelBuilder>) ReflectionUtils.findActualClassParameter(this.getClass(), 0);
        Class<? extends Model.Parameters> parameters_class = (Class<? extends Model.Parameters>) this.parameters.getImplClass();

        // NOTE: we want the parameters to be empty except for the destination_key, so that the builder gets created with any passed-in key name.
        // We then wipe out the impl parameter's destination_key, so we get the correct default.
        Model.Parameters _parameters = null;

        if (null != parameters) {
          _parameters = (Model.Parameters) parameters.createImpl();
          if (null != parameters.model_id)
            _parameters._model_id = Key.make(parameters.model_id.name);
        }
        Constructor builder_constructor = builder_class.getConstructor(new Class[]{parameters_class});
        impl = (B) builder_constructor.newInstance(_parameters);
        impl.clearInitState(); // clear out validation errors from default parameters
        impl._parms._model_id = null;
      } catch (Exception e) {
        throw H2O.fail("Caught exception trying to instantiate a builder instance for ModelBuilderSchema: " + this + ": " + e, e);
      }
    return impl;
  }

  @Override public B fillImpl(B impl) {
    super.fillImpl(impl);
    parameters.fillImpl(impl._parms);
    impl.init(false);
    return impl;
  }

  // Generic filling from the impl
  @Override public S fillFromImpl(B builder) {
    // DO NOT, because it can already be running: builder.init(false); // check params

    this.algo = builder.getAlgo();
    this.algo_full_name = ModelBuilder.getAlgoFullName(this.algo);

    this.can_build = builder.can_build();
    this.visibility = builder.builderVisibility();
    job = (JobV3)Schema.schema(this.getSchemaVersion(), Job.class).fillFromImpl(builder);
    this.messages = new ValidationMessageBase[builder._messages.length];
    int i = 0;
    for( ModelBuilder.ValidationMessage vm : builder._messages ) {
      this.messages[i++] = new ValidationMessageV3().fillFromImpl(vm); // TODO: version // Note: does default field_name mapping
    }
    // default fieldname hacks
    ValidationMessageBase.mapValidationMessageFieldNames(this.messages, new String[]{"_train", "_valid"}, new String[]{"training_frame", "validation_frame"});
    this.error_count = builder.error_count();
    parameters = createParametersSchema();
    parameters.fillFromImpl(builder._parms);
    // parameters.destination_key = new KeyV1.ModelKeyV1(builder._dest);
    return (S)this;
  }

  @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
    ab.title(this.getClass().getSimpleName()+" Started");
    String url = JobV3.link(job.key.key());
    return ab.href("Poll",url,url);
  }

  // TODO: Drop this writeJSON_impl and use the default one.
  // TODO: Pull out the help text & metadata into the ParameterSchema for the front-end to display.
  @Override
  public AutoBuffer writeJSON_impl( AutoBuffer ab ) {
    ab.put1(','); // the schema and version fields get written before we get called
    ab.putJSON("job", job);
    ab.put1(',');
    ab.putJSONStr("algo", algo);
    ab.put1(',');
    ab.putJSONStr("algo_full_name", algo_full_name);
    ab.put1(',');
    ab.putJSONAEnum("can_build", can_build);
    ab.put1(',');
    ab.putJSONEnum("visibility", visibility);
    ab.put1(',');
    ab.putJSONA("messages", messages);
    ab.put1(',');
    ab.putJSON4("error_count", error_count);
    ab.put1(',');

    // Builds ModelParameterSchemaV2 objects for each field, and then calls writeJSON on the array
    ModelParametersSchema.writeParametersJSON(ab, parameters, createParametersSchema().fillFromImpl((Model.Parameters)parameters.createImpl()));
    return ab;
  }

}
