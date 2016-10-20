package water.fvec;

import water.parser.BufferedString;

/**
 * Created by tomas on 10/11/16.
 */
public class NewChunkAry extends ChunkAry<NewChunk> {
  public NewChunkAry(Vec v, int cidx, NewChunk[] cs, int[] ids) {
    super(v, cidx, cs, ids);
  }
  public final void addNA(int c){_cs[c].addNA();}
  public final void addNum(double d){addNum(0,d);}
  public final void addNum(int c, double d){_cs[c].addNum(d);}
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
}
