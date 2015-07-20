package water.currents;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import water.AutoBuffer;
import water.MRTask;
import water.fvec.*;
import water.nbhm.NonBlockingHashMapLong;
import water.util.ArrayUtils;

/** Variance between columns of a frame */
class ASTTable extends ASTPrim {
  @Override int nargs() { return -1; } // (table X)  or (table X Y)
  @Override public String str() { return "table"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr1 = stk.track(asts[1].exec(env)).getFrame();
    Frame fr2 = asts.length==3 ? stk.track(asts[2].exec(env)).getFrame() : null;
    int ncols = fr1.numCols() + (fr2==null ? 0 : fr2.numCols());
    Vec vec1 = fr1.vec(0);

    Val res = fast_table(vec1,ncols,fr1._names[0]);
    if( res != null ) return res;

    if( !(asts.length == 2 || asts.length == 3) || ncols > 2 )
      throw new IllegalArgumentException("table expects one or two columns");

    Vec vec2 = fr1.numCols()==2 ? fr1.vec(1) : fr2 != null ? fr2.vec(0) : null;
    return slow_table(vec1,vec2);
  }

  // -------------------------------------------------------------------------
  // Fast-path for 1 integer column
  private ValFrame fast_table( Vec v1, int ncols, String colname ) {
    if( ncols != 1 || !v1.isInt() ) return null;
    long spanl = (long)v1.max()-(long)v1.min()+1;
    if( spanl > 100000 ) return null; // Cap at decent array size, for performance

    // First fast-pass counting
    final long cnts[] = new FastCnt((long)v1.min(),(int)spanl).doAll(v1)._cnts;

    // Second pass to build the result frame, skipping zeros
    Vec dataLayoutVec = Vec.makeCon(0, cnts.length);
    Frame fr = new MRTask() {
        @Override public void map(Chunk cs[], NewChunk nc0, NewChunk nc1) {
          final Chunk c = cs[0];
          for( int i = 0; i < c._len; ++i ) {
            int idx = (int) (i + c.start());
            if( cnts[idx] > 0 ) {
              nc0.addNum(idx);
              nc1.addNum(cnts[idx]);
            }
          }
        }
      }.doAll(2, dataLayoutVec).outputFrame(new String[]{colname, "Count"},
                                            new String[][]{v1.domain(),null});
    dataLayoutVec.remove();
    return new ValFrame(fr);
  }

  // Fast-pass for counting unique integers in a span
  private static class FastCnt extends MRTask<FastCnt> {
    final long _min;  final int _span;
    long _cnts[];
    FastCnt( long min, int span ) { _min = min; _span = span; }
    @Override public void map( Chunk c ) {
      _cnts = new long[(int)_span];
      for( int i=0; i<c._len; i++ ) 
        if( !c.isNA(i) ) 
          _cnts[(int)(c.at8(i)-_min)]++;
    }
    @Override public void reduce( FastCnt fc ) { ArrayUtils.add(_cnts,fc._cnts); }
  }

  // -------------------------------------------------------------------------
  // Count unique combos in 1 or 2 columns, where the values are not integers,
  // or cover a very large span.
  private ValFrame slow_table( Vec v1, Vec v2 ) {
    // For simplicity, repeat v1 if v2 is missing; this will end up filling in
    // only the diagonal of a 2-D array (in what is otherwise a 1-D array).
    // This should be nearly the same cost as a 1-D array, since everything is
    // sparsely filled in.
    Vec vx = v2==null ? v1 : v2;

    // Slow-pass group counting, very sparse hashtables.  Note that Vec v2 is
    // used as the left-most arg, or OUTER dimension - which will be columns in
    // the final result.
    SlowCnt sc = new SlowCnt().doAll(vx,v1);

    // Get the column headers as sorted doubles
    double dcols[] = collectDomain(sc._col0s);
    
    // Need the row headers as sorted doubles also, but these are scattered
    // throughout the nested tables.  Fold 'em into 1 table.
    NonBlockingHashMapLong rows = new NonBlockingHashMapLong();
    for( NonBlockingHashMapLong.IteratorLong i = iter(sc._col0s); i.hasNext(); )
      rows.putAll(sc._col0s.get(i.nextLong()));
    double drows[] = collectDomain(rows);

    // Now walk the columns one by one, building a Vec per column, building a
    // Frame result.


    throw water.H2O.unimpl();
  }

  // Collect the unique longs from this NBHML, convert to doubles and return
  // them as a sorted double[].
  private static double[] collectDomain( NonBlockingHashMapLong ls ) {
    int sz = ls.size();         // Uniques
    double ds[] = new double[sz];
    int x=0;
    for( NonBlockingHashMapLong.IteratorLong i = iter(ls); i.hasNext(); )
      ds[x++] = Double.longBitsToDouble(i.nextLong());
    Arrays.sort(ds);
    return ds;
  }


  private static NonBlockingHashMapLong.IteratorLong iter(NonBlockingHashMapLong nbhml) { 
    return (NonBlockingHashMapLong.IteratorLong)nbhml.keySet().iterator();  
  }

  // Implementation is a double-dimension NBHML.  Each dimension key is the raw
  // long bits of the double column.  Bottoms out in an AtomicLong.
  private static class SlowCnt extends MRTask<SlowCnt> {
    transient NonBlockingHashMapLong<NonBlockingHashMapLong<AtomicLong>> _col0s;
    @Override public void setupLocal() {  _col0s = new NonBlockingHashMapLong();  }

    @Override public void map( Chunk c0, Chunk c1 ) {
      for( int i=0; i<c0._len; i++ ) {

        double d0 = c0.atd(i);
        if( Double.isNaN(d0) ) continue;
        long l0 = Double.doubleToRawLongBits(d0);

        double d1 = c1.atd(i);
        if( Double.isNaN(d1) ) continue;
        long l1 = Double.doubleToRawLongBits(d1);

        // Atomically fetch/create nested NBHM
        NonBlockingHashMapLong<AtomicLong> col1s = _col0s.get(l0);
        if( col1s == null ) {   // Speed filter pre-filled entries
          col1s = new NonBlockingHashMapLong();
          NonBlockingHashMapLong<AtomicLong> old = _col0s.putIfAbsent(l0,col1s);
          if( old != null ) col1s = old; // Lost race, use old value
        }
        
        // Atomically fetch/create nested AtomicLong
        AtomicLong cnt = col1s.get(l1);
        if( cnt == null ) {   // Speed filter pre-filled entries
          cnt = new AtomicLong();
          AtomicLong old = col1s.putIfAbsent(l1,cnt);
          if( old != null ) cnt = old; // Lost race, use old value
        }

        // Atomically bump counter
        cnt.incrementAndGet();
      }
    }

    @Override public void reduce( SlowCnt sc ) {
      if( _col0s == sc._col0s ) return;
      throw water.H2O.unimpl();
    }

    @Override public AutoBuffer write_impl(AutoBuffer ab) {
      //int len=_nd.length;
      //ab.put4(len);
      //for( NonBlockingHashSet a_nd : _nd ) {
      //  if( a_nd==null ) {
      //    ab.put4(0);
      //    continue;
      //  }
      //  int s = a_nd.size();
      //  ab.put4(s);
      //  for (Object d : a_nd) ab.put8d((double)d);
      //}
      //return ab;
      throw water.H2O.unimpl();
    }
    @Override public SlowCnt read_impl(AutoBuffer ab) {
      //int len = ab.get4();
      //_n=len;
      //_nd=new NonBlockingHashSet[len];
      //for(int i=0;i<len;++i) {
      //  _nd[i] = new NonBlockingHashSet<>();
      //  int s = ab.get4();
      //  if( s==0 ) continue;
      //  for(int j=0;j<s;++j) _nd[i].add(ab.get8d());
      //}
      //return this;
      throw water.H2O.unimpl();
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder();
      for( NonBlockingHashMapLong.IteratorLong i = iter(_col0s); i.hasNext(); ) {
        long l = i.nextLong();
        double d = Double.longBitsToDouble(l);
        sb.append(d).append(": {");
        NonBlockingHashMapLong<AtomicLong> col1s = _col0s.get(l);
        for( NonBlockingHashMapLong.IteratorLong j = iter(col1s); j.hasNext(); ) {
          long l2 = j.nextLong();
          double d2 = Double.longBitsToDouble(l2);
          AtomicLong al = col1s.get(l2);
          sb.append(d2).append(": ").append(al.get()).append(", ");
        }
        sb.append("}\n");
      }
      return sb.toString();
    }
  }
}
