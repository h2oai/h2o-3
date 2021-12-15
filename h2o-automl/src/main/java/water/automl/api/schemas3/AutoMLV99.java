package water.automl.api.schemas3;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.events.EventLog;
import ai.h2o.automl.leaderboard.Leaderboard;
import ai.h2o.automl.StepDefinition;
import hex.genmodel.utils.DistributionFamily;
import water.Iced;
import water.Key;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;
import water.api.schemas3.TwoDimTableV3;
import water.logging.LoggingLevel;

// TODO: this is about to change from SchemaV3 to RequestSchemaV3:
public class AutoMLV99 extends SchemaV3<AutoML,AutoMLV99> {
  
  public static class AutoMLKeyV3 extends KeyV3<Iced, AutoMLKeyV3, AutoML> {
    public AutoMLKeyV3() { }

    public AutoMLKeyV3(Key<AutoML> key) {
      super(key);
    }
  }

  @API(help="Optional AutoML run ID; omitting this returns all runs", direction=API.Direction.INPUT)
  public AutoMLKeyV3 automl_id;

  @API(help="Verbosity level of the returned event log", direction=API.Direction.INOUT,
          valuesProvider= EventLogEntryV99.LevelProvider.class)
  public LoggingLevel verbosity;

  @API(help="ID of the actual training frame for this AutoML run after any automatic splitting", direction=API.Direction.OUTPUT)
  public KeyV3.FrameKeyV3 training_frame;

  @API(help="ID of the actual validation frame for this AutoML run after any automatic splitting", direction=API.Direction.OUTPUT)
  public KeyV3.FrameKeyV3 validation_frame;

  @API(help="ID of the actual blending frame used to train the Stacked Ensembles in blending mode", direction = API.Direction.OUTPUT)
  public KeyV3.FrameKeyV3 blending_frame;
  
  @API(help="ID of the actual leaderboard frame for this AutoML run after any automatic splitting", direction=API.Direction.OUTPUT)
  public KeyV3.FrameKeyV3 leaderboard_frame;

  /**
   * Identifier for models that should be grouped together in the leaderboard
   * (e.g., "airlines" and "iris").
   */
  @API(help="Identifier for models that should be grouped together in the same leaderboard", direction=API.Direction.INOUT)
  public String project_name;

  @API(help="The leaderboard for this project, potentially including models from other AutoML runs", direction=API.Direction.OUTPUT)
  public LeaderboardV99 leaderboard;

  @API(help="The leaderboard for this project, potentially including models from other AutoML runs, for easy rendering", direction=API.Direction.OUTPUT)
  public TwoDimTableV3 leaderboard_table;

  @API(help="Event log of this AutoML run", direction=API.Direction.OUTPUT)
  public EventLogV99 event_log;

  @API(help="Event log of this AutoML run, for easy rendering", direction=API.Direction.OUTPUT)
  public TwoDimTableV3 event_log_table;

  @API(help="Metric used to sort leaderboard", direction=API.Direction.INPUT)
  public String sort_metric;

  @API(help="The list of modeling steps effectively used during the AutoML run", direction=API.Direction.OUTPUT)
  public StepDefinitionV99[] modeling_steps;

  @Override public AutoMLV99 fillFromImpl(AutoML autoML) {
    super.fillFromImpl(autoML, new String[] { "leaderboard", "event_log", "leaderboard_table", "event_log_table", "sort_metric", "modeling_steps" });

    if (null == autoML) return this;

    project_name = autoML.projectName();

    if (null != autoML._key) {
      automl_id = new AutoMLKeyV3(autoML._key);
    }

    if (null != autoML.getTrainingFrame()) {
      training_frame = new KeyV3.FrameKeyV3(autoML.getTrainingFrame()._key);
    }

    if (null != autoML.getValidationFrame()) {
      validation_frame = new KeyV3.FrameKeyV3(autoML.getValidationFrame()._key);
    }

    if (null != autoML.getBlendingFrame()) {
      blending_frame = new KeyV3.FrameKeyV3(autoML.getBlendingFrame()._key);
    }

    if (null != autoML.getLeaderboardFrame()) {
      leaderboard_frame = new KeyV3.FrameKeyV3(autoML.getLeaderboardFrame()._key);
    }

    // NOTE: don't return nulls; return an empty leaderboard/eventLog, to ease life for the client
    EventLog eventLog = autoML.eventLog();
    if (null == eventLog) {
      eventLog = new EventLog(autoML._key);
    }
    event_log = new EventLogV99();
    event_log.verbosity = verbosity;
    event_log.fillFromImpl(eventLog);
    event_log_table = event_log.table;  // for backwards compatibility

    Leaderboard lb = autoML.leaderboard();
    if (null == lb) {
      lb = new Leaderboard(project_name, eventLog, autoML.getLeaderboardFrame(), sort_metric);
    }
    leaderboard = new LeaderboardV99().fillFromImpl(lb);
    leaderboard_table = leaderboard.table; //for backwards compatibility

    if (autoML.getActualModelingSteps() != null) {
      modeling_steps = new StepDefinitionV99[autoML.getActualModelingSteps().length];
      int i = 0;
      for (StepDefinition stepDef : autoML.getActualModelingSteps()) {
        modeling_steps[i++] = new StepDefinitionV99().fillFromImpl(stepDef);
      }
    }

    return this;
  }
}
