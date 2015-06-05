package water.api;

import water.*;
import water.fvec.Frame;
import water.currents.Val;
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
      val = water.currents.Exec.exec(rapids.ast);
    } catch( IllegalArgumentException e ) {
      throw e;
    } catch( Throwable e ) {
      Log.err(e);
      throw e;
    }

    switch( val.type() ) {
    case Val.NUM:  return new RapidsScalarV3(val.getNum());
    case Val.STR:  return new RapidsStringV3(val.getStr());
    case Val.FUN:  return new RapidsFunctionV3(val.getFun().toString());
    case Val.FRM:
      Frame fr = val.getFrame();
      assert fr._key==null;     // Rapids always returns a key-less Frame
      if( rapids.id==null ) {
        fr.delete();
        throw new IllegalArgumentException("Missing the result key 'id' for the returned frame");
      }
      Key k = Key.make(rapids.id);
      // Smart delete any prior top-level result
      Iced i = DKV.getGet(k);
      if( i instanceof Lockable) ((Lockable)i).delete();
      else if( i instanceof Keyed ) ((Keyed)i).remove();
      else if( i != null ) throw new IllegalArgumentException("Attempting to overright an unexpected key");
      // Install new top-level result
      DKV.put(fr=new Frame(k,fr._names,fr.vecs()));
      return new RapidsFrameV3(fr); // Return the Frame key, not the entire frame
    default:  throw H2O.fail();
    }
  }
}
