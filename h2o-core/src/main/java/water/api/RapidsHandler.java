package water.api;

import water.H2O;
import water.rapids.Exec;
import water.rapids.Session;
import water.rapids.Val;
import water.util.Log;

class RapidsHandler extends Handler {
  public RapidsSchema exec(int version, RapidsSchema rapids) {
    if( rapids == null ) return null;
    if( rapids.ast == null || rapids.ast.equals("") ) return rapids;
    if( rapids.session_id == null || rapids.session_id.equals("") )
      rapids.session_id = "_specialSess";

    Session ses = InitIDHandler.SESSIONS.get(rapids.session_id);
    if( ses == null )
      InitIDHandler.SESSIONS.put(rapids.session_id, ses = new water.rapids.Session());

    Val val;
    try {
      // No locking, no synchronization - since any local locking is NOT a
      // cluster-wide lock locking, which just provides the illusion of safety
      // but not the actuality.
      val = Exec.exec(rapids.ast, ses);
    } catch( IllegalArgumentException e ) {
      throw e;
    } catch( Throwable e ) {
      Log.err(e);
      e.printStackTrace();
      throw e;
    }

    switch( val.type() ) {
    case Val.NUM:  return new RapidsNumberV3(val.getNum());
    case Val.NUMS: return new RapidsNumbersV3(val.getNums());
    case Val.STR:  return new RapidsStringV3(val.getStr());
    case Val.STRS: return new RapidsStringsV3(val.getStrs());
    case Val.FRM:  return new RapidsFrameV3 (val.getFrame());
    case Val.FUN:  return new RapidsFunctionV3(val.getFun().toString());
    default:  throw H2O.fail();
    }
  }
}
