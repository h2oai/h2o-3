package hex.glm;

import hex.DataInfo;
import water.Futures;
import water.H2O;
import water.Iced;
import water.util.ArrayUtils;
import water.util.MRUtils;

/**
 * Created by tomas on 7/6/17.
 */
abstract class GLMSolver extends Iced {

  protected long estimateMemoryPerWorker(DataInfo dataInfo){return 8*dataInfo.fullN();}
  protected long estimateMemoryForDriver(DataInfo dinfo){return estimateMemoryPerWorker(dinfo);}

  protected final long estimateMemoryUsage(long max_memory,DataInfo activeData){
    return estimateMemoryForDriver(activeData) + estimateMemoryPerWorker(activeData)*MRUtils.memoryUsageMultiplier(activeData._adaptedFrame,false);
  }
  protected Futures cleanup(Futures fs){return fs;}
  protected abstract int defaultMaxIterations();
  protected abstract GLM.GLMState fit(GLM.GLMState glm);
}
