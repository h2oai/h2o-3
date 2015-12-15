package ai.h2o.automl;

import water.fvec.Frame;
import water.fvec.Vec;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Cache common questions asked upon the frame.
 */
public class FrameMeta {
  private final Frame _fr;
  private final int _response;
  ColMeta[] _cols;

  // cached things
  private int[] _ignoredCols;

  public FrameMeta(Frame fr, int response) {
    _fr=fr;
    _response=response;
  }

  public int[] ignoredCols() {  // publishes private field
    if( _ignoredCols==null ) {
      ArrayList<Integer> cols = new ArrayList<>();
      for(ColMeta c: _cols)
        if( c._ignored ) cols.add(c._idx);
      _ignoredCols=new int[cols.size()];
      for(int i=0;i<cols.size();++i)
        _ignoredCols[i]=cols.get(i);
      Arrays.sort(_ignoredCols);
    }
    return _ignoredCols;
  }

  public Vec response() { return _fr.vec(_response); }
  public ColMeta responseMeta() { return _cols[_response]; }

  public void computeFrameMetaPass1() {

  }
}
