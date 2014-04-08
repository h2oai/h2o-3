package water.fvec;

import water.Futures;
import water.H2O;
import water.Key;

public class AppendableVec extends Vec {
  public AppendableVec( Key key ) { super(null,null,null); throw H2O.unimpl(); }
  public String[] _domain;
  public Vec close( Futures fs ) { throw H2O.unimpl(); }
  public AppendableVec reduce( AppendableVec avec ) { throw H2O.unimpl(); }
}
