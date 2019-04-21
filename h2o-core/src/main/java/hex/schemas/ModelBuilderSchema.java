package hex.schemas;

import hex.Model;
import hex.ModelBuilder;
import hex.ModelCategory;
import water.AutoBuffer;
import water.H2O;
import water.api.API;
import water.api.SpecifiesHttpResponseCode;
import water.api.schemas3.*;
import water.util.IcedSortedHashMap;
import water.util.ReflectionUtils;

import java.util.Properties;

public class ModelBuilderSchema<B extends ModelBuilder, S extends ModelBuilderSchema<B,S,P>, P extends
    ModelParametersSchemaV3> extends RequestSchemaV3<B,S> implements SpecifiesHttpResponseCode {
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

  @API(help="Model categories this ModelBuilder can build.", values={ "Unknown", "Binomial", "Ordinal", "Multinomial", "Regression", "Clustering", "AutoEncoder", "DimReduction" }, direction = API.Direction.OUTPUT)
  public ModelCategory[] can_build;

  @API(help="Indicator whether the model is supervised or not.", direction=API.Direction.OUTPUT)
  public boolean supervised;

  @API(help="Should the builder always be visible, be marked as beta, or only visible if the user starts up with the experimental flag?", values = { "Experimental", "Beta", "AlwaysVisible" }, direction = API.Direction.OUTPUT)
  public ModelBuilder.BuilderVisibility visibility;

  @API(help = "Job Key", direction = API.Direction.OUTPUT)
  public JobV3 job;

  @API(help="Parameter validation messages", direction=API.Direction.OUTPUT)
  public ValidationMessageV3 messages[];

  @API(help="Count of parameter validation errors", direction=API.Direction.OUTPUT)
  public int error_count;

  @API(help="HTTP status to return for this build.", json = false, direction=API.Direction.OUTPUT)
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
    // special case, because ModelBuilderSchema is the top of the tree and is parameterized differently
    if (ModelBuilderSchema.class == this.getClass()) {
      return (P)new ModelParametersSchemaV3();
    }

    try {
      Class<? extends ModelParametersSchemaV3> parameters_class = ReflectionUtils.findActualClassParameter(this.getClass(), 2);
      return (P)parameters_class.newInstance();
    }
    catch (Exception e) {
      throw H2O.fail("Caught exception trying to instantiate a builder instance for ModelBuilderSchema: " + this + ": " + e, e);
    }
  }

  public S fillFromParms(Properties parms) {
    this.parameters.fillFromParms(parms);
    return (S)this;
  }

  /** Create the corresponding impl object, as well as its parameters object. */
  @Override final public B createImpl() {
    return ModelBuilder.make(getSchemaType(), null, null);
  }

  @Override public B fillImpl(B impl) {
    super.fillImpl(impl);
    parameters.fillImpl(impl._parms);
    impl.init(false); // validate parameters
    return impl;
  }

  // Generic filling from the impl
  @Override public S fillFromImpl(B builder) {
    // DO NOT, because it can already be running: builder.init(false); // check params

    this.algo = builder._parms.algoName().toLowerCase();
    this.algo_full_name = builder._parms.fullName();
    this.supervised = builder.isSupervised();

    this.can_build = builder.can_build();
    this.visibility = builder.builderVisibility();
    job = builder._job == null ? null : new JobV3(builder._job);
    // In general, you can ask about a builder in-progress, and the error
    // message list can be growing - so you have to be prepared to read it
    // racily.  Common for Grid searches exploring with broken parameter
    // choices.
    final ModelBuilder.ValidationMessage[] msgs = builder._messages; // Racily growing; read only once
    if( msgs != null ) {
      this.messages = new ValidationMessageV3[msgs.length];
      int i = 0;
      for (ModelBuilder.ValidationMessage vm : msgs) {
        if( vm != null ) this.messages[i++] = new ValidationMessageV3().fillFromImpl(vm); // TODO: version // Note: does default field_name mapping
      }
      // default fieldname hacks
      ValidationMessageV3.mapValidationMessageFieldNames(this.messages, new String[]{"_train", "_valid"}, new
          String[]{"training_frame", "validation_frame"});
    }
    this.error_count = builder.error_count();
    parameters = createParametersSchema();
    parameters.fillFromImpl(builder._parms);
    parameters.model_id = builder.dest() == null ? null : new KeyV3.ModelKeyV3(builder.dest());
    return (S)this;
  }

  // TODO: Drop this writeJSON_impl and use the default one.
  // TODO: Pull out the help text & metadata into the ParameterSchema for the front-end to display.
  public final AutoBuffer writeJSON_impl( AutoBuffer ab ) {
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
    ab.putJSONZ("supervised", supervised);
    ab.put1(',');
    ab.putJSONA("messages", messages);
    ab.put1(',');
    ab.putJSON4("error_count", error_count);
    ab.put1(',');

    // Builds ModelParameterSchemaV2 objects for each field, and then calls writeJSON on the array
    ModelParametersSchemaV3.writeParametersJSON(ab, parameters, createParametersSchema().fillFromImpl((Model.Parameters)parameters.createImpl()));
    return ab;
  }

}
