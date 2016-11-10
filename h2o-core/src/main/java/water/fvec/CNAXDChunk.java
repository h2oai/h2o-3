package water.fvec;


import water.H2O;
import water.util.UnsafeUtils;

import java.util.Iterator;

//NA sparse double chunk
public class CNAXDChunk extends CXDChunk {
  protected CNAXDChunk(int len, int valsz, byte [] buf){super(len,valsz,buf);}
  @Override public boolean isSparseNA(){return true;}
  @Override public boolean isSparseZero(){return false;}
  @Override public int nextNZ(int rid){ return rid + 1;}
  @Override public int nextNNA(int rid){ return super.nextNZ(rid);}


}
