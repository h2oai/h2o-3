package water.automl.api.schemas3;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.Leaderboard;
import water.api.API;
import water.api.schemas3.SchemaV3;
import water.api.schemas3.TwoDimTableV3;

// TODO: this is about to change from SchemaV3 to RequestSchemaV3:
public class AutoMLV99 extends SchemaV3<AutoML,AutoMLV99> {
  @API(help="The AutoML key", direction=API.Direction.INPUT)
  public AutoML.AutoMLKeyV3 automl_id;

  /**
   * Identifier for models that should be grouped together in the leaderboard
   * (e.g., "airlines" and "iris").
   */
  @API(help="Identifier for models that should be grouped together in the leaderboard", direction=API.Direction.INOUT)
  public final String project = "<default>";

  @API(help="The leaderboard for this project, potentially including models from other AutoML runs", direction=API.Direction.OUTPUT)
  public LeaderboardV99   leaderboard;

  @API(help="User feedback for events from this AutoML run.", direction=API.Direction.OUTPUT)
  public TwoDimTableV3 user_feedback;

  @Override public AutoMLV99 fillFromImpl(AutoML m) {
    super.fillFromImpl(m, new String[] { "leaderboard", "user_feedback" });
    if (null != m._key) {
      this.automl_id = new AutoML.AutoMLKeyV3(m._key);
      Leaderboard leaderboard = m.leaderboard();
      if (null != leaderboard)
        this.leaderboard = new LeaderboardV99().fillFromImpl(leaderboard);
      if (null != m.userFeedback())
        this.user_feedback = new TwoDimTableV3().fillFromImpl(m.userFeedback().toTwoDimTable(m._key.toString()));
    }

    return this; // have to cast because the definition of S doesn't include ModelSchemaV3
  }
}
