package water.parser;

import water.Futures;
import water.fvec.AppendableVec;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Arrays;

// --------------------------------------------------------
public class SVMLightFVecParseWriter extends FVecParseWriter {
  protected final Vec.VectorGroup _vg;
  int _vecIdStart;

  public SVMLightFVecParseWriter(Vec.VectorGroup vg, int vecIdStart, int cidx, int chunkSize, AppendableVec[] avs, int[] parse_columns_indices){
    super(vg, cidx, null, null, chunkSize, avs, parse_columns_indices);
    _vg = vg;
    _vecIdStart = vecIdStart;
    int numParseCols = parse_columns_indices.length;
    _nvs = new NewChunk[numParseCols];
    for(int i = 0; i < numParseCols; ++i)
      _nvs[i] = new NewChunk(_vecs[_parse_columns_indices[i]], _cidx, true);
    _col = 0;
  }

  @Override public void addNumCol(int colIdx, long number, int exp) {
    assert colIdx >= _col;
    if(colIdx >= _vecs.length) addColumns(colIdx+1);
    _nvs[colIdx].addZeros((int)_nLines - _nvs[colIdx]._len);
    _nvs[colIdx].addNum(number, exp);
    _col = colIdx+1;
  }
  @Override
  public void newLine() {
    ++_nLines;
    _col = 0;
  }
  @Override public void addStrCol(int idx, BufferedString str){addInvalidCol(idx);}
  @Override public boolean isString(int idx){return false;}
  @Override public FVecParseWriter close(Futures fs) {
    if (_nvs != null) {
    for(NewChunk nc:_nvs) {
      nc.addZeros((int) _nLines - nc._len);
      assert nc._len == _nLines:"incompatible number of lines after parsing chunk, " + _nLines + " != " + nc._len;
    }
    }
    _nCols = _nvs == null ? 0 : _nvs.length;
    return super.close(fs);
  }
  private void addColumns(int newColCnt){
    int oldColCnt = _vecs.length;
    if(newColCnt > oldColCnt){
      _nvs   = Arrays.copyOf(_nvs, newColCnt);
      _vecs  = Arrays.copyOf(_vecs  , newColCnt);
      for(int i = oldColCnt; i < newColCnt; ++i) {
        _vecs[i] = new AppendableVec(_vg.vecKey(i+_vecIdStart),_vecs[0]._tmp_espc,Vec.T_NUM,_vecs[0]._chunkOff);
        _nvs[i] = new NewChunk(_vecs[i], _cidx, true);
      }
      _nCols = newColCnt;
    }
  }
}
