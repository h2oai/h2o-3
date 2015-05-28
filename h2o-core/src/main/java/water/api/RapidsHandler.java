package water.api;

import water.DKV;
import water.H2O;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.currents.Val;
import water.util.Log;

class RapidsHandler extends Handler {
  public RapidsV3 exec(int version, RapidsV3 rapids) {
    if( rapids == null ) return null;
    if (rapids.ast == null || rapids.ast.equals("")) return rapids;
    Throwable e = null;
    try {
    // No locking, no synchronization - since any local locking is NOT a
    // cluster-wide lock locking, which just provides the illusion of safety but not
    // the actuality.
//private static final Object _lock = new Object();
//    synchronized( _lock ) {
//    }
    
      Val val = water.currents.Exec.exec(rapids.ast);
      switch( val.type() ) {
      case Val.NUM:  rapids.scalar = val.getNum(); break;
      case Val.STR:  rapids.string = val.getStr(); break;
      case Val.FUN:  rapids.funstr = val.getFun().toString(); break;
      case Val.FRM:
        Frame fr = val.getFrame();
        if( fr._key == null ) DKV.put(fr = new Frame(fr)); // Add a random key, if none there
        rapids.key = new KeyV3.FrameKeyV3(fr._key); // Return the Frame key, not the entire frame
        break;
      default:  throw H2O.fail();
      }

    } catch( IllegalArgumentException pe ) {
      e = pe;
    } catch (Throwable e2) {
      Log.err(e = e2);
    } finally {
      if( e != null ) {
        e.printStackTrace();
        rapids.error = (e.getMessage() == null||e instanceof ArrayIndexOutOfBoundsException) ? e.toString() : e.getMessage();
        throw new H2OIllegalArgumentException(rapids.error);
      }
      return rapids;
    }
  }
}
