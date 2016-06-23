package water.api;

import water.H2O;
import water.api.schemas3.*;
import water.exceptions.H2OIllegalArgumentException;
import water.rapids.Rapids;
import water.rapids.Session;
import water.rapids.Val;
import water.util.Log;
import water.util.StringUtils;

public class RapidsHandler extends Handler {

  public static RapidsSchemaV3 exec(int version, RapidsSchemaV3 rapids) {
    if (rapids == null) return null;
    if (rapids.id != null)
      throw new H2OIllegalArgumentException("Field RapidsSchemaV3.id is deprecated and should be null: " + rapids.id);

    if (StringUtils.isNullOrEmpty(rapids.ast)) return rapids;
    if (StringUtils.isNullOrEmpty(rapids.session_id))
      rapids.session_id = "_specialSess";
    assert rapids.id == null || rapids.id.equals(""): "Rapids 'id' parameter is unused and should not be set.";


    Session ses = InitIDHandler.SESSIONS.get(rapids.session_id);
    if (ses == null) {
      ses = new Session();
      InitIDHandler.SESSIONS.put(rapids.session_id, ses);
    }

    Val val;
    try {
      // No locking, no synchronization - since any local locking is NOT a
      // cluster-wide lock locking, which just provides the illusion of safety
      // but not the actuality.
      val = Rapids.exec(rapids.ast, ses);
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Throwable e) {
      Log.err(e);
      e.printStackTrace();
      throw e;
    }

    switch (val.type()) {
      case Val.NUM:  return new RapidsNumberV3(val.getNum());
      case Val.NUMS: return new RapidsNumbersV3(val.getNums());
      case Val.STR:  return new RapidsStringV3(val.getStr());
      case Val.STRS: return new RapidsStringsV3(val.getStrs());
      case Val.FRM:  return new RapidsFrameV3(val.getFrame());
      case Val.FUN:  return new RapidsFunctionV3(val.getFun().toString());
      default:       throw H2O.fail();
    }
  }

}
