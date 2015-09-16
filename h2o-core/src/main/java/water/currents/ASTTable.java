package water.currents;

import water.AutoBuffer;
import water.DKV;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.nbhm.NonBlockingHashMapLong;
import water.util.ArrayUtils;
import water.util.IcedHashMap;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

// TODO:  Define "table" in terms of "groupby"
// TODO: keep dense format for two-column comparison (like in previous version of Rapids)
// (table X Y) ==>
// (groupby (cbind X Y) [X Y] nrow TRUE)

/** Variance between columns of a frame */
class ASTTable extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"X", "Y"}; }
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
    return slow_table(vec1,vec2,fr1._names[0]);
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
      _cnts = new long[_span];
      for( int i=0; i<c._len; i++ ) 
        if( !c.isNA(i) ) 
          _cnts[(int)(c.at8(i)-_min)]++;
    }
    @Override public void reduce( FastCnt fc ) { ArrayUtils.add(_cnts,fc._cnts); }
  }

  // -------------------------------------------------------------------------
  // Count unique combos in 1 or 2 columns, where the values are not integers,
  // or cover a very large span.
  private ValFrame slow_table( Vec v1, Vec v2, String colname ) {
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

    // If this is the 1-column case (all counts on the diagonals), just build a
    // 1-d result.
    if( v2==null ) {
      Frame res = new Frame();
      Vec rowlabel = Vec.makeVec(dcols,Vec.VectorGroup.VG_LEN1.addVec());
      rowlabel.setDomain(v1.domain());
      res.add(colname,rowlabel);
      long cnts[] = new long[dcols.length];
      for( int col=0; col<dcols.length; col++ ) {
        long lkey = Double.doubleToRawLongBits(dcols[col]);
        NonBlockingHashMapLong<AtomicLong> colx = sc._col0s.get(lkey);
        AtomicLong al = colx.get(lkey);
        cnts[col] = al.get();
      }
      Vec vec = Vec.makeVec(cnts,null,Vec.VectorGroup.VG_LEN1.addVec());
      res.add("Counts",vec);
      return new ValFrame(res);
    }

    // 2-d table result.

    // Need the row headers as sorted doubles also, but these are scattered
    // throughout the nested tables.  Fold 'em into 1 table.
    NonBlockingHashMapLong<AtomicLong> rows = new NonBlockingHashMapLong<>();
    for( NonBlockingHashMapLong.IteratorLong i = iter(sc._col0s); i.hasNext(); )
      rows.putAll(sc._col0s.get(i.nextLong()));
    double drows[] = collectDomain(rows);

    // Now walk the columns one by one, building a Vec per column, building a
    // Frame result.  Rowlabel for first column.
    Frame res = new Frame();
    Vec rowlabel = Vec.makeVec(drows,Vec.VectorGroup.VG_LEN1.addVec());
    rowlabel.setDomain(v1.domain());
    res.add(colname,rowlabel);
    long cnts[] = new long[drows.length];
    for( int col=0; col<dcols.length; col++ ) {
      NonBlockingHashMapLong<AtomicLong> colx = sc._col0s.get(Double.doubleToRawLongBits(dcols[col]));
      for( int row = 0; row<drows.length; row++ ) {
        AtomicLong al = colx.get(Double.doubleToRawLongBits(drows[row]));
        cnts[row] = al==null ? 0 : al.get();
      }
      Vec vec = Vec.makeVec(cnts,null,Vec.VectorGroup.VG_LEN1.addVec());
      res.add(vx.isEnum() ? vx.domain()[col] : Double.toString(dcols[col]),vec);
    }

    return new ValFrame(res);
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
    @Override public void setupLocal() {  _col0s = new NonBlockingHashMapLong<>();  }

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
          col1s = new NonBlockingHashMapLong<>();
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
      if( _col0s == null ) return ab.put8(0);
      ab.put8(_col0s.size());
      for( long col0 : _col0s.keySetLong() ) {
        ab.put8(col0);
        NonBlockingHashMapLong<AtomicLong> col1s = _col0s.get(col0);
        ab.put8(col1s.size());
        for( long col1 : col1s.keySetLong() ) {
          ab.put8(col1);
          ab.put8(col1s.get(col1).get());
        }
      }
      return ab;
    }
    @Override public SlowCnt read_impl(AutoBuffer ab) {
      long len0 = ab.get8();
      if( len0 == 0 ) return this;
      _col0s = new NonBlockingHashMapLong<>();
      for( long i=0; i<len0; i++ ) {
        NonBlockingHashMapLong<AtomicLong> col1s = new NonBlockingHashMapLong<>();
        _col0s.put(ab.get8(),col1s);
        long len1 = ab.get8();
        for( long j=0; j<len1; j++ )
          col1s.put(ab.get8(),new AtomicLong(ab.get8()));
      }
      return this;
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


class ASTUnique extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"ary"}; }
  @Override int nargs() { return 1 + 1; }  // (unique col)

  @Override String str() { return "unique"; }

  @Override Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Vec v;
    if( fr.numCols()!=1 )
      throw new IllegalArgumentException("Unique applies to a single column only.");
    if( fr.anyVec().isEnum() ) {
      v = Vec.makeSeq(0, (long)fr.anyVec().domain().length, true);
      v.setDomain(fr.anyVec().domain());
      DKV.put(v);
    } else {
      UniqTask t = new UniqTask().doAll(fr);
      int nUniq = t._uniq.size();
      final ASTGroup.G[] uniq = t._uniq.keySet().toArray(new ASTGroup.G[nUniq]);
      v = Vec.makeZero(nUniq);
      new MRTask() {
        @Override
        public void map(Chunk c) {
          int start = (int) c.start();
          for (int i = 0; i < c._len; ++i) c.set(i, uniq[i + start]._gs[0]);
        }
      }.doAll(v);
    }
    return new ValFrame(new Frame(v));
  }

  private static class UniqTask extends MRTask<UniqTask> {
    IcedHashMap<ASTGroup.G, String> _uniq;
    @Override public void map(Chunk[] c) {
      _uniq=new IcedHashMap<>();
      ASTGroup.G g = new ASTGroup.G(1,null);
      for(int i=0;i<c[0]._len;++i) {
        g.fill(i, c, new int[]{0});
        String s_old=_uniq.putIfAbsent(g,"");
        if( s_old==null ) g=new ASTGroup.G(1,null);
      }
    }
    @Override public void reduce(UniqTask t) {
      if( _uniq!=t._uniq ) {
        IcedHashMap<ASTGroup.G,String> l = _uniq;
        IcedHashMap<ASTGroup.G,String> r = t._uniq;
        if( l.size() < r.size() ) { l=r; r = _uniq; }  // larger on the left
        for( ASTGroup.G rg:r.keySet() ) l.putIfAbsent(rg,"");  // loop over smaller set
        _uniq=l;
        t._uniq=null;
      }
    }
  }
}
