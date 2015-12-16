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

  public final static byte UNK = 0;
  public final static byte ID  = 1;
  public final static byte AGE = 2;
  public final static byte GENDER = 3;
  public final static byte DATETIME = 4;

  public static byte scan(String columnName) {
    return UNK;
  }
}
