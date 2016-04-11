package ai.h2o.automl.guessers;


/**
 * Guess column information based on the column names. As a first pass, stupidly do some
 * string matching.
 *
 * Tries to identify things like:
 *  ID
 *  Age
 *  Income
 *  Gender
 *  Date/Time (month, year, day, time)
 *
 * Use some common column naming patterns to make guesses about the data.
 */
public final class ColNameScanner {
  private final static String[] _id = {"id", "key"};  // TODO: more exotic like "customer" ?
  private final static String[] _age = {"age"};
  private final static String[] _gender = {"gender", "sex", "male", "female", "male_female"};
  private final static String[] _datetime = {"ts", "dt", "date", "year", "month", "day", "hour", "minute", "sec", "s", "time", "datetime"};


  // nothing special on this column based on its name; compute usual battery of rollups
  public final static byte UNK = 0;

  // ID alone is not grounds for ignoring the column:
  // but duplicated many times within the data set, I would then try to calculate a ton of
  // features about that entity. Count of occurrences and the average target rate per ID;
  // both prior to the specific observation if time is known. That's the group-by/merge
  // pattern I've been mentioning. If you have IDs and you expect to continue to see
  // them and it's valid to reuse them, then take guesses at a lot of calculations about
  // those IDs.
  public final static byte IGNORED=-1;
  public final static byte ID  = 1;
  public final static byte AGE = 2;
  public final static byte GENDER = 3;

  // try out differences in days, ordering, things like that.
  // Understanding that you may have a temporal (time) problem and SOUND THE ALARM.
  // If you have time-oriented data, you need to be careful that you don't
  // leak things to yourself.
  public final static byte DATETIME = 4;

  public static byte scan(String columnName) { return UNK; }
}
