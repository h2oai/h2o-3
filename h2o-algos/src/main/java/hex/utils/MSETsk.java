package hex.utils;

import water.MRTask;
import water.fvec.Chunk;

/**
 * Created by tomasnykodym on 9/9/14.
 */
public class MSETsk extends MRTask<MSETsk> {
  public double _resDev;
  public long   _nobs;
  public void map(Chunk prediction, Chunk response){
    for(int i = 0; i < prediction._len; ++i){
      if(prediction.isNA0(i) || response.isNA0(i))
        continue;
      double diff = prediction.at0(i) - response.at0(i);
      _resDev += diff*diff;
      ++_nobs;
    }
  }

  @Override
  public void reduce(MSETsk t){
    _resDev += t._resDev;
    _nobs += t._nobs;
  }

}
