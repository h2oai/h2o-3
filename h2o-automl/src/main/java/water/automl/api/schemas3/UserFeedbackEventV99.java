package water.automl.api.schemas3;

import ai.h2o.automl.UserFeedbackEvent;
import water.api.API;
import water.api.Schema;

public class UserFeedbackEventV99 extends Schema<UserFeedbackEvent, UserFeedbackEventV99> {

  @API(help="Timestamp for this event, in milliseconds since Jan 1, 1970", direction=API.Direction.OUTPUT)
  public long timestamp;

  @API(help="Importance of this feedback event", values = { "Debug", "Info", "Warn" }, direction=API.Direction.OUTPUT)
  public UserFeedbackEvent.Level level;

  @API(help="Stage of the AutoML process for this feedback event", values = { "Workflow", "DataImport", "FeatureAnalysis", "FeatureReduction", "FeatureCreation", "ModelTraining" }, direction=API.Direction.OUTPUT)
  public UserFeedbackEvent.Stage stage;

  @API(help="User message for this event", direction=API.Direction.OUTPUT)
  public String message;
}
