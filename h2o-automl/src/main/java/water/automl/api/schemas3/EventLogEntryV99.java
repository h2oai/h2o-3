package water.automl.api.schemas3;

import ai.h2o.automl.events.EventLogEntry;
import ai.h2o.automl.events.EventLogEntry.Stage;
import water.api.API;
import water.api.EnumValuesProvider;
import water.api.schemas3.SchemaV3;
import water.logging.LoggingLevel;

import java.util.Objects;

public class EventLogEntryV99 extends SchemaV3<EventLogEntry, EventLogEntryV99> {

  @API(help="Timestamp for this event, in milliseconds since Jan 1, 1970", direction=API.Direction.OUTPUT)
  public long timestamp;

  @API(help="Importance of this log event", valuesProvider = LevelProvider.class, direction=API.Direction.OUTPUT)
  public LoggingLevel level;

  @API(help="Stage of the AutoML process for this log event", valuesProvider = StageProvider.class, direction=API.Direction.OUTPUT)
  public Stage stage;

  @API(help="Message for this event", direction=API.Direction.OUTPUT)
  public String message;

  @API(help="String identifier associated to this entry", direction=API.Direction.OUTPUT)
  public String name;

  @API(help="Value associated to this entry", direction=API.Direction.OUTPUT)
  public String value;


  public static final class LevelProvider extends EnumValuesProvider<LoggingLevel> {
    public LevelProvider() { super(LoggingLevel.class); }
  }

  public static final class StageProvider extends EnumValuesProvider<Stage> {
    public StageProvider() { super(Stage.class); }
  }

  @Override
  public EventLogEntryV99 fillFromImpl(EventLogEntry impl) {
    super.fillFromImpl(impl, new String[] { "value", "valueFormatter" });
    this.value = impl.getValueFormatter() == null ? Objects.toString(impl.getValue(), "")
            : impl.getValueFormatter().format(impl.getValue());
    return this;
  }
}
