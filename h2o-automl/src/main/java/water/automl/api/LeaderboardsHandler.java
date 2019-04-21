package water.automl.api;

import ai.h2o.automl.Leaderboard;
import water.*;
import water.api.Handler;
import water.automl.api.schemas3.LeaderboardV99;
import water.automl.api.schemas3.LeaderboardsV99;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.exceptions.H2OKeyWrongTypeArgumentException;

public class LeaderboardsHandler extends Handler {
  /** Class which contains the internal representation of the leaderboards list and params. */
  public static final class Leaderboards extends Iced {
    public Leaderboard[] leaderboards;

    public static Leaderboard[] fetchAll() {
      final Key<Leaderboard>[] leaderboardKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
          @Override
          public boolean filter(KeySnapshot.KeyInfo k) {
            return Value.isSubclassOf(k._type, Leaderboard.class);
          }
        }).keys();

      Leaderboard[] leaderboards = new Leaderboard[leaderboardKeys.length];
      for (int i = 0; i < leaderboardKeys.length; i++) {
        Leaderboard leaderboard = getFromDKV("(none)", leaderboardKeys[i]);
        leaderboards[i] = leaderboard;
      }

      return leaderboards;
    }

  } // public class Leaderboards

    /** Return all the Leaderboards. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public water.automl.api.schemas3.LeaderboardsV99 list(int version, water.automl.api.schemas3.LeaderboardsV99 s) {
    Leaderboards m = s.createAndFillImpl();
    m.leaderboards = Leaderboards.fetchAll();
    return s.fillFromImpl(m);
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public LeaderboardV99 fetch(int version, LeaderboardsV99 s) {
    if (null == s.project_name)
      throw new H2OKeyNotFoundArgumentException("Client must specify a project_name.");

      return new LeaderboardV99().fillFromImpl(getFromDKV("project_name", Leaderboard.idForProject(s.project_name)));
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static Leaderboard getFromDKV(String param_name, String key_str) {
    return getFromDKV(param_name, Key.make(key_str));
  }

  // TODO: almost identical to ModelsHandler; refactor
  public static Leaderboard getFromDKV(String param_name, Key key) {
    if (key == null)
      throw new H2OIllegalArgumentException(param_name, "Leaderboard.getFromDKV()", null);

    Value v = DKV.get(key);
    if (v == null)
      throw new H2OKeyNotFoundArgumentException(param_name, key.toString());

    Iced ice = v.get();
    if (! (ice instanceof Leaderboard))
      throw new H2OKeyWrongTypeArgumentException(param_name, key.toString(), Leaderboard.class, ice.getClass());

    return (Leaderboard) ice;
  }
}
