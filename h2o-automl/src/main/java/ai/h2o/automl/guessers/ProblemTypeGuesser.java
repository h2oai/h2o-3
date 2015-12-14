package ai.h2o.automl.guessers;


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

  public static boolean guess(byte vecType, boolean isInt) {
    if( vecType == Vec.T_CAT ) return true;
    else if( !isInt ) {
      // do some guessing to reinforce that this is a regression problem
      return true;
    } else {
      // do some more thorough analysis on the column, might be somewhat intensive
      return false;
    }
  }
}
