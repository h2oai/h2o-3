package hex.coxph;

import hex.DataInfo;
import water.DKV;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;

abstract class CPHBaseTask<T extends CPHBaseTask<T>> extends MRTask<T> {

  private Key<DataInfo> _dinfoKey;

  protected transient DataInfo _dinfo;

  CPHBaseTask(DataInfo dinfo) {
    _dinfoKey = dinfo._key;
  }

  @Override
  public void map(Chunk[] cs) {
    chunkInit();
    DataInfo.Row row = _dinfo.newDenseRow();
    for (int r = 0; r < cs[0]._len; r++) {
      row = _dinfo.extractDenseRow(cs, r, row);
      if (row.isBad() || row.weight == 0)
        continue;
      processRow(row);
    }
  }

  abstract protected void processRow(DataInfo.Row row);

  @Override
  protected void setupLocal(){
    _dinfo = DKV.get(_dinfoKey).get();
  }

  protected void chunkInit() {}

}
