package hex.schemas;

import hex.Model;
import hex.ModelBuilder;
import water.AutoBuffer;
import water.H2O;
import water.Job;
import water.Key;
import water.api.*;
import water.api.ModelParametersSchema.ValidationMessageBase;
import water.util.DocGen;
import water.util.Log;
import water.util.ReflectionUtils;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public abstract class ModelBuilderSchema<B extends ModelBuilder, S extends ModelBuilderSchema<B,S,P>, P extends ModelParametersSchema> extends Schema<B,S> implements SpecifiesHttpResponseCode {
  // NOTE: currently ModelBuilderSchema has its own JSON serializer.
  // If you add more fields here you MUST add them to writeJSON_impl() below.

  // Input fields
  @API(help="Model builder parameters.")
  public P parameters;

  // Output fields
  @API(help="Model categories this ModelBuilder can build.", direction = API.Direction.OUTPUT)
  public Model.ModelCategory[] can_build;

  @API(help = "Job Key", direction = API.Direction.OUTPUT)
  JobV2 job;

  @API(help="Parameter validation messages", direction=API.Direction.OUTPUT)
  public ValidationMessageBase validation_messages[];

  @API(help="Count of parameter validation errors", direction=API.Direction.OUTPUT)
  public int validation_error_count;

  private int __http_status; // The handler sets this to 400 if we're building and validation_error_count > 0, else 200.

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
          if (null != parameters.destination_key)
            _parameters._destination_key = Key.make(parameters.destination_key.name);
        }
        Constructor builder_constructor = builder_class.getConstructor(new Class[]{parameters_class});
        impl = (B) builder_constructor.newInstance(_parameters);
        impl.clearInitState(); // clear out validation errors from default parameters
        impl._parms._destination_key = null;
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
    builder.init(false); // check params
    this.can_build = builder.can_build();
    job = (JobV2)Schema.schema(this.getSchemaVersion(), Job.class).fillFromImpl(builder);
    this.validation_messages = new ValidationMessageBase[builder._messages.length];
    int i = 0;
    for( ModelBuilder.ValidationMessage vm : builder._messages ) {
      this.validation_messages[i++] = new ModelParametersSchema.ValidationMessageV2().fillFromImpl(vm); // TODO: version // Note: does default field_name mapping
    }
    // default fieldname hacks
    mapValidationMessageFieldNames(new String[] {"train", "valid"}, new String[] {"training_frame", "validation_frame"});
    this.validation_error_count = builder.error_count();
    parameters = createParametersSchema();
    parameters.fillFromImpl(builder._parms);
    return (S)this;
  }

  /**
   * Map impl field names in the validation messages to schema field names,
   * called <i>after</i> behavior of stripping leading _ characters.
   */
  protected void mapValidationMessageFieldNames(String[] from, String[] to) {
    if (null == from && null == to)
      return;
    if (null == from || null == to)
      throw new IllegalArgumentException("Bad parameter name translation arrays; one is null and the other isn't.");
    Map<String, String> translations = new HashMap();
    for (int i = 0; i < from.length; i++) {
      translations.put(from[i], to[i]);
    }

    for( ValidationMessageBase vm : this.validation_messages) {
      if (null == vm) {
        Log.err("Null ValidationMessageBase for ModelBuilderSchema: " + this);
        continue;
      }

      if (null == vm.field_name) {
        Log.err("Null field_name: " + vm);
        continue;
      }
      if (translations.containsKey(vm.field_name))
        vm.field_name = translations.get(vm.field_name);
    }
  }

  @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
    ab.title(this.getClass().getSimpleName()+" Started");
    String url = JobV2.link(job.key.key());
    return ab.href("Poll",url,url);
  }

  // TODO: Drop this writeJSON_impl and use the default one.
  // TODO: Pull out the help text & metadata into the ParameterSchema for the front-end to display.
  @Override
  public AutoBuffer writeJSON_impl( AutoBuffer ab ) {
    ab.put1(','); // the schema and version fields get written before we get called
    ab.putJSON("job", job);
    ab.put1(',');
    ab.putJSONAEnum("can_build", can_build);
    ab.put1(',');
    ab.putJSONA("validation_messages", validation_messages);
    ab.put1(',');
    ab.putJSON4("validation_error_count", validation_error_count);
    ab.put1(',');

    // Builds ModelParameterSchemaV2 objects for each field, and then calls writeJSON on the array
    ModelParametersSchema.writeParametersJSON(ab, parameters, createParametersSchema().fillFromImpl((Model.Parameters)parameters.createImpl()));
    return ab;
  }

}
