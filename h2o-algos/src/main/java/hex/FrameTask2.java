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
  public final Vec     _rowFilter;
  protected final DataInfo _dinfo;

  public FrameTask2(H2OCountedCompleter cmp, DataInfo dinfo, Key jobKey){
    this(cmp,dinfo,jobKey,null);
  }
  public FrameTask2(H2OCountedCompleter cmp, DataInfo dinfo, Key jobKey, Vec rowFilter){
    super(cmp);
    _dinfo = dinfo;
    _jobKey = jobKey;
    _rowFilter = rowFilter;
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

  public double sparseOffset(){return 0;}

  public boolean handlesSparseData(){return false;}
  abstract protected void processRow(Row r);

  @Override
  public void map(Chunk[] chks) {
    if(_jobKey != null && (DKV.get(_jobKey) == null || !Job.isRunning(_jobKey)))
      throw new Job.JobCancelledException();
    Chunk rowFilter = _rowFilter == null?null:_rowFilter.chunkForChunkIdx(chks[0].cidx());
    chunkInit();
    // compute
    if(_sparse) {
      for(Row r:_dinfo.extractSparseRows(chks, sparseOffset()))
        if(rowFilter == null || rowFilter.at8((int)(r.rid - chks[0].start())) == 0)
          processRow(r);
    } else {
      Row row = _dinfo.newDenseRow();
      for(int r = 0 ; r < chks[0]._len; ++r)
        if(rowFilter == null || rowFilter.at8(r) == 0)
          processRow(_dinfo.extractDenseRow(chks, r, row));
    }
    chunkDone();
  }

}
