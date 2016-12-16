package ai.h2o.cascade;

import water.api.Handler;
import water.api.schemas4.OutputSchemaV4;
import water.api.schemas4.input.CascadeCloseSessionIV4;
import water.api.schemas4.input.CascadeIV4;
import water.api.schemas4.input.CascadeSessionIV4;
import water.api.schemas4.output.CascadeOV4;
import water.api.schemas4.output.CascadeSessionOV4;

import java.util.HashMap;


/**
 * <h1>REST API handlers for Cascade endpoints:</h1>
 *
 * <b>{@link CascadeHandlers.Run}</b>
 * <p>Execute a cascade statement within the provided session.</p>
 *
 * <b>{@link CascadeHandlers.StartSession}</b>
 * <p>Start a new Cascade session.</p>
 *
 * <b>{@link CascadeHandlers.CloseSession}</b>
 * <p>Close an existing Cascade session.</p>
 */
public abstract class CascadeHandlers {
  /** Map of session-ids (sent by the client) to the actual session instance. */
  public static final HashMap<String, CascadeSession> SESSIONS = new HashMap<>();


  //--------------------------------------------------------------------------------------------------------------------

  public static class Run extends Handler {
    public String name() {
      return "runCascade";
    }
    public String help() {
      return "Execute a Cascade statement within the provided session.";
    }

    public CascadeOV4 exec(int ignored, CascadeIV4 input) {
      String sessionId = input.session_id;
      if (!SESSIONS.containsKey(sessionId))
        throw new IllegalArgumentException("Session id " + sessionId + " is not valid");

      CascadeSession sess;
      synchronized (sess = SESSIONS.get(sessionId)) {

        return new CascadeOV4();
      }
    }

  }


  //--------------------------------------------------------------------------------------------------------------------

  public static class StartSession extends Handler {
    public String name() {
      return "startCascadeSession";
    }
    public String help() {
      return "Start a new Cascade session, and return the session's id.";
    }

    public CascadeSessionOV4 exec(int ignored, CascadeSessionIV4 input) {
      CascadeSession sess = new CascadeSession(input.user);
      String sessionId = sess.id();

      synchronized (SESSIONS) {
        // Extremely rare, but nonetheless possible to accidentally generate
        // same session id as already exists. In this case just restart.
        if (SESSIONS.containsKey(sessionId)) return exec(ignored, input);

        SESSIONS.put(sessionId, sess);
      }

      CascadeSessionOV4 res = new CascadeSessionOV4();
      res.session_id = sessionId;
      return res;
    }
  }


  //--------------------------------------------------------------------------------------------------------------------

  public static class CloseSession extends Handler {
    public String name() {
      return "closeCascadeSession";
    }
    public String help() {
      return "Finish a Cascade session, removing all objects created within it.";
    }

    public OutputSchemaV4 exec(int ignored, CascadeCloseSessionIV4 input) {
      String sessionId = input.session_id;

      CascadeSession sess;
      synchronized (SESSIONS) {
        sess = SESSIONS.remove(sessionId);
      }
      if (sess == null)
        throw new IllegalArgumentException("Session " + sessionId + " does not exist.");
      else
        sess.close();

      return new OutputSchemaV4();
    }
  }

}
