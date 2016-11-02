package water.rapids.ast.prims.advmath;

import water.AutoBuffer;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.nbhm.NonBlockingHashMapLong;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Variance between columns of a frame
 * TODO: Define "table" in terms of "groupby"
 * TODO: keep dense format for two-column comparison (like in previous version of Rapids)
 * (table X Y) ==>
 * (groupby (cbind X Y) [X Y] nrow TRUE)
 */
public class AstTable extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"X", "Y", "dense"};
  }

  @Override
  public int nargs() {
    return -1;
  } // (table X dense)  or (table X Y dense)

  @Override
  public String str() {
    return "table";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr1 = stk.track(asts[1].exec(env)).getFrame();
    final boolean dense = asts[asts.length - 1].exec(env).getNum() == 1;
    Frame fr2 = asts.length == 4 ? stk.track(asts[2].exec(env)).getFrame() : null;
    int ncols = fr1.numCols() + (fr2 == null ? 0 : fr2.numCols());
    Vec vec1 = fr1.vec(0);

    ValFrame res = fast_table(vec1, ncols, fr1._names[0]);
    if (res != null) return res;

    if (!(asts.length == 3 || asts.length == 4) || ncols > 2)
      throw new IllegalArgumentException("table expects one or two columns");

    Vec vec2 = fr1.numCols() == 2 ? fr1.vec(1) : fr2 != null ? fr2.vec(0) : null;
    int sz = fr1._names.length + (fr2 != null ? fr2._names.length : 0);
    String[] colnames = new String[sz];
    int i = 0;
    for (String name : fr1._names) colnames[i++] = name;
    if (fr2 != null) for (String name : fr2._names) colnames[i++] = name;


    return slow_table(vec1, vec2, colnames, dense);
  }

  // -------------------------------------------------------------------------
  // Fast-path for 1 integer column
  private ValFrame fast_table(Vec v1, int ncols, String colname) {
    if (ncols != 1 || !v1.isInt()) return null;
    long spanl = (long) v1.max() - (long) v1.min() + 1;
    if (spanl > 1000000) return null; // Cap at decent array size, for performance

    // First fast-pass counting
    AstTable.FastCnt fastCnt = new AstTable.FastCnt((long) v1.min(), (int) spanl).doAll(v1);
    final long cnts[] = fastCnt._cnts;
    final long minVal = fastCnt._min;

    // Second pass to build the result frame, skipping zeros
    Vec dataLayoutVec = Vec.makeCon(0, cnts.length);
    Frame fr = new MRTask() {
      @Override
      public void map(Chunk cs[], NewChunk nc0, NewChunk nc1) {
        final Chunk c = cs[0];
        for (int i = 0; i < c._len; ++i) {
          int idx = (int) (i + c.start());
          if (cnts[idx] > 0) {
            nc0.addNum(idx + minVal);
            nc1.addNum(cnts[idx]);
          }
        }
      }
    }.doAll(new byte[]{Vec.T_NUM, Vec.T_NUM}, dataLayoutVec).outputFrame(new String[]{colname, "Count"},
        new String[][]{v1.domain(), null});
    dataLayoutVec.remove();
    return new ValFrame(fr);
  }

  // Fast-pass for counting unique integers in a span
  private static class FastCnt extends MRTask<AstTable.FastCnt> {
    final long _min;
    final int _span;
    long _cnts[];

    FastCnt(long min, int span) {
      _min = min;
      _span = span;
    }

    @Override
    public void map(Chunk c) {
      _cnts = new long[_span];
      for (int i = 0; i < c._len; i++)
        if (!c.isNA(i))
          _cnts[(int) (c.at8(i) - _min)]++;
    }

    @Override
    public void reduce(AstTable.FastCnt fc) {
      ArrayUtils.add(_cnts, fc._cnts);
    }
  }

  // -------------------------------------------------------------------------
  // Count unique combos in 1 or 2 columns, where the values are not integers,
  // or cover a very large span.
  private ValFrame slow_table(Vec v1, Vec v2, String[] colnames, boolean dense) {


    // For simplicity, repeat v1 if v2 is missing; this will end up filling in
    // only the diagonal of a 2-D array (in what is otherwise a 1-D array).
    // This should be nearly the same cost as a 1-D array, since everything is
    // sparsely filled in.

    // If this is the 1-column case (all counts on the diagonals), just build a
    // 1-d result.
    if (v2 == null) {


      // Slow-pass group counting, very sparse hashtables.  Note that Vec v2 is
      // used as the left-most arg, or OUTER dimension - which will be columns in
      // the final result.
      AstTable.SlowCnt sc = new AstTable.SlowCnt().doAll(v1, v1);

      // Get the column headers as sorted doubles
      double dcols[] = collectDomain(sc._col0s);

      Frame res = new Frame();
      Vec rowlabel = Vec.makeVec(dcols, Vec.VectorGroup.VG_LEN1.addVec());
      rowlabel.setDomain(v1.domain());
      res.add(colnames[0], rowlabel);
      long cnts[] = new long[dcols.length];
      for (int col = 0; col < dcols.length; col++) {
        long lkey = Double.doubleToRawLongBits(dcols[col]);
        NonBlockingHashMapLong<AtomicLong> colx = sc._col0s.get(lkey);
        AtomicLong al = colx.get(lkey);
        cnts[col] = al.get();
      }
      Vec vec = Vec.makeVec(cnts, null, Vec.VectorGroup.VG_LEN1.addVec());
      res.add("Counts", vec);
      return new ValFrame(res);
    }

    // 2-d table result.
    Frame res = new Frame();
    if (!dense) {

      // Slow-pass group counting, very sparse hashtables.  Note that Vec v2 is
      // used as the left-most arg, or OUTER dimension - which will be columns in
      // the final result.
      AstTable.SlowCnt sc = new AstTable.SlowCnt().doAll(v2, v1);

      // Get the column headers as sorted doubles
      double dcols[] = collectDomain(sc._col0s);

      // Need the row headers as sorted doubles also, but these are scattered
      // throughout the nested tables.  Fold 'em into 1 table.
      NonBlockingHashMapLong<AtomicLong> rows = new NonBlockingHashMapLong<>();
      for (NonBlockingHashMapLong.IteratorLong i = iter(sc._col0s); i.hasNext(); )
        rows.putAll(sc._col0s.get(i.nextLong()));
      double drows[] = collectDomain(rows);

      // Now walk the columns one by one, building a Vec per column, building a
      // Frame result.  Rowlabel for first column.

      Vec rowlabel = Vec.makeVec(drows, Vec.VectorGroup.VG_LEN1.addVec());
      rowlabel.setDomain(v1.domain());
      res.add(colnames[0], rowlabel);
      long cnts[] = new long[drows.length];
      for (int col = 0; col < dcols.length; col++) {
        NonBlockingHashMapLong<AtomicLong> colx = sc._col0s.get(Double.doubleToRawLongBits(dcols[col]));
        for (int row = 0; row < drows.length; row++) {
          AtomicLong al = colx.get(Double.doubleToRawLongBits(drows[row]));
          cnts[row] = al == null ? 0 : al.get();
        }
        Vec vec = Vec.makeVec(cnts, null, Vec.VectorGroup.VG_LEN1.addVec());
        res.add(v2.isCategorical() ? v2.domain()[col] : Double.toString(dcols[col]), vec);
      }
    } else {

      AstTable.SlowCnt sc = new AstTable.SlowCnt().doAll(v1, v2);

      double dcols[] = collectDomain(sc._col0s);

      NonBlockingHashMapLong<AtomicLong> rows = new NonBlockingHashMapLong<>();
      for (NonBlockingHashMapLong.IteratorLong i = iter(sc._col0s); i.hasNext(); )
        rows.putAll(sc._col0s.get(i.nextLong()));
      double drows[] = collectDomain(rows);

      int x = 0;
      int sz = 0;
      for (NonBlockingHashMapLong.IteratorLong i = iter(sc._col0s); i.hasNext(); ) {
        sz += sc._col0s.get(i.nextLong()).size();
      }
      long cnts[] = new long[sz];
      double[] left_categ = new double[sz];
      double[] right_categ = new double[sz];

      for (double dcol : dcols) {
        NonBlockingHashMapLong<AtomicLong> colx = sc._col0s.get(Double.doubleToRawLongBits(dcol));
        for (double drow : drows) {
          AtomicLong al = colx.get(Double.doubleToRawLongBits(drow));
          if (al != null) {
            left_categ[x] = dcol;
            right_categ[x] = drow;
            cnts[x] = al.get();
            x++;
          }
        }
      }

      Vec vec = Vec.makeVec(left_categ, Vec.VectorGroup.VG_LEN1.addVec());
      if (v1.isCategorical()) vec.setDomain(v1.domain());
      res.add(colnames[0], vec);
      vec = Vec.makeVec(right_categ, Vec.VectorGroup.VG_LEN1.addVec());
      if (v2.isCategorical()) vec.setDomain(v2.domain());
      res.add(colnames[1], vec);
      vec = Vec.makeVec(cnts, null, Vec.VectorGroup.VG_LEN1.addVec());
      res.add("Counts", vec);
    }
    return new ValFrame(res);
  }

  // Collect the unique longs from this NBHML, convert to doubles and return
  // them as a sorted double[].
  private static double[] collectDomain(NonBlockingHashMapLong ls) {
    int sz = ls.size();         // Uniques
    double ds[] = new double[sz];
    int x = 0;
    for (NonBlockingHashMapLong.IteratorLong i = iter(ls); i.hasNext(); )
      ds[x++] = Double.longBitsToDouble(i.nextLong());
    Arrays.sort(ds);
    return ds;
  }


  private static NonBlockingHashMapLong.IteratorLong iter(NonBlockingHashMapLong nbhml) {
    return (NonBlockingHashMapLong.IteratorLong) nbhml.keySet().iterator();
  }

  // Implementation is a double-dimension NBHML.  Each dimension key is the raw
  // long bits of the double column.  Bottoms out in an AtomicLong.
  private static class SlowCnt extends MRTask<AstTable.SlowCnt> {
    transient NonBlockingHashMapLong<NonBlockingHashMapLong<AtomicLong>> _col0s;

    @Override
    public void setupLocal() {
      _col0s = new NonBlockingHashMapLong<>();
    }

    @Override
    public void map(Chunk c0, Chunk c1) {
      for (int i = 0; i < c0._len; i++) {

        double d0 = c0.atd(i);
        if (Double.isNaN(d0)) continue;
        long l0 = Double.doubleToRawLongBits(d0);

        double d1 = c1.atd(i);
        if (Double.isNaN(d1)) continue;
        long l1 = Double.doubleToRawLongBits(d1);

        // Atomically fetch/create nested NBHM
        NonBlockingHashMapLong<AtomicLong> col1s = _col0s.get(l0);
        if (col1s == null) {   // Speed filter pre-filled entries
          col1s = new NonBlockingHashMapLong<>();
          NonBlockingHashMapLong<AtomicLong> old = _col0s.putIfAbsent(l0, col1s);
          if (old != null) col1s = old; // Lost race, use old value
        }

        // Atomically fetch/create nested AtomicLong
        AtomicLong cnt = col1s.get(l1);
        if (cnt == null) {   // Speed filter pre-filled entries
          cnt = new AtomicLong();
          AtomicLong old = col1s.putIfAbsent(l1, cnt);
          if (old != null) cnt = old; // Lost race, use old value
        }

        // Atomically bump counter
        cnt.incrementAndGet();
      }
    }

    @Override
    public void reduce(AstTable.SlowCnt sc) {
      if (_col0s == sc._col0s) return;
      throw water.H2O.unimpl();
    }

    public final AutoBuffer write_impl(AutoBuffer ab) {
      if (_col0s == null) return ab.put8(0);
      ab.put8(_col0s.size());
      for (long col0 : _col0s.keySetLong()) {
        ab.put8(col0);
        NonBlockingHashMapLong<AtomicLong> col1s = _col0s.get(col0);
        ab.put8(col1s.size());
        for (long col1 : col1s.keySetLong()) {
          ab.put8(col1);
          ab.put8(col1s.get(col1).get());
        }
      }
      return ab;
    }

    public final AstTable.SlowCnt read_impl(AutoBuffer ab) {
      long len0 = ab.get8();
      if (len0 == 0) return this;
      _col0s = new NonBlockingHashMapLong<>();
      for (long i = 0; i < len0; i++) {
        NonBlockingHashMapLong<AtomicLong> col1s = new NonBlockingHashMapLong<>();
        _col0s.put(ab.get8(), col1s);
        long len1 = ab.get8();
        for (long j = 0; j < len1; j++)
          col1s.put(ab.get8(), new AtomicLong(ab.get8()));
      }
      return this;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (NonBlockingHashMapLong.IteratorLong i = iter(_col0s); i.hasNext(); ) {
        long l = i.nextLong();
        double d = Double.longBitsToDouble(l);
        sb.append(d).append(": {");
        NonBlockingHashMapLong<AtomicLong> col1s = _col0s.get(l);
        for (NonBlockingHashMapLong.IteratorLong j = iter(col1s); j.hasNext(); ) {
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
