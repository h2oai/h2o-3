package water.automl.api.schemas3;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.Leaderboard;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;
import water.api.schemas3.TwoDimTableV3;

// TODO: this is about to change from SchemaV3 to RequestSchemaV3:
public class AutoMLV99 extends SchemaV3<AutoML,AutoMLV99> {
  @API(help="Optional AutoML run ID; omitting this returns all runs", direction=API.Direction.INPUT)
  public AutoML.AutoMLKeyV3 automl_id;

  @API(help="ID of the actual training frame for this AutoML run after any automatic splitting", direction=API.Direction.OUTPUT)
  public KeyV3.FrameKeyV3 training_frame;

  @API(help="ID of the actual validation frame for this AutoML run after any automatic splitting", direction=API.Direction.OUTPUT)
  public KeyV3.FrameKeyV3 validation_frame;

  @API(help="ID of the actual leaderboard frame for this AutoML run after any automatic splitting", direction=API.Direction.OUTPUT)
  public KeyV3.FrameKeyV3 leaderboard_frame;

  /**
   * Identifier for models that should be grouped together in the leaderboard
   * (e.g., "airlines" and "iris").
   */
  @API(help="Identifier for models that should be grouped together in the same leaderboard", direction=API.Direction.INOUT)
  public String project_name = "<default>";

  @API(help="The leaderboard for this project, potentially including models from other AutoML runs", direction=API.Direction.OUTPUT)
  public LeaderboardV99   leaderboard;

  @API(help="The leaderboard for this project, potentially including models from other AutoML runs, for easy rendering", direction=API.Direction.OUTPUT)
  public TwoDimTableV3   leaderboard_table;

  @API(help="User feedback for events from this AutoML run", direction=API.Direction.OUTPUT)
  public UserFeedbackV99 user_feedback;

  @API(help="User feedback for events from this AutoML run, for easy rendering", direction=API.Direction.OUTPUT)
  public TwoDimTableV3 user_feedback_table;

  @Override public AutoMLV99 fillFromImpl(AutoML autoML) {
    super.fillFromImpl(autoML, new String[] { "leaderboard", "user_feedback", "leaderboard_table", "user_feedback_table" });

    if (null == autoML) return this;

    this.project_name = autoML.projectName();

    if (null != autoML._key) {
      this.automl_id = new AutoML.AutoMLKeyV3(autoML._key);
    }

    if (null != autoML.getTrainingFrame()) {
      this.training_frame = new KeyV3.FrameKeyV3(autoML.getTrainingFrame()._key);
    }

    if (null != autoML.getValidationFrame()) {
      this.validation_frame = new KeyV3.FrameKeyV3(autoML.getValidationFrame()._key);
    }

    if (null != autoML.getLeaderboardFrame()) {
      this.leaderboard_frame = new KeyV3.FrameKeyV3(autoML.getLeaderboardFrame()._key);
    }

    Leaderboard leaderboard = autoML.leaderboard();
    if (null != leaderboard) {
      this.leaderboard = new LeaderboardV99().fillFromImpl(leaderboard);
      this.leaderboard_table = new TwoDimTableV3().fillFromImpl(leaderboard.toTwoDimTable());
    }

    if (null != autoML.userFeedback()) {
      this.user_feedback = new UserFeedbackV99().fillFromImpl(autoML.userFeedback());
      this.user_feedback_table = new TwoDimTableV3().fillFromImpl(autoML.userFeedback().toTwoDimTable(autoML._key.toString()));
    }

    return this;
  }
}
