package ai.h2o.automl.guessers;


import ai.h2o.automl.ColMeta;
import water.fvec.Vec;

/**
 * Is the problem regression or classification?
 *
 * This is typically a matter of reading the Vec's type IFF the type is categorical (then
 * it's classification). Or if the Vec is some non-integral numeric type, then it may be
 * safe to call it a regression problem (though AutoML better buttress with more data).
 *
 * Things start to get weird if the type is int: is it regression? is it classification?
 * What helps to decide if the column is discrete/continuous:
 *  How many uniques? (2? 10? 10000?)
 *  What's the column name? ("isDelayed" => binomial; "age" (still ambiguous?) noun or not?)
 *
 * If no decision is made what should we do? WARN or ERROR??
 */
public class ProblemTypeGuesser {
  ColMeta _meta;

  public static boolean guess(Vec v) { // true -> classification; false -> regression
    if( v.get_type() == Vec.T_CAT ) return true;
    else if( !v.isInt()) {
      if( v.isNumeric() ) return false;
      // should probably throw up if v.isString() (no h2o algo works in that case)
      return true;
    } else {
      if( v.isBinary()) return true;  // only checks for 0/1
      // do some more thorough analysis on the column, might be somewhat intensive
      // let's try to histo into 256 bins, we'll save some of this data for the metadata
      return false;
    }
  }
}
