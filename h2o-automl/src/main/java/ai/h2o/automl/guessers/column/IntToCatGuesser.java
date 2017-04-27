package ai.h2o.automl.guessers.column;

import ai.h2o.automl.colmeta.ColMeta;
import water.fvec.Vec;

/**
 * Check to see if this integer column might be better off as a categorical.
 */
public class IntToCatGuesser extends Guesser {
  public IntToCatGuesser(ColMeta cm) { super(cm); }
  @Override public void guess0(String name, Vec v) {
    if( _cm._response ) return; // dont care

  }
}
