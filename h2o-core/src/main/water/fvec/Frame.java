package water.fvec;

import water.Lockable;
import water.H2O;

public class Frame extends Lockable {

  public final Vec[] vecs() { throw H2O.unimpl(); }
  public final long byteSize() { throw H2O.unimpl(); }
}
