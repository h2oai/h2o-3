package ai.h2o.automl.guessers.column;

import ai.h2o.automl.colmeta.ColMeta;
import org.apache.commons.lang.ArrayUtils;
import water.fvec.Vec;
import water.util.Log;
import water.util.MRUtils;

/**
 * If classificaiton problem, then determine whether or not to perform stratified sampling.
 */
public class StratifiedGuesser extends Guesser {
  public StratifiedGuesser(ColMeta cm) { super(cm); }
  @Override public void guess0(String name, Vec v) {
    if( !_cm._response ) return;
    if( !_cm.isClassification() ) return;
    double[] dist = new MRUtils.ClassDist(v).doAll(v).rel_dist();
    double frac = 1./dist.length;
    double avgDistFromUniform=0;
    for(double d: dist) avgDistFromUniform += Math.abs(frac-d);  // sum up the differences from the evenly distributed case
    avgDistFromUniform*=frac; // gives the average
    if( avgDistFromUniform > 0.1 ) {
      Log.info("class distribution is uneven, will attempt to stratify. dist=" + ArrayUtils.toString(dist));
      _cm._stratify = true;
    }
  }
}
