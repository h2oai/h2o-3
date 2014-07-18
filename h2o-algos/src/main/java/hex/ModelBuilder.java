package hex;

import java.util.Arrays;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.Log;

/**
 *  Model builder parent class.  Contains the common interfaces and fields across all model builders.
 */
abstract public class ModelBuilder<M extends Model<M,P,O>, P extends Model.Parameters<M,P,O>, O extends Model.Output<M,P,O>> extends Job<M> {
  /** All the parameters required to build the model. */
  public P _parms;

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
  abstract public Job train();
}
