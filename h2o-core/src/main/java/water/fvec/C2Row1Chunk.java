package water.fvec;

/**
 * Created by tomas on 2/13/17.
 * Chunk which has only one non-zero integer value.
 * Created to reduce overhead of really sparse datasets.
 *
 */
public class C2Row1Chunk extends Chunk{
  final int _row0;
  final int _row1;

  public C2Row1Chunk(int row0, int row1) {_row0 = row0; _row1 = row1;}

  @Override
  public Chunk deepCopy() {return new C2Row1Chunk(_row0,_row1);}

  @Override
  public double atd(int idx) {return (idx == _row0 || idx == _row1)?1:0;}

  @Override
  public long at8(int idx) {return (idx == _row0 || idx == _row1)?1:0;}


  @Override
  public boolean isNA(int idx) {return false;}

  @Override
  public DVal getInflated(int i, DVal v) {
    v._missing = false;
    v._t = DVal.type.N;
    v._m = (i == _row0 || i == _row1)?1:0;;
    v._e = 0;
    return v;
  }

  @Override
  public int asSparseDoubles(double [] vals, int [] ids, double NA){
    vals[0] = 1;
    ids[0] = _row0;
    if(_row1 != -1) {
      vals[1] = 1;
      ids[1] = _row1;
    }
    return 1;
  }

  @Override
  public final NewChunk add2Chunk(NewChunk nc, int from, int to){
    if(to <= _row0) {
      nc.addZeros(to - from);
      return nc;
    }
    if(_row1 != -1 && _row1 < from || _row1 == -1 && _row0 < from){
      nc.addZeros(to - from);
      return nc;
    }
    int x = from;
    if(_row0 >= from && _row0 < to) {
      nc.addZeros(_row0-from);
      nc.addNum(1,0);
      x = _row0+1;
    }
    if(_row1 >= from && _row1 < to) {
      nc.addZeros(_row1-x);
      nc.addNum(1,0);
      x = _row1+1;
    }
    nc.addZeros(to-x);
    return nc;
  }

  @Override
  public final NewChunk add2Chunk(NewChunk nc, int [] ids){
    int zs = 0;
    for(int i = 0; i < ids.length; i++){
      if(ids[i] == _row0){
        nc.addZeros(zs);
        nc.addNum(1,0);
        zs = 0;
      } else if(ids[i] == _row1){
        nc.addZeros(zs);
        nc.addNum(1,0);
        zs = 0;
      } else zs++;
    }
    nc.addZeros(zs);
    return nc;
  }

  @Override public boolean isSparseZero(){return true;}
  @Override
  public int len() {
    return _row1 != -1?2:1;
  }
  @Override public long byteSize(){return super.byteSize()+8;}

  public final SparseNum nextNZ(SparseNum sv){
    if(sv._off <= 0){
      sv._id = _row0;
      sv._val = 1;
      sv._off = 1;
    } else if(_row1 == -1 || sv._id == _row1) {
      sv._id = sv._len;
      sv._val = Double.NaN;
    } else {
      sv._id = _row1;
    }
    return sv;
  }
}
