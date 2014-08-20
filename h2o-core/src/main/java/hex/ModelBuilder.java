package hex;

import hex.schemas.ModelBuilderSchema;
import water.H2O;
import water.Job;
import water.Key;
import water.Model;

import java.util.HashMap;
import java.util.Map;

/**
 *  Model builder parent class.  Contains the common interfaces and fields across all model builders.
 */
abstract public class ModelBuilder<M extends Model<M,P,O>, P extends Model.Parameters<M,P,O>, O extends Model.Output<M,P,O>> extends Job<M> {
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

  /**
   * Externally visible default schema
   * TODO: this is in the wrong layer: the internals should not know anything about the schemas!!!
   * This puts a reverse edge into the dependency graph.
   */
  public abstract ModelBuilderSchema schema();


  /** Constructor called from an http request; MUST override in subclasses. */
  public ModelBuilder(Model.Parameters parms) {
    super(Key.make("Failed"),"ModelBuilder constructor needs to be overridden.", Long.MAX_VALUE);
    throw H2O.unimpl("ModelBuilder subclass failed to override the params constructor: " + this.getClass());
  }

  public ModelBuilder(Key dest, String desc, P parms, long work) {
    super(dest, desc, work);
    this._parms = parms;
  }

  /** Method to launch training of a Model, based on its parameters. */
  abstract public Job<M> train();
}
