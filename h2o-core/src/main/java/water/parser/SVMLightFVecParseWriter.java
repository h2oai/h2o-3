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

  public SVMLightFVecParseWriter(Vec.VectorGroup vg, int vecIdStart, int cidx, int chunkSize, AppendableVec avs){
    super(vg, cidx, null, null, chunkSize, avs);
    _vg = vg;
    _vecIdStart = vecIdStart;
    _col = 0;
  }

  @Override public void addNumCol(int colIdx, long number, int exp) {
    assert colIdx >= _col;
    if(colIdx >= _vecs.numCols()) addColumns(colIdx+1);
    _nvs.addZeros(colIdx,(_nLines - _nvs.len(colIdx)));
    _nvs.addNum(colIdx,number, exp);
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
      for (int i = 0; i < _nvs._numCols; ++i)
        _nvs.addZeros(i, _nLines - _nvs.len(i));
      _nCols = _nvs._numCols;
    }
    return super.close(fs);
  }
  private void addColumns(int newColCnt){
    int oldColCnt = _vecs.numCols();
    if(newColCnt > oldColCnt){
      _nvs.addNumCols(newColCnt-oldColCnt,true);
      _nCols = newColCnt;
    }
  }
}
