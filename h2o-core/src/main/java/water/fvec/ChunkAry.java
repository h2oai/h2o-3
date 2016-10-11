package water.fvec;

import water.Futures;
import water.Iced;
import water.parser.BufferedString;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Created by tomas on 10/5/16.
 */
public class ChunkAry extends Iced {
  public final Vec _v;
  public final long _start;
  public final int _numRows;
  public final int _numCols;
  public final int _cidx;

  public ChunkAry(Vec v,int cidx, Chunk [] cs, int [] ids){
    _v = v;
    _cidx = cidx;
    _start = _v.chunk2StartElem(cidx);
    _numRows = (int)(_v.chunk2StartElem(cidx+1) - _start);
    _numCols = v.numCols();
    _cs = cs;
    _ids = ids;
  }

  public long start(){return _v.chunk2StartElem(_cidx);}

  public void close(){close(new Futures()).blockForPending();}
  public Futures close(Futures fs){
    if(_changedCols == null || _changedCols.size() == 0)
      return fs;
    for(int i = 0; i < _cs.length; ++i)
      _cs[i] = _cs[i].compress();
    return _v.closeChunk(_cidx,this,fs);
  }

  ArrayUtils.IntAry _changedCols;
  Chunk [] _cs;
  int [] _ids;

  private boolean isSparse(){return _ids != null;}
  public Chunk getChunk(int c){return _cs[c];}
  public Chunk[] getChunks(){return _cs;}

  private void setWrite(int j){
    if(_changedCols == null) _changedCols = new ArrayUtils.IntAry();
    _changedCols.add(j);
  }

  public double set(int i, int j, double d){
    setWrite(j);
    if(!_cs[j].set_impl(i,d)){
      _cs[j] = _cs[j].inflate_impl(new NewChunk());
      _cs[j].set_impl(i,d);
    }
    return d;
  }

  public long set(int i, int j, long l){
    setWrite(j);
    if(!_cs[j].set_impl(i,l)){
      _cs[j] = _cs[j].inflate_impl(new NewChunk());
      _cs[j].set_impl(i,l);
    }
    return l;
  }

  public double atd(int i, int j){
    if(_ids != null){
      j = Arrays.binarySearch(_ids,j);
      if(j < 0) return 0;
    }
    return _cs[j].atd_impl(i);
  }

  public int chunkRelativeOffset(long globalRowId){
    long start = start();
    long x = globalRowId - (start>0 ? start : 0);
    if( 0 <= x && x < _numRows) return(int)x;
    throw new ArrayIndexOutOfBoundsException(""+start+" <= "+globalRowId+" < "+(start+ _numRows));
  }

  public final long at16l(int i, int j){
    if(j < 0 || j > _numCols)
      throw new ArrayIndexOutOfBoundsException(j);
    if(_ids != null){
      j = Arrays.binarySearch(_ids,j);
      if(j < 0) throw new IllegalArgumentException("not a UUId chunk"); // UUID chunks should not be 0-sparse
    }
    return _cs[j].at16l_impl(i);
  }

  public final long at16h(int i, int j){
    if(j < 0 || j > _numCols)
      throw new ArrayIndexOutOfBoundsException(j);
    if(_ids != null){
      j = Arrays.binarySearch(_ids,j);
      if(j < 0) throw new IllegalArgumentException("not a UUId chunk"); // UUID chunks should not be 0-sparse
    }
    return _cs[j].at16h_impl(i);
  }


  public boolean isNA(int i, int j){
    if(_ids != null){
      j = Arrays.binarySearch(_ids,j);
      if(j < 0) return false;
    }
    return _cs[j].isNA_impl(i);
  }

  public long at8(int i, int j){
    if(_ids != null){
      j = Arrays.binarySearch(_ids,j);
      if(j < 0) return 0;
    }
    return _cs[j].at8_impl(i);
  }

  public int at4(int i, int j){
    if(_ids != null){
      j = Arrays.binarySearch(_ids,j);
      if(j < 0) return 0;
    }
    return _cs[j].at4_impl(i);
  }

  public BufferedString atStr(BufferedString str,int i, int j){
    if(_ids != null){
      j = Arrays.binarySearch(_ids,j);
      if(j < 0) throw new ArrayIndexOutOfBoundsException(j);
    }
    return _cs[j].atStr_impl(str,i);
  }

  public String set(int i, int j, String str){
    if(_ids != null){
      j = Arrays.binarySearch(_ids,j);
      if(j < 0) throw new ArrayIndexOutOfBoundsException(j);
    }
    return _cs[j].set(i,str);
  }

  private Chunk getOrMakeSparseChunk(int j){
    int j2 = Arrays.binarySearch(_ids,j);
    if(j2 > j) return _cs[j2];
    j2 = -j2 - 1;
    int n = _cs.length;
    _cs = Arrays.copyOf(_cs,n+1);
    _ids = Arrays.copyOf(_ids,n+1);
    for(int i = n; i > j2; --i) {
      _cs[i + 1] = _cs[i];
      _ids[i+1] = _ids[i+1];
    }
    _ids[j2] = j;
    return _cs[j2] = new C0DChunk(0,_numRows);
  }

  public void setNA(int i, int j){
    Chunk c = _ids != null?getOrMakeSparseChunk(j):_cs[j];
    c.setNA_impl(i);
  }

  public int[] changedCols() {
    return _changedCols == null?new int[0]:_changedCols.toArray();
  }
}
