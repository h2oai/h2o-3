package water.api;

import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.Arrays;

class FindHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FindV2 find(int version, FindV2 find) {
    Frame frame = find.key.createAndFillImpl();
    // Peel out an optional column; restrict to this column
    if( find.column != null ) {
      Vec vec = frame.vec(find.column);
      if( vec==null ) throw new IllegalArgumentException("Column "+find.column+" not found in frame "+find.key);
      find.key = new FrameV2(new Frame(new String[]{find.column}, new Vec[]{vec}));
    }

    // Convert the search string into a column-specific flavor
    Vec[] vecs = frame.vecs();
    double ds[] = new double[vecs.length];
    for( int i=0; i<vecs.length; i++ ) {
      if( vecs[i].isEnum() ) {
        int idx = ArrayUtils.find(vecs[i].domain(),find.match);
        if( idx==-1 && vecs.length==1 ) throw new IllegalArgumentException("Not one of "+Arrays.toString(vecs[i].domain()));
        ds[i] = idx;
      } else if( vecs[i].isUUID() ) {
        throw H2O.unimpl();
      } else if( vecs[i].isString() ) {
        throw H2O.unimpl();
      } else if( vecs[i].isTime() ) {
        throw H2O.unimpl();
      } else {
        try {
          ds[i] = find.match==null ? Double.NaN : Double.parseDouble(find.match);
        } catch( NumberFormatException e ) {
          if( vecs.length==1 ) throw new IllegalArgumentException("Not a number: "+find.match);
          ds[i] = Double.longBitsToDouble(0xcafebabe); // Do not match
        }
      }
    }

    Find f = new Find(find.row,ds).doAll(frame);
    find.prev = f._prev;
    find.next = f._next==Long.MAX_VALUE ? -1 : f._next;
    return find;
  }

  private static class Find extends MRTask<Find> {
    final long _row;
    final double[] _ds;
    long _prev, _next;
    Find( long row, double[] ds ) { _row = row; _ds = ds; _prev = -1; _next = Long.MAX_VALUE; }
    @Override public void map( Chunk cs[] ) {
      for( int col = 0; col<cs.length; col++ ) {
        Chunk C = cs[col];
        for( int row=0; row<C._len; row++ ) {
          if( C.at0(row) == _ds[col] || (C.isNA0(row) && Double.isNaN(_ds[col])) ) {
            long r = C.start()+row;
            if( r < _row ) { if( r > _prev ) _prev = r; }
            else if( r > _row ) { if( r < _next ) _next = r; }
          }
        }
      }
    }
    @Override public void reduce( Find f ) {
      if( _prev < f._prev ) _prev = f._prev;
      if( _next > f._next ) _next = f._next;
    }
  }


  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
}
