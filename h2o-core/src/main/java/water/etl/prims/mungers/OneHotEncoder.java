package water.etl.prims.mungers;

import water.etl.prims.reducers.CumSum;
import water.fvec.Frame;
import water.fvec.Vec;
import water.MRTask;
import water.fvec.Chunk;

/**
 * Created by markc on 2/27/17.
 */
public final class OneHotEncoder {
  private OneHotEncoder() { }
  public static Frame get(Frame fr, String col) {
    Frame oneVec = new Frame(Vec.makeCon(1.0, fr.anyVec().length()));
    oneVec.setNames(new String[]{"h2o_ones"});
    fr.add(oneVec);
    Frame sumVec = CumSum.get(new Frame(oneVec),1.0);
    sumVec.setNames(new String[]{"h2o_cumsum_tmp"});
    fr.add(sumVec);
    Frame pivoted = Pivot.get(fr,"h2o_cumsum_tmp",col,"h2o_ones");
    new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        for (int i = 0; i < cs.length; i++) {
          for (int j = 0; j < cs[0].len(); j++) {
            if (Double.isNaN(cs[i].atd(j))) {
              cs[i].set(j, 0.0);
            }
          }
        }
      }
    }.doAll(pivoted);
    fr.remove("h2o_cumsum_tmp");
    fr.remove("h2o_ones");
    fr.add(pivoted);
    return fr;
  }
}
