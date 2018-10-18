package water.api;

import water.H2O;
import water.Key;
import water.api.schemas3.*;
import water.api.schemas3.RapidsHelpV3.RapidsExpressionV3;
import water.api.schemas4.InputSchemaV4;
import water.api.schemas4.SessionIdV4;
import water.exceptions.H2OIllegalArgumentException;
import water.rapids.Rapids;
import water.rapids.Session;
import water.rapids.Val;
import water.rapids.ast.AstRoot;
import water.util.Log;
import water.util.StringUtils;

import java.util.*;

public class RapidsHandler extends Handler {

  public RapidsSchemaV3 exec(int version, RapidsSchemaV3 rapids) {
    if (rapids == null) return null;
    if (!StringUtils.isNullOrEmpty(rapids.id))
      throw new H2OIllegalArgumentException("Field RapidsSchemaV3.id is deprecated and should not be set " + rapids.id);
    if (StringUtils.isNullOrEmpty(rapids.ast)) return rapids;
    if (StringUtils.isNullOrEmpty(rapids.session_id))
      rapids.session_id = "_specialSess";

    Session ses = RapidsHandler.SESSIONS.get(rapids.session_id);
    if (ses == null) {
      ses = new Session(rapids.session_id);
      RapidsHandler.SESSIONS.put(rapids.session_id, ses);
    }

    Val val;
    try {
      // This call is synchronized on the session instance
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
      case Val.ROW:  return new RapidsNumbersV3(val.getRow());
      case Val.STR:  return new RapidsStringV3(val.getStr());
      case Val.STRS: return new RapidsStringsV3(val.getStrs());
      case Val.FRM:  return new RapidsFrameV3(val.getFrame());
      case Val.MFRM:  return new RapidsMapFrameV3(val.getMapFrame());
      case Val.FUN:  return new RapidsFunctionV3(val.getFun().toString());
      default:       throw H2O.fail();
    }
  }

  public RapidsHelpV3 genHelp(int version, SchemaV3 noschema) {
    Iterator<AstRoot> iterator = ServiceLoader.load(AstRoot.class).iterator();
    List<AstRoot> rapids = new ArrayList<>();
    while (iterator.hasNext()) {
      rapids.add(iterator.next());
    }

    ArrayList<RapidsExpressionV3> expressions = new ArrayList<>();
    for(AstRoot expr: rapids){
      expressions.add(processAstClass(expr));
    }

    RapidsHelpV3 res = new RapidsHelpV3();
    res.expressions = expressions.toArray(new RapidsExpressionV3[expressions.size()]);
    return res;
  }

  private RapidsExpressionV3 processAstClass(AstRoot expr) {
    RapidsExpressionV3 target = new RapidsExpressionV3();
    target.name = expr.getClass().getSimpleName();
    target.pattern = expr.example();
    target.description = expr.description();
    return target;
  }


  /** Map of session-id (sent by the client) to the actual session instance. */
  public static HashMap<String, Session> SESSIONS = new HashMap<>();

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public InitIDV3 startSession(int version, InitIDV3 p) {
    p.session_key = "_sid" + Key.make().toString().substring(0,5);
    return p;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public InitIDV3 endSession(int version, InitIDV3 p) {
    if (SESSIONS.get(p.session_key) != null) {
      try {
        SESSIONS.get(p.session_key).end(null);
        SESSIONS.remove(p.session_key);
      } catch (Throwable ex) {
        throw SESSIONS.get(p.session_key).endQuietly(ex);
      }
    }
    return p;
  }

  public static class StartSession4 extends RestApiHandler<InputSchemaV4, SessionIdV4> {
    @Override public String name() {
      return "newSession4";
    }
    @Override public String help() {
      return "Start a new Rapids session, and return the session id.";
    }

    @Override
    public SessionIdV4 exec(int ignored, InputSchemaV4 input) {
      SessionIdV4 out = new SessionIdV4();
      out.session_key = "_sid" + Key.make().toString().substring(0, 5);
      return out;
    }
  }


}
