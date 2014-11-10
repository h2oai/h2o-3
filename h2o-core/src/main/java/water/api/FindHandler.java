package water.api;

import water.H2O;
import water.Iced;
import water.MRTask;
import water.api.FindHandler.FindPojo;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.Arrays;

class
        FindHandler extends Handler<FindPojo,FindV2> {

  protected static final class FindPojo extends Iced {
    // Inputs
    public Frame _fr;                    // Thing to inspect
    public long _row;                    // Row to start from
    public String _val;                  // Thing to search for

    // Outputs
    public long _prev, _next;            // Nearest matching row in either direction
  }

  // Running all in exec2, no need for backgrounding on F/J threads
  @Override public void compute2() { throw H2O.fail(); }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FindV2 find(int version, FindPojo find) {
    // Convert the search string into a column-specific flavor
    Vec[] vecs = find._fr.vecs();
    double ds[] = new double[vecs.length];
    for( int i=0; i<vecs.length; i++ ) {
      if( vecs[i].isEnum() ) {
        int idx = ArrayUtils.find(vecs[i].domain(),find._val);
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
          ds[i] = find._val==null ? Double.NaN : Double.parseDouble(find._val);
        } catch( NumberFormatException e ) {
          if( vecs.length==1 ) throw new IllegalArgumentException("Not a number: "+find._val);
          ds[i] = Double.longBitsToDouble(0xcafebabe); // Do not match
        }
      }
    }

    Find f = new Find(find._row,ds).doAll(find._fr);
    find._prev = f._prev;
    find._next = f._next==Long.MAX_VALUE ? -1 : f._next;
    return schema(version).fillFromImpl(find);
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
  @Override protected FindV2 schema(int version) { return new FindV2(); }
}
