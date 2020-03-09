package ai.h2o.automl.events;

import ai.h2o.automl.AutoML;
import water.Iced;
import water.Key;
import water.util.TwoDimTable;

import java.io.Serializable;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public class EventLogEntry<V extends Serializable> extends Iced {

  public enum Level {
    Debug, Info, Warn
  }

  public enum Stage {
    Validation,
    Workflow,
    DataImport,
    FeatureAnalysis,
    FeatureReduction,
    FeatureCreation,
    ModelTraining,
    ModelSelection,
  }

  static TwoDimTable makeTwoDimTable(String tableHeader, int length) {
    String[] rowHeaders = new String[length];
    for (int i = 0; i < length; i++) rowHeaders[i] = "" + i;
    return new TwoDimTable(
            tableHeader,
            "Actions taken and discoveries made by AutoML",
            rowHeaders,
            EventLogEntry.colHeaders,
            EventLogEntry.colTypes,
            EventLogEntry.colFormats,
            "#"
    );
  }

  static String nowStr() {
    return dateTimeFormat.format(new Date());
  }

  static abstract class SimpleFormat<T> extends Format {
    @Override
    public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
      pos.setBeginIndex(0);
      pos.setEndIndex(0);
      format((T)obj, toAppendTo);
      return toAppendTo;
    }

    public abstract StringBuffer format(T t, StringBuffer toAppendTo);

    @Override
    public Object parseObject(String source, ParsePosition pos) {
      return null;
    }
  }

  public static final Format epochFormat = new SimpleFormat<Date>() {
    @Override
    public StringBuffer format(Date date, StringBuffer toAppendTo) {
        long epoch = Math.round(date.getTime() / 1e3);
        toAppendTo.append(epoch);
        return toAppendTo;
    }
  };
  public static final SimpleDateFormat dateTimeISOFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"); // uses local timezone
  public static final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.S"); // uses local timezone
  public static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.S");  // uses local timezone

  private static final String[] colHeaders = {
          "timestamp",
          "level",
          "stage",
          "message",
          "name",
          "value",
  };

  private static final String[] colTypes= {
          "string",
          "string",
          "string",
          "string",
          "string",
          "string",
  };

  private static final String[] colFormats= {
          "%s",
          "%s",
          "%s",
          "%s",
          "%s",
          "%s",
  };

  private static <E extends Enum<E>> int longest(Class<E> enu) {
    int longest = -1;
    for (E v : enu.getEnumConstants())
      longest = Math.max(longest, v.name().length());
    return longest;
  }

  private final int longestLevel = longest(Level.class); // for formatting
  private final int longestStage = longest(Stage.class); // for formatting

  private Key<AutoML> _automlKey;
  private long _timestamp;
  private Level _level;
  private Stage _stage;
  private String _message;
  private String _name;
  private V _value;
  private Format _valueFormatter;

  public Key<AutoML> getAutomlKey() { return _automlKey; }

  public long getTimestamp() {
    return _timestamp;
  }

  public Level getLevel() {
    return _level;
  }

  public Stage getStage() {
    return _stage;
  }

  public String getMessage() {
    return _message;
  }

  public String getName() {
    return _name;
  }

  public V getValue() {
    return _value;
  }

  public Format getValueFormatter() {
    return _valueFormatter;
  }

  public EventLogEntry(Key<AutoML> automlKey, Level level, Stage stage, String message) {
    _automlKey = automlKey;
    _timestamp = System.currentTimeMillis();
    _level = level;
    _stage = stage;
    _message = message;
  }

  public void setNamedValue(String name, V value) {
      setNamedValue(name, value, null);
  }

  public void setNamedValue(String name, V value, Format formatter) {
    _name = name;
    _value = value;
    _valueFormatter = formatter;
  }

  void addTwoDimTableRow(TwoDimTable table, int row) {
    int col = 0;
    table.set(row, col++, timeFormat.format(new Date(_timestamp)));
    table.set(row, col++, _level);
    table.set(row, col++, _stage);
    table.set(row, col++, _message);
    table.set(row, col++, _name);
    table.set(row, col++, _valueFormatter == null ? _value : _valueFormatter.format(_value));
  }

  @Override
  public String toString() {
    return String.format("%-12s %-"+longestLevel+"s %-"+longestStage+"s %s %s %s",
            timeFormat.format(new Date(_timestamp)),
            _level,
            _stage,
            Objects.toString(_message, ""),
            Objects.toString(_name, ""),
            _valueFormatter == null ? Objects.toString(_value, "") : _valueFormatter.format(_value)
    );
  }
}
