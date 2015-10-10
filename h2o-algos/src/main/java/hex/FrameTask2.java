package hex;

import hex.DataInfo.Row;
import water.DKV;
import water.H2O.H2OCountedCompleter;
import water.Job;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.*;
import water.util.FrameUtils;

/**
 * Created by tomasnykodym on 6/1/15.
 *
 * Frame task updated with sparse data support. Separate class for now,
 * should be merged with FrameTask(1) at some point.
 *
 *
 *
 */
public abstract class FrameTask2<T extends FrameTask2<T>> extends MRTask<T> {
  protected boolean _sparse;
  final Key     _jobKey;
  protected final DataInfo _dinfo;

  public FrameTask2(H2OCountedCompleter cmp, DataInfo dinfo, Key jobKey){
    super(cmp);
    _dinfo = dinfo;
    _jobKey = jobKey;
    _sparse = handlesSparseData() && FrameUtils.sparseRatio(dinfo._adaptedFrame) < .5;
  }

  public T setSparse(boolean b) { _sparse = b; return self();}

  /**
   * Initialization method, called once per "chunk".
   * Typically create result object used by processRow to store rersults.
   *
   */
  public void chunkInit(){}

  /**
   * Perform action after processing one "chunk" of data/
   */
  public void chunkDone(){}


  public boolean handlesSparseData(){return false;}
  abstract protected void processRow(Row r);

  @Override
  public void map(Chunk[] chks) {
    if(_jobKey != null && (DKV.get(_jobKey) == null || !Job.isRunning(_jobKey)))
      throw new Job.JobCancelledException();
    chunkInit();
    // compute
    if(_sparse) {
      for(Row r:_dinfo.extractSparseRows(chks, 0)) {
        if(!r.bad && r.weight != 0)
          processRow(r);
      }
    } else {
      Row row = _dinfo.newDenseRow();
      for(int r = 0 ; r < chks[0]._len; ++r) {
        _dinfo.extractDenseRow(chks, r, row);
        if(!row.bad && row.weight != 0)
          processRow(row);
      }
    }
    chunkDone();
  }

}
