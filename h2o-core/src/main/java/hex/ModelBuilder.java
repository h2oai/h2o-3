package hex;

import hex.schemas.ModelBuilderSchema;
import water.H2O;
import water.Job;
import water.Key;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 *  Model builder parent class.  Contains the common interfaces and fields across all model builders.
 */
abstract public class ModelBuilder<M extends Model<M,P,O>, P extends Model.Parameters, O extends Model.Output> extends Job<M> {
  /** All the parameters required to build the model. */
  public P _parms;

  // TODO: tighten up the type
  private static final Map<String, Class<? extends ModelBuilder>> _builders = new HashMap<>();

  public static final Map<String, Class<? extends ModelBuilder>>getModelBuilders() { return _builders; }

  public static void registerModelBuilder(String name, Class<? extends ModelBuilder> clz) {
    _builders.put(name, clz);
  }

  public static Class<? extends ModelBuilder> getModelBuilder(String name) {
    return _builders.get(name);
  }

  public static String getModelBuilderName(Class<? extends ModelBuilder> clz) {
    if (! _builders.containsValue(clz))
      throw H2O.fail("Failed to find ModelBuilder class in registry: " + clz);

    for (Map.Entry<String, Class<? extends ModelBuilder>> entry : _builders.entrySet())
      if (entry.getValue().equals(clz))
        return entry.getKey();
    // Note: unreachable:
    throw H2O.fail("Failed to find ModelBuilder class in registry: " + clz);
  }

  /**
   * Externally visible default schema
   * TODO: this is in the wrong layer: the internals should not know anything about the schemas!!!
   * This puts a reverse edge into the dependency graph.
   */
  public abstract ModelBuilderSchema schema();


  /** Constructor called from an http request; MUST override in subclasses. */
  public ModelBuilder(P parms) {
    super(Key.make("Failed"),"ModelBuilder constructor needs to be overridden.");
    throw H2O.unimpl("ModelBuilder subclass failed to override the params constructor: " + this.getClass());
  }

  public ModelBuilder(Key jobKey, Key dest, String desc, P parms) {
    super(jobKey,dest,desc);
    this._parms = parms;
  }
  public ModelBuilder(String desc, P parms) {
    super((parms._destination_key== null ? Key.make(desc + "Model_" + Key.rand()) : parms._destination_key), desc);
    _parms = parms;
    if( parms.sanityCheckParameters() > 0 )
      throw new IllegalArgumentException("Error(s) in model parameters: " + parms.validationErrors());
  }

  /** Factory method to create a ModelBuilder instance of the correct class given the algo name. */
  public static ModelBuilder createModelBuilder(String algo) {
    ModelBuilder modelBuilder = null;

    try {
      Class<? extends ModelBuilder> clz = ModelBuilder.getModelBuilder(algo);
      if (! (clz.getGenericSuperclass() instanceof ParameterizedType)) {
        throw H2O.fail("Class is not parameterized as expected: " + clz);
      }

      Type[] handler_type_parms = ((ParameterizedType)(clz.getGenericSuperclass())).getActualTypeArguments();
      // [0] is the Model type; [1] is the Model.Parameters type; [2] is the Model.Output type.
      Class<? extends Model.Parameters> pclz = (Class<? extends Model.Parameters>)handler_type_parms[1];
      Constructor<ModelBuilder> constructor = (Constructor<ModelBuilder>)clz.getDeclaredConstructor(new Class[] { (Class)handler_type_parms[1] });
      Model.Parameters p = pclz.newInstance();
      modelBuilder = constructor.newInstance(p);
    }
    catch (Exception e) {
      throw H2O.fail("Exception when trying to instantiate ModelBuilder for: " + algo + ": " + e);
    }

    return modelBuilder;
  }

  /** Method to launch training of a Model, based on its parameters. */
  abstract public Job<M> train();
}
