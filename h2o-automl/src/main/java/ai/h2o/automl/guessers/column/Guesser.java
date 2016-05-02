package ai.h2o.automl.guessers.column;


import ai.h2o.automl.colmeta.ColMeta;
import water.fvec.Vec;

/**
 * A guesser tries to perform some type of action on a column based on some metadata.
 *
 * For example, a column name may indicate that the vector is an ID or Date column.
 *
 * ColMeta constructor uses reflection to dynamically pickup all guessers at runtime.
 */
public abstract class Guesser {

  protected final ColMeta _cm;

  Guesser(ColMeta cm) { _cm=cm; }

  /**
   * Void method that possibly mutates the fields of _cm.
   */
  abstract void guess0(String name, Vec v);

  /**
   * Shared guess code
   * @param name column name
   * @param v the actual vec to perform any kind of guessing on
   */
  public void guess(String name, Vec v) {
    if( _cm._ignored ) return;
    guess0(name,v);
  }
}
