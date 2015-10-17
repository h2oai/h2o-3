package water.api;

import water.*;
import water.rapids.Val;
import water.fvec.Frame;
import water.util.Log;

class RapidsHandler extends Handler {
  public RapidsSchema exec(int version, RapidsSchema rapids) {
    if( rapids == null ) return null;
    if( rapids.ast == null || rapids.ast.equals("") ) return rapids;
    Val val;
    try {
      // No locking, no synchronization - since any local locking is NOT a
      // cluster-wide lock locking, which just provides the illusion of safety
      // but not the actuality.
      val = water.rapids.Exec.exec(rapids.ast);
    } catch( IllegalArgumentException e ) {
      throw e;
    } catch( Throwable e ) {
      Log.err(e);
      throw e;
    }

    switch( val.type() ) {
    case Val.NUM:  return new RapidsScalarV3(val.getNum());
    case Val.STR:  return new RapidsStringV3(val.getStr());
    case Val.FRM:  return new RapidsFrameV3 (val.getFrame());
    case Val.FUN:  return new RapidsFunctionV3(val.getFun().toString());
    default:  throw H2O.fail();
    }
  }
}
