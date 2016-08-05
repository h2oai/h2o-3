package water.parser;

import water.Futures;
import water.fvec.AppendableVec;
import water.fvec.ChunkBlock;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Arrays;

// --------------------------------------------------------
public class SVMLightFVecParseWriter extends FVecParseWriter {
  protected final Vec.VectorGroup _vg;
  int _vecIdStart;



  public SVMLightFVecParseWriter(Vec.VectorGroup vg, int vecIdStart, int cidx, AppendableVec av){
    super(vg, cidx, null, null, av);
    _vg = vg;
    _vecIdStart = vecIdStart;
    _nvs = new NewChunk[av.numCols()];
    for(int i = 0; i < _nvs.length; ++i)
      _nvs[i] = new NewChunk(true);
    _col = 0;
  }

  @Override public void addNumCol(int colIdx, long number, int exp) {
    assert colIdx >= _col;
    if(colIdx >= _vec.numCols()) addColumns(colIdx+1);
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
    for(NewChunk nc:_nvs) {
      nc.addZeros((int) _nLines - nc._len);
      assert nc._len == _nLines:"incompatible number of lines after parsing chunk, " + _nLines + " != " + nc._len;
    }
    _nCols = _nvs.length;
    return super.close(fs);
  }
  private void addColumns(int newColCnt){
    int oldColCnt = _vec.numCols();
    if(newColCnt > oldColCnt){
      _nvs   = Arrays.copyOf(_nvs, newColCnt);
      _vec.setNCols(newColCnt);
      for(int i = oldColCnt; i < newColCnt; ++i)
        _nvs[i] = new NewChunk(true);
      _nCols = newColCnt;
    }
  }
}
