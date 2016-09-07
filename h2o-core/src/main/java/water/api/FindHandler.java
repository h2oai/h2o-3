package water.api;

import water.H2O;
import water.MRTask;
import water.api.schemas3.FindV3;
import water.api.schemas3.FrameV3;
import water.exceptions.H2OColumnNotFoundArgumentException;
import water.exceptions.H2OCategoricalLevelNotFoundArgumentException;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.VecAry;
import water.util.ArrayUtils;
import water.util.IcedHashMap;

class FindHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public FindV3 find(int version, FindV3 find) {
    Frame frame = find.key._fr;
    // Peel out an optional column; restrict to this column
    if( find.column != null ) {
      VecAry vec = frame.vecs().getVecs(frame.find(find.column));
      if( vec==null ) throw new H2OColumnNotFoundArgumentException("column", frame, find.column);
      find.key = new FrameV3(new Frame(new String[]{find.column},vec));
    }

    // Convert the search string into a column-specific flavor
    VecAry vecs = frame.vecs();
    double ds[] = new double[vecs.len()];
    for( int i=0; i<vecs.len(); i++ ) {
      if( vecs.isCategorical(i) ) {
        int idx = ArrayUtils.find(vecs.domain(i),find.match);
        if( idx==-1 && vecs.len()==1 ) throw new H2OCategoricalLevelNotFoundArgumentException("match", find.match, frame._key.toString(), frame.name(i));
        ds[i] = idx;
      } else if( vecs.isUUID(i) ) {
        throw H2O.unimpl();
      } else if( vecs.isString(i) ) {
        throw H2O.unimpl();
      } else if( vecs.isTime(i) ) {
        throw H2O.unimpl();
      } else {
        try {
          ds[i] = find.match==null ? Double.NaN : Double.parseDouble(find.match);
        } catch( NumberFormatException e ) {
          if( vecs.len()==1 ) {
            // There's only one Vec and it's a numeric Vec and our search string isn't a number
            IcedHashMap.IcedHashMapStringObject values = new IcedHashMap.IcedHashMapStringObject();
            String msg = "Frame: " + frame._key.toString() + " as only one column, it is numeric, and the find pattern is not numeric: " + find.match;
            values.put("frame_name", frame._key.toString());
            values.put("column_name", frame.name(i));
            values.put("pattern", find.match);
            throw new H2OIllegalArgumentException(msg, msg, values);
          }
          ds[i] = Double.longBitsToDouble(0xcafebabe); // Do not match
        }
      }
    }

    Find f = new Find(find.row,ds).doAll(frame.vecs());
    find.prev = f._prev;
    find.next = f._next==Long.MAX_VALUE ? -1 : f._next;
    return find;
  }

  private static class Find extends MRTask<Find> {
    final long _row;
    final double[] _ds;
    long _prev, _next;
    Find( long row, double[] ds ) { 
      super((byte)(H2O.GUI_PRIORITY - 2));
      _row = row; _ds = ds; _prev = -1; _next = Long.MAX_VALUE; 
    }
    @Override public void map( Chunk cs[] ) {
      for( int col = 0; col<cs.length; col++ ) {
        Chunk C = cs[col];
        for( int row=0; row<C._len; row++ ) {
          if( C.atd(row) == _ds[col] || (C.isNA(row) && Double.isNaN(_ds[col])) ) {
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
}
