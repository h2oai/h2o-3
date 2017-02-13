package water.fvec;

import water.Futures;
import water.H2O;
import water.parser.BufferedString;

import java.util.Arrays;

/**
 * Created by tomas on 10/11/16.
 */
public class NewChunkAry extends ChunkAry<NewChunk> {
  final Vec _av;
  public NewChunkAry(Vec v, int cidx, NewChunk[] cs) {
    super(cidx,-1,cs);
    _av = v;
  }
  public final void addNA(int c){_cs[c].addNA();}
  public final void addNum(double d){addNum(0,d);}
  public final void addNum(int c, double d){
    _cs[c].addNum(d);
  }
  public final void addNum(long l, int x){addNum(0,l,x);}
  public final void addNum(int c, long l, int x){_cs[c].addNum(l,x);}
  public final void addInteger(long l){addNum(0,l,0);}
  public final void addInteger(int c, long l){addNum(c,l,0);}
  public final void addInteger(double d){ addInteger(0,d);}
  public final void addUUID(int c, long lo, long hi){
    _cs[c].addUUID(lo,hi);
  }
  public final void addInteger(int c, double d){
    if(Double.isNaN(d)) addNA(c);
    else {
      long l = (long)d;
      if((double)l != d)
        throw new IllegalArgumentException("adding float as integer");
      addInteger(c,l);
    }
  }
  public final void addStr(int c, BufferedString s){_cs[c].addStr(s);}
  public final void addStr(int c, String s){_cs[c].addStr(s);}

  public void addZeros(int c, int skipped) {
    _cs[c].addZeros(skipped);
  }
  public void addNAs(int c, int skipped) {
    _cs[c].addNAs(skipped);
  }

  public void setDoubles(int c, double[] vals) {
    _cs[c].setDoubles(vals);
  }

  public int len(int c) {return _cs[c]._len;}

  public void addNumCols(int n){
    int oldN = _cs.length;
    _cs = Arrays.copyOf(_cs,oldN+n);
    for(int i = oldN; i < _cs.length; ++i)
      _cs[i] = new NewChunk(Vec.T_NUM);
  }

  public void addVal(int i, DVal inflated) {
    switch(inflated._t){
      case N: addNum(i,inflated._m,inflated._e); break;
      case D: addNum(i,inflated._d); break;
      case S: addStr(i,inflated._str); break;
      case U:
        throw H2O.unimpl();
    }
  }

  @Override
  public Futures close(Futures fs){
    Chunk[] cs = new Chunk[_cs.length];
    int len = _cs[0].len();
    for(int i = 0; i < _cs.length; ++i)
      cs[i] = _cs[i].compress();
    _cs = null;
    DBlock db = _numCols == 1?cs[0]:new DBlock.MultiChunkBlock(cs);
    return _av.closeChunk(_cidx,len,db,fs);
  }
  public void addInflated(int c, DVal inflated) {
    _cs[c].addInflated(inflated);
  }

  public int len() {
    return _cs[0]._len;
  }
}
