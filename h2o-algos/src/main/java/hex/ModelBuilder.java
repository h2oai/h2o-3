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

  public ModelBuilder(Key dest, String desc, long work) {
    super(dest, desc, work);
  }

  /** Method to launch training of a Model, based on its parameters. */
  abstract public Job train();
}
