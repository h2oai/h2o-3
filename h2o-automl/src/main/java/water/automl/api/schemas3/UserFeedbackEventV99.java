package water.automl.api.schemas3;

import ai.h2o.automl.UserFeedbackEvent;
import ai.h2o.automl.UserFeedbackEvent.Level;
import ai.h2o.automl.UserFeedbackEvent.Stage;
import water.api.API;
import water.api.EnumValuesProvider;
import water.api.Schema;

public class UserFeedbackEventV99 extends Schema<UserFeedbackEvent, UserFeedbackEventV99> {

  @API(help="Timestamp for this event, in milliseconds since Jan 1, 1970", direction=API.Direction.OUTPUT)
  public long timestamp;

  @API(help="Importance of this feedback event", valuesProvider = LevelProvider.class, direction=API.Direction.OUTPUT)
  public Level level;

  @API(help="Stage of the AutoML process for this feedback event", valuesProvider = StageProvider.class, direction=API.Direction.OUTPUT)
  public Stage stage;

  @API(help="User message for this event", direction=API.Direction.OUTPUT)
  public String message;

  public static final class LevelProvider extends EnumValuesProvider<Level> {
    public LevelProvider() { super(Level.class); }
  }

  public static final class StageProvider extends EnumValuesProvider<Stage> {
    public StageProvider() { super(Stage.class); }
  }
}
