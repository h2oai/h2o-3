package hex.deepwater;

import hex.DataInfo;
import water.*;
import water.fvec.C4FChunk;
import water.fvec.C8DChunk;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.util.UnsafeUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;


class DeepWaterDatasetIterator extends DeepWaterIterator {

  DeepWaterDatasetIterator(ArrayList<Integer> rows, ArrayList<Float> labels, DataInfo dinfo, int batch_size, boolean cache) throws IOException {
    super(batch_size, dinfo.fullN(), cache);
    _rows_lst = rows;
    _label_lst = labels;
    _dinfo = dinfo;
  }

  // Row-based storage, dense - direct mapping to float[]
  static class IcedRow extends Iced<IcedRow> {
    int size() { return _data.length; }
    float getVal(int i) { return _data[i]; }
    void insertValuesIntoArray(float[] vals, int offset) {
      for (int i=0; i<_data.length; ++i) {
        vals[offset+i] = _data[i];
      }
    }
    public IcedRow() {}
    IcedRow(float[] fs) {
      _data = fs;
    }
    private float[] _data;
  }

  static class FrameDataConverter extends H2O.H2OCountedCompleter<FrameDataConverter> {
    int _index;
    int _globalIndex;
    DataInfo _dinfo;
    float _label;
    float[] _destData;
    float[] _destLabel;
    boolean _cache;
    FrameDataConverter(int index, int globalIndex, DataInfo dinfo, float label, float[] destData, float[] destLabel, boolean cache) {
      _index=index;
      _globalIndex=globalIndex;
      _dinfo = dinfo;
      _label = label;
      _destData=destData;
      _destLabel=destLabel;
      _cache = cache;
    }

    @Override
    public void compute2() {
      _destLabel[_index] = _label;
      final int start=_index*_dinfo.fullN();
      Key rowKey = Key.make(_dinfo._adaptedFrame._key + "_" + _dinfo.fullN() + "_row_" + Integer.toString(_globalIndex) + "_" + DeepWaterModel.CACHE_MARKER);
      boolean status = false;
      if (_cache) {
        IcedRow icedRow = DKV.getGet(rowKey);
        if (icedRow != null) {
          icedRow.insertValuesIntoArray(_destData, start);
          status = true;
        }
      }
      if (!status) { //only do this the first time
        DataInfo.Row row = _dinfo.newDenseRow();
        Chunk[] chks = new Chunk[_dinfo._adaptedFrame.numCols()];
        for (int i=0;i<chks.length;++i)
          chks[i] = _dinfo._adaptedFrame.vec(i).chunkForRow(_globalIndex);
        _dinfo.extractDenseRow(chks, _globalIndex-(int)chks[0].start(), row);
        for (int i = 0; i< _dinfo.fullN(); ++i)
          _destData[start+i] = (float)row.get(i);
//        System.err.println("Row: " + _dinfo._adaptedFrame.vec(0).domain()[(int)_dinfo._adaptedFrame.vec(0).at8(_globalIndex)] + " -> " + Arrays.toString(_destData));
//        System.err.println(Arrays.toString(Arrays.copyOfRange(_destData, start, start + _dinfo.fullN())));
        if (_cache) {
          Value v = new Value(rowKey, new IcedRow(Arrays.copyOfRange(_destData, start, start + _dinfo.fullN())));
          DKV.put(rowKey, v);
          v.freeMem();
        }
      }
      tryComplete();
    }
  }

  public boolean Next(Futures fs) throws IOException {
    if (_start_index < _rows_lst.size()) {
      if (_start_index + _batch_size > _rows_lst.size())
        _start_index = _rows_lst.size() - _batch_size;
      // Multi-Threaded data preparation
      for (int i = 0; i < _batch_size; i++)
        fs.add(H2O.submitTask(new FrameDataConverter(i, _rows_lst.get(_start_index+i), _dinfo, _label_lst==null?-1:_label_lst.get(_start_index + i), _data[which()], _label[which()], _cache)));
      fs.blockForPending();
      flip();
      _start_index += _batch_size;
      return true;
    } else {
      return false;
    }
  }

  final private ArrayList<Integer> _rows_lst;
  final private ArrayList<Float> _label_lst;
  final private DataInfo _dinfo;
}
