package water.automl.api;

import hex.leaderboard.Leaderboard;
import water.*;
import water.api.Handler;
import water.api.schemas3.TwoDimTableV3;
import water.automl.api.schemas3.LeaderboardV99;
import water.automl.api.schemas3.LeaderboardsV99;
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
        Leaderboard leaderboard = getFromDKV(leaderboardKeys[i]);
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
  public LeaderboardV99 fetch(int version, LeaderboardsV99 input) {
    if (null == input.project_name)
      throw new H2OKeyNotFoundArgumentException("Client must specify a project_name.");
    Leaderboard leaderboard = getFromDKV(Key.make(Leaderboard.idForProject(input.project_name)), "project_name");
    LeaderboardV99 lb = new LeaderboardV99().fillFromImpl(leaderboard);
    if (input.extensions != null) {
      lb.table = new TwoDimTableV3().fillFromImpl(leaderboard.toTwoDimTable(input.extensions));
    }
    return lb;
  }

  private static Leaderboard getFromDKV(Key key) {
      return getFromDKV(key, "(none)");
  }

  private static Leaderboard getFromDKV(Key key, String argName) {
    Value v = DKV.get(key);
    if (v == null)
      throw new H2OKeyNotFoundArgumentException(key.toString());

    Iced ice = v.get();
    if (! (ice instanceof Leaderboard))
      throw new H2OKeyWrongTypeArgumentException(argName, key.toString(), Leaderboard.class, ice.getClass());

    return (Leaderboard) ice;
  }
}
