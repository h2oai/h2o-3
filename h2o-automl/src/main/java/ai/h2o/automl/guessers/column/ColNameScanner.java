package ai.h2o.automl.guessers.column;


import ai.h2o.automl.colmeta.ColMeta;
import water.fvec.Vec;

import java.util.Collections;
import java.util.HashSet;

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
public final class ColNameScanner extends Guesser {
  private final static String[] IDS = {"id", "key"};  // TODO: more exotic like "customer" ?
  private final static String[] AGES = {"age"};
  private final static String[] GENDERS = {"gender", "sex", "male", "female", "male_female"};
  private final static String[] TIMES = {"ts", "dt", "date", "year", "month", "day", "hour", "minute", "sec", "s", "time", "datetime", "date_time", "time_date"};

  private static final HashSet<String> _id;
  private static final HashSet<String> _age;
  private static final HashSet<String> _gender;
  private static final HashSet<String> _datetime;


  static {
    _id = new HashSet<>(); Collections.addAll(_id,IDS);
    _age = new HashSet<>();  Collections.addAll(_age,AGES);
    _gender = new HashSet<>(); Collections.addAll(_gender,GENDERS);
    _datetime = new HashSet<>(); Collections.addAll(_datetime,TIMES);
  }

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
  public final static byte ID  = 1;  // basically ignore these, but might be useful for a join!
  public final static byte AGE = 2;
  public final static byte GENDER = 3;

  // try out differences in days, ordering, things like that.
  // Understanding that you may have a temporal (time) problem and SOUND THE ALARM.
  // If you have time-oriented data, you need to be careful that you don't
  // leak things to yourself.
  public final static byte DATETIME = 4;

  public ColNameScanner(ColMeta cm) { super(cm); }

  public void guess0(String name, Vec v) {
    if( _cm._response ) return; // dont care
    String columnName = name.toLowerCase();
    if( v.isTime() ) _cm._nameType=DATETIME;
    else if( v.isInt() ) {
      if( v.max()-v.min() < v.length()*.5 ) {
        // try for datetime, but if ID just return UNK since IDs likely to be useful
        _cm._nameType = _datetime.contains(columnName)
                ? DATETIME
                : _age.contains(columnName) ? AGE : ID;
      } else {
        // if ID then return ID, otherwise, return UNK
        if( _id.contains(columnName) ) {
          _cm._nameType = ID;
          return;
        }
        for (String idString : _id)
          if (columnName.contains(idString)) {
            _cm._nameType = ID;
            return;
          }
        _cm._nameType = UNK;
      }
    } else if( v.isCategorical() ) {
      if( v.bins().length < v.length() )
        _cm._nameType = _gender.contains(columnName) ? GENDER : UNK;
    } else _cm._nameType = UNK;
  }
}
