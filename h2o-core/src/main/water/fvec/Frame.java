package water.fvec;

import water.*;

public class Frame extends Lockable {

  public Frame( Key key, String names[], Vec vecs[] ) { throw H2O.unimpl(); }
  public Frame( String names[], Vec vecs[] ) { throw H2O.unimpl(); }
  public Frame( Vec vecs[] ) { throw H2O.unimpl(); }
  public final boolean checkCompatible() { throw H2O.unimpl(); }
  public void reloadVecs() { throw H2O.unimpl(); }
  public int numCols() { throw H2O.unimpl(); }
  public final Vec anyVec() { throw H2O.unimpl(); }
  public final Vec[] vecs() { throw H2O.unimpl(); }
  public final long byteSize() { throw H2O.unimpl(); }
}
