package water.rapids.ast.prims.mungers;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.rapids.Env;
import water.rapids.Merge;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNum;
import water.rapids.ast.params.AstNumList;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValFun;
import water.util.ArrayUtils;
import water.util.IcedHashSet;
import water.util.Log;

import java.util.Arrays;

/**
 * GroupBy
 * Group the rows of 'data' by unique combinations of '[group-by-cols]'.
 * Apply function 'fcn' to a Frame for each group, with a single column
 * argument, and a NA-handling flag.  Sets of tuples {fun,col,na} are allowed.
 * <p/>
 * 'fcn' must be a one of a small set of functions, all reductions, and 'GB'
 * returns a row per unique group, with the first columns being the grouping
 * columns, and the last column(s) the reduction result(s).
 * <p/>
 * Grouping can be performed on both numeric and string columns, but aggregation
 * functions are limited to the ones listed in {@link AstGroup.FCN}.
 * <p/>
 * The returned column(s).
 */
public class AstGroup extends AstPrimitive {

  private final boolean _per_node_aggregates;

  public AstGroup() {
    this(true);
  }

  public AstGroup(boolean perNodeAggregates) {
    _per_node_aggregates = perNodeAggregates;
  }

  public enum NAHandling {ALL, RM, IGNORE}

  // Functions handled by GroupBy
  public enum FCN {
    nrow() {
      @Override
      public void op(double[] d0s, double d1) {
        d0s[0]++;
      }

      @Override
      public void atomic_op(double[] d0s, double[] d1s) {
        d0s[0] += d1s[0];
      }

      @Override
      public double postPass(double ds[], long n) {
        return ds[0];
      }
    },
    mean() {
      @Override
      public void op(double[] d0s, double d1) {
        d0s[0] += d1;
      }

      @Override
      public void atomic_op(double[] d0s, double[] d1s) {
        d0s[0] += d1s[0];
      }

      @Override
      public double postPass(double ds[], long n) {
        return ds[0] / n;
      }
    },
    sum() {
      @Override
      public void op(double[] d0s, double d1) {
        d0s[0] += d1;
      }

      @Override
      public void atomic_op(double[] d0s, double[] d1s) {
        d0s[0] += d1s[0];
      }

      @Override
      public double postPass(double ds[], long n) {
        return ds[0];
      }
    },
    sumSquares() {
      @Override
      public void op(double[] d0s, double d1) {
        d0s[0] += d1 * d1;
      }

      @Override
      public void atomic_op(double[] d0s, double[] d1s) {
        d0s[0] += d1s[0];
      }

      @Override
      public double postPass(double ds[], long n) {
        return ds[0];
      }
    },
    var() {
      @Override
      public void op(double[] d0s, double d1) {
        d0s[0] += d1 * d1;
        d0s[1] += d1;
      }

      @Override
      public void atomic_op(double[] d0s, double[] d1s) {
        ArrayUtils.add(d0s, d1s);
      }

      @Override
      public double postPass(double ds[], long n) {
        double numerator = ds[0] - ds[1] * ds[1] / n;
        if (Math.abs(numerator) < 1e-5) numerator = 0;
        return numerator / (n - 1);
      }

      @Override
      public double[] initVal(int ignored) {
        return new double[2]; /* 0 -> sum_squares; 1 -> sum*/
      }
    },
    sdev() {
      @Override
      public void op(double[] d0s, double d1) {
        d0s[0] += d1 * d1;
        d0s[1] += d1;
      }

      @Override
      public void atomic_op(double[] d0s, double[] d1s) {
        ArrayUtils.add(d0s, d1s);
      }

      @Override
      public double postPass(double ds[], long n) {
        double numerator = ds[0] - ds[1] * ds[1] / n;
        if (Math.abs(numerator) < 1e-5) numerator = 0;
        return Math.sqrt(numerator / (n - 1));
      }

      @Override
      public double[] initVal(int ignored) {
        return new double[2]; /* 0 -> sum_squares; 1 -> sum*/
      }
    },
    min() {
      @Override
      public void op(double[] d0s, double d1) {
        d0s[0] = Math.min(d0s[0], d1);
      }

      @Override
      public void atomic_op(double[] d0s, double[] d1s) {
        op(d0s, d1s[0]);
      }

      @Override
      public double postPass(double ds[], long n) {
        return ds[0];
      }

      @Override
      public double[] initVal(int maxx) {
        return new double[]{Double.MAX_VALUE};
      }
    },
    max() {
      @Override
      public void op(double[] d0s, double d1) {
        d0s[0] = Math.max(d0s[0], d1);
      }

      @Override
      public void atomic_op(double[] d0s, double[] d1s) {
        op(d0s, d1s[0]);
      }

      @Override
      public double postPass(double ds[], long n) {
        return ds[0];
      }

      @Override
      public double[] initVal(int maxx) {
        return new double[]{-Double.MAX_VALUE};
      }
    },
    median() {  // we will be doing our own thing here for median

      @Override
      public void op(double[] d0s, double d1) {
        ;
      }

      @Override
      public void atomic_op(double[] d0s, double[] d1s) {
        ;
      }

      @Override
      public double postPass(double ds[], long n) {
        return 0;
      }

      @Override
      public double[] initVal(int maxx) {
        return new double[maxx];
      }
    },
    mode() {
      @Override
      public void op(double[] d0s, double d1) {
        d0s[(int) d1]++;
      }

      @Override
      public void atomic_op(double[] d0s, double[] d1s) {
        ArrayUtils.add(d0s, d1s);
      }

      @Override
      public double postPass(double ds[], long n) {
        return ArrayUtils.maxIndex(ds);
      }

      @Override
      public double[] initVal(int maxx) {
        return new double[maxx];
      }
    },;

    public abstract void op(double[] d0, double d1);

    public abstract void atomic_op(double[] d0, double[] d1);

    public abstract double postPass(double ds[], long n);

    public double[] initVal(int maxx) {
      return new double[]{0};
    }
  }

  @Override
  public int nargs() {
    return -1;
  } // (GB data [group-by-cols] {fcn col "na"}...)

  @Override
  public String[] args() {
    return new String[]{"..."};
  }

  @Override
  public String str() {
    return "GB";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    int ncols = fr.numCols();

    AstNumList groupby = check(ncols, asts[2]);
    final int[] gbCols = groupby.expand4();

    int validAggregatesCount = countNumberOfAggregates(fr, ncols, asts);

    final AGG[] aggs = constructAggregates(fr, validAggregatesCount, env, asts);

    return performGroupingWithAggregations(fr, gbCols, aggs);
  }

  /**
   * Performs grouping and aggregations on given data.
   *
   * @param fr          frame on which grouping should be performed
   * @param gbCols      indices of columns (of given frame) to be used for grouping.
   * @param aggs        aggregations to be performed on the grouped data.
   *
   * @return  grouped data with aggregations results.
   */
  public ValFrame performGroupingWithAggregations(Frame fr, int[] gbCols, AGG[] aggs) {
    final boolean hasMedian = hasMedian(aggs);
    final byte[] gbColsTypes = ArrayUtils.select(fr.types(), gbCols);
    
    final IcedHashSet<G> gss = doGroups(fr, gbCols,  gbColsTypes, aggs, hasMedian, _per_node_aggregates);
    final G[] grps = gss.toArray(new G[gss.size()]);
    
    applyOrdering(gbCols, gbColsTypes, grps);

    final int medianActionsNeeded = hasMedian ? calculateMediansForGRPS(fr, gbCols, gbColsTypes, aggs, gss, grps) : -1;

    MRTask mrFill = prepareMRFillTask(grps, aggs, gbColsTypes, medianActionsNeeded);

    String[] fcNames = prepareFCNames(fr, aggs);

    Frame f = buildOutput(gbCols, aggs.length, fr, fcNames, grps.length, mrFill);
    return new ValFrame(f);
  }

  private static boolean hasMedian(AGG[] aggs) {
    for (AGG agg : aggs) 
      if (FCN.median.equals(agg._fcn))
        return true;
    return false;
  }
  private MRTask prepareMRFillTask(final G[] grps, final AGG[] aggs, final byte[] gbColsTypes, final int medianCount) {
    return new MRTask() {
      @Override
      public void map(Chunk[] c, NewChunk[] ncs) {
        int start = (int) c[0].start();
        for (int i = 0; i < c[0]._len; ++i) {
          G g = grps[i + start];  // One Group per row
          int gbColsCnt = g.colsCount();
          int j;

          int strIdx = 0;
          int numIdx = 0;

          for (j = 0; j < gbColsCnt; j++) {
            if (gbColsTypes[j] == Vec.T_STR)
              ncs[j].addStr(g._gsStr[strIdx++]);
            else
              ncs[j].addNum(g._gs[numIdx++]);
          }

          for (int a = 0; a < aggs.length; a++) {
            if ((medianCount >=0) && g.medianR._isMedian[a])
              ncs[j++].addNum(g.medianR._medians[a]);
            else
              ncs[j++].addNum(aggs[a]._fcn.postPass(g._dss[a], g._ns[a]));
          }
        }
      }
    };
  }

  private String[] prepareFCNames(Frame fr, AGG[] aggs) {
    String[] fcnames = new String[aggs.length];
    for (int i = 0; i < aggs.length; i++) {
      if (aggs[i]._fcn.toString() != "nrow") {
        fcnames[i] = aggs[i]._fcn.toString() + "_" + fr.name(aggs[i]._col);
      } else {
        fcnames[i] = aggs[i]._fcn.toString();
      }
    }
    return fcnames;
  }



  // Count of aggregates; knock off the first 4 ASTs (GB data [group-by] [order-by]...), then count by triples.
  private int countNumberOfAggregates(Frame fr, int numberOfColumns, AstRoot asts[]) {
    int validGroupByCols = 0;
    for (int idx=3; idx < asts.length; idx+=3) {  // initial loop to count operations on valid columns, ignore String columns
      AstNumList col = check(numberOfColumns, asts[idx + 1]);
      if (col.cnt() != 1) throw new IllegalArgumentException("Group-By functions take only a single column");
      int agg_col = (int) col.min(); // Aggregate column
      if (fr.vec(agg_col).isString()) {
        Log.warn("Column "+fr._names[agg_col]+" is a string column.  Groupby operations will be skipped for this column.");
      } else
        validGroupByCols++;
    }
    return validGroupByCols;
  }

  private AGG[] constructAggregates(Frame fr, int numberOfAggregates, Env env, AstRoot asts[]) {
    AGG[] aggs = new AGG[numberOfAggregates];
    int ncols  = fr.numCols();

    int countCols = 0;
    for (int idx = 3; idx < asts.length; idx += 3) {
      Val v = asts[idx].exec(env);
      String fn = v instanceof ValFun ? v.getFun().str() : v.getStr();
      FCN fcn = FCN.valueOf(fn);
      AstNumList col = check(ncols, asts[idx + 1]);
      if (col.cnt() != 1) throw new IllegalArgumentException("Group-By functions take only a single column");
      int agg_col = (int) col.min(); // Aggregate column
      if (fcn == FCN.mode && !fr.vec(agg_col).isCategorical())
        throw new IllegalArgumentException("Mode only allowed on categorical columns");

      NAHandling na = NAHandling.valueOf(asts[idx + 2].exec(env).getStr().toUpperCase());
      if (!fr.vec(agg_col).isString())
        aggs[countCols++] = new AGG(fcn, agg_col, na, (int) fr.vec(agg_col).max() + 1);
    }
    return aggs;
  }

  private void applyOrdering(final int[] gbCols, final byte[] gbColsTypes, G[] grps) {
    if (gbCols.length > 0)
      Arrays.sort(grps, new java.util.Comparator<G>() {
        // Compare 2 groups.  Iterate down _gs, stop when _gs[i] > that._gs[i],
        // or _gs[i] < that._gs[i].  Order by various columns specified by
        // gbCols.  NaN is treated as least
        @Override
        public int compare(G g1, G g2) {
          int strIdx = 0;
          int numIdx = 0;

          for (int gbColIdx = 0; gbColIdx < gbCols.length; gbColIdx++) {
            if (gbColsTypes[gbColIdx] == Vec.T_STR) {
              if (g1._gsStr[strIdx] != null && g2._gsStr[strIdx] == null) return -1;
              if (g1._gsStr[strIdx] == null && g2._gsStr[strIdx] != null) return 1;
              int res = g1._gsStr[strIdx].compareTo(g2._gsStr[strIdx]);
              if (res != 0) return res;
              
              strIdx++;
            } else {
              if (Double.isNaN(g1._gs[numIdx]) && !Double.isNaN(g2._gs[numIdx])) return -1;
              if (!Double.isNaN(g1._gs[numIdx]) && Double.isNaN(g2._gs[numIdx])) return 1;
              if (g1._gs[numIdx] != g2._gs[numIdx]) return g1._gs[numIdx] < g2._gs[numIdx] ? -1 : 1;

              numIdx++;
            }
          }

          return 0;
        }

        // I do not believe sort() calls equals() at this time, so no need to implement
        @Override
        public boolean equals(Object o) {
          throw H2O.unimpl();
        }
      });
  }

  private int calculateMediansForGRPS(Frame fr, int[] gbCols, byte[] gbColsTypes, AGG[] aggs, IcedHashSet<G> gss, G[] grps) {
    // median action exists, we do the following three things:
    // 1. Find out how many columns over all groups we need to perform median on
    // 2. Assign an index to the NewChunk that we will be storing the data for each median column for each group
    // 3. Fill out the NewChunk for each column of each group
    int numberOfMedianActionsNeeded = 0;
    for (G g : grps) {
      for (int index = 0; index < g.medianR._isMedian.length; index++) {
        if (g.medianR._isMedian[index]) {
          g.medianR._newChunkCols[index] = numberOfMedianActionsNeeded++;
        }
      }
    }
    BuildGroup buildMedians = new BuildGroup(gbCols, gbColsTypes, aggs, gss, grps, numberOfMedianActionsNeeded);
    Vec[] groupChunks = buildMedians.doAll(numberOfMedianActionsNeeded, Vec.T_NUM, fr).close();
    buildMedians.calcMedian(groupChunks);
    return numberOfMedianActionsNeeded;
  }

  // Argument check helper
  public static AstNumList check(long dstX, AstRoot ast) {
    // Sanity check vs dst.  To simplify logic, jam the 1 col/row case in as a AstNumList
    AstNumList dim;
    if (ast instanceof AstNumList) dim = (AstNumList) ast;
    else if (ast instanceof AstNum) dim = new AstNumList(((AstNum) ast).getNum());
    else throw new IllegalArgumentException("Requires a number-list, but found a " + ast.getClass());
    if (dim.isEmpty()) return dim; // Allow empty
    for (int col : dim.expand4())
      if (!(0 <= col && col < dstX))
        throw new IllegalArgumentException("Selection must be an integer from 0 to " + dstX);
    return dim;
  }

  // Do all the grouping work.  Find groups in frame 'fr', grouped according to
  // the selected 'gbCols' columns, and for each group compute aggregrate
  // results using 'aggs'.  Return an array of groups, with the aggregate results.
  public static IcedHashSet<G> doGroups(Frame fr, int[] gbCols, AGG[] aggs) {
    final byte[] gbColsTypes = ArrayUtils.select(fr.types(), gbCols);

    return doGroups(fr, gbCols, gbColsTypes, aggs, false, true);
  }

  public static IcedHashSet<G> doGroups(Frame fr, int[] gbCols, byte[] gbColsTypes, AGG[] aggs, boolean hasMedian, boolean perNodeAggregates) {
    // do the group by work now
    long start = System.currentTimeMillis();
    GBTask<?> p1 = makeGBTask(perNodeAggregates, gbCols, gbColsTypes, aggs, hasMedian).doAll(fr);
    Log.info("Group By Task done in " + (System.currentTimeMillis() - start) / 1000. + " (s)");
    return p1.getGroups();
  }

  private static GBTask<? extends GBTask> makeGBTask(boolean perNodeAggregates, int[] gbCols, byte[] gbColsTypes, AGG[] aggs, boolean hasMedian) {
    if (perNodeAggregates)
      return new GBTaskAggsPerNode(gbCols, gbColsTypes, aggs, hasMedian);
    else
      return new GBTaskAggsPerMap(gbCols, gbColsTypes, aggs, hasMedian);
  }
  
  // Utility for AstDdply; return a single aggregate for counting rows-per-group
  public static AGG[] aggNRows() {
    return new AGG[]{new AGG(FCN.nrow, 0, NAHandling.IGNORE, 0)};
  }
  
  // Build output frame from the multi-column results
  public static Frame buildOutput(int[] gbCols, int noutCols, Frame fr, String[] fcnames, int ngrps, MRTask mrfill) {
    // Build the output!
    // the names of columns
    final int nCols = gbCols.length + noutCols;
    String[] names = new String[nCols];
    String[][] domains = new String[nCols][];
    byte[] types = new byte[nCols];

    // GroupBy columns
    for (int i = 0; i < gbCols.length; i++) {
      names[i] = fr.name(gbCols[i]);
      domains[i] = fr.domains()[gbCols[i]];
      types[i] = fr.vec(names[i]).get_type();
    }

    // Output columns of GroupBy functions
    for (int i = 0; i < fcnames.length; i++) {
      names[i + gbCols.length] = fcnames[i];
      types[i + gbCols.length] = Vec.T_NUM;
    }

    Vec v = Vec.makeZero(ngrps); // dummy layout vec
    // Convert the output arrays into a Frame, also doing the post-pass work
    Frame f =  mrfill.doAll(types, new Frame(v)).outputFrame(names, domains);
    v.remove();
    return f;
  }

  // Description of a single aggregate, including the reduction function, the
  // column and specified NA handling
  public static class AGG extends Iced {
    final FCN _fcn;
    public final int _col;
    final NAHandling _na;
    final int _maxx;            // Largest integer this column

    public AGG(FCN fcn, int col, NAHandling na, int maxx) {
      _fcn = fcn;
      _col = col;
      _na = na;
      _maxx = maxx;
    }

    // Update the array pair {ds[i],ns[i]} with d1.
    // ds is the reduction array
    // ns is the element count
    public void op(double[][] d0ss, long[] n0s, int i, double d1) {
      // Normal number or ALL   : call op()
      if (!Double.isNaN(d1) || _na == NAHandling.ALL) _fcn.op(d0ss[i], d1);
      // Normal number or IGNORE: bump count; RM: do not bump count
      if (!Double.isNaN(d1) || _na == NAHandling.IGNORE) n0s[i]++;
    }

    // Atomically update the array pair {dss[i],ns[i]} with the pair {d1,n1}.
    // Same as op() above, but called racily and updates atomically.
    public void atomic_op(double[][] d0ss, long[] n0s, int i, double[] d1s, long n1) {
      synchronized (d0ss[i]) {
        _fcn.atomic_op(d0ss[i], d1s);
        n0s[i] += n1;
      }
    }

    public double[] initVal() {
      return _fcn.initVal(_maxx);
    }
  }

  private static abstract class GBTask<E extends MRTask<E>> extends MRTask<E> {
    final int[] _gbCols; // Columns used to define group
    final byte[] _gbColsTypes; // Types of gb columns
    final int _numericGbColsCnt;
    final int _stringGbColsCnt;
    
    final AGG[] _aggs;   // Aggregate descriptions
    final boolean _hasMedian;

    GBTask(int[] gbCols, byte[] gbColsTypes, AGG[] aggs, boolean hasMedian) {
      _gbCols = gbCols;
      _gbColsTypes = gbColsTypes;
      _stringGbColsCnt = ArrayUtils.occurrenceCount(_gbColsTypes, Vec.T_STR);
      _numericGbColsCnt = gbColsTypes.length - _stringGbColsCnt;
      _aggs = aggs;
      _hasMedian = hasMedian;
    }

    protected void map(Chunk[] cs, IcedHashSet<G> groups) {
      G gWork = new G(_numericGbColsCnt, _stringGbColsCnt, _aggs, _hasMedian); // Working Group
      G gOld;                   // Existing Group to be filled in
      for (int row = 0; row < cs[0]._len; row++) {
        // Find the Group being worked on
        gWork.fill(row, cs, _gbCols, _gbColsTypes);            // Fill the worker Group for the hashtable lookup
        if (groups.addIfAbsent(gWork) == null) { // Insert if not absent (note: no race, no need for atomic)
          gOld = gWork;                          // Inserted 'gWork' into table
          gWork = new G(_numericGbColsCnt, _stringGbColsCnt, _aggs, _hasMedian);   // need entirely new G
        } else gOld = groups.get(gWork);            // Else get existing group

        for (int i = 0; i < _aggs.length; i++) // Accumulate aggregate reductions
          _aggs[i].op(gOld._dss, gOld._ns, i, cs[_aggs[i]._col].atd(row));
      }
    }
    
    abstract IcedHashSet<G> getGroups();
    
  } 
  
  // --------------------------------------------------------------------------
  // Main worker MRTask.  Makes 1 pass over the data, and accumulates both all
  // groups and all aggregates
  // This version merges discovered groups into a per-node aggregates map - it
  // more memory efficient but it seems to suffer from a race condition 
  // (bug PUBDEV-6319).
  private static class GBTaskAggsPerNode extends GBTask<GBTaskAggsPerNode> {
    final IcedHashSet<G> _gss; // Shared per-node, common, racy

    GBTaskAggsPerNode(int[] gbCols, byte[] gbColsTypes, AGG[] aggs, boolean hasMedian) {
      super(gbCols, gbColsTypes, aggs, hasMedian);
      _gss = new IcedHashSet<>();
    }

    @Override
    public void map(Chunk[] cs) {
      // Groups found in this Chunk
      IcedHashSet<G> gs = new IcedHashSet<>();
      map(cs, gs);
      // This is a racy update into the node-local shared table of groups
      reduce(gs);               // Atomically merge Group stats
    }

    // Racy update on a subtle path: reduction is always single-threaded, but
    // the shared global hashtable being reduced into is ALSO being written by
    // parallel map calls.
    @Override
    public void reduce(GBTaskAggsPerNode t) {
      if (_gss != t._gss) {
        // this means we got the result from another node
        // it is easy to partition the result into distinct subsets - we don't have to worry about collisions
        // => no need to synchronize (but for now we do use atomic_op anyway), we just parallelize the merge
        int otherSize = t._gss.size();
        if (otherSize == 0)
          return;
        G[] otherGroups = t._gss.toArray(new G[otherSize]);
        final int subGroupSize = otherSize > H2O.ARGS.nthreads ? 
                (int) Math.ceil((double) otherSize / H2O.ARGS.nthreads) : otherSize;
        MergeGroupsFun f = new MergeGroupsFun(_aggs, _gss, otherGroups, subGroupSize);
        if (subGroupSize == otherSize) {
          f.map(0); // not worth parallelizing, execute directly
        } else {
          H2O.submitTask(new LocalMR(f, H2O.ARGS.nthreads)).join();
        }
      }
    }

    // Non-blocking race-safe update of the shared per-node groups hashtable
    private void reduce(IcedHashSet<G> r) {
      for (G rg : r) {
        G lg;
        if ((lg = _gss.addIfAbsent(rg)) != null) {
          for (int i = 0; i < _aggs.length; i++)
            _aggs[i].atomic_op(lg._dss, lg._ns, i, rg._dss[i], rg._ns[i]); // Need to atomically merge groups here
        }
      }
    }

    @Override
    IcedHashSet<G> getGroups() {
      return _gss;
    }
  }

  private static class MergeGroupsFun extends MrFun<MergeGroupsFun> {
    private final AGG[] _aggs;
    private final transient IcedHashSet<G> _gss;
    private final transient G[] _other;
    private final int _size;

    MergeGroupsFun(AGG[] aggs, IcedHashSet<G> gss, G[] other, int size) {
      _aggs = aggs;
      _gss = gss;
      _other = other;
      _size = size;
    }

    @Override
    protected void map(final int subGroupId) {
      for (int g = subGroupId * _size; g < (subGroupId + 1) * _size && g < _other.length; g++) {
        G rg = _other[g];
        G lg;
        if ((lg = _gss.addIfAbsent(rg)) != null) {
          for (int i = 0; i < _aggs.length; i++)
            _aggs[i].atomic_op(lg._dss, lg._ns, i, rg._dss[i], rg._ns[i]); // Need to atomically merge groups here
        }
      }
    }
  }

  // --------------------------------------------------------------------------
  // "Safe" alternative of GBTaskAggsPerNode - instead of maintaining
  // a node-global map of aggregates, it creates aggregates per chunk
  // and uses reduce to reduce results of map into a single aggregated.
  // Consumes more memory but doesn't suffer from bug PUBDEV-6319.
  public static class GBTaskAggsPerMap extends GBTask<GBTaskAggsPerMap> {
    IcedHashSet<G> _gss; // each map will have its own IcedHashMap

    GBTaskAggsPerMap(int[] gbCols, byte[] gbColsTypes, AGG[] aggs, boolean hasMedian) {
      super(gbCols, gbColsTypes, aggs, hasMedian);
    }

    @Override
    public void map(Chunk[] cs) {
      // Groups found in this Chunk
      _gss = new IcedHashSet<>();
      map(cs, _gss);
    }

    // combine IcedHashMap from all threads here.
    @Override
    public void reduce(GBTaskAggsPerMap t) {
      for (G rg : t._gss) {
        if (_gss.addIfAbsent(rg) != null) {
          G lg = _gss.get(rg);
          for (int i = 0; i < _aggs.length; i++)
            _aggs[i].atomic_op(lg._dss, lg._ns, i, rg._dss[i], rg._ns[i]); // Need to atomically merge groups here
        }
      }
    }

    @Override
    IcedHashSet<G> getGroups() {
      return _gss;
    }
  }

  public static class MedianResult extends Iced {
    int[] _medianCols;
    double[] _medians;
    boolean[] _isMedian;
    int[] _newChunkCols;
    public NAHandling[] _na;
    
    public MedianResult(int len) {
      _medianCols = new int[len];
      _medians = new double[len];
      _isMedian = new boolean[len];
      _newChunkCols = new int[len];
      _na = new NAHandling[len];
    }
  }
  // Groups!  Contains a Group Key - arrays of doubles and strings that
  // define the Group.  Also contains an array of doubles for the
  // aggregate results, one per aggregate.
  public static class G extends Iced<G> {
    public final double[] _gs;  // Group Key: Array is final; contents change with the "fill"
    public final BufferedString[] _gsStr;  // Group Key: Array is final; contents change with the "fill"
    int _hash;           // Hash is not final; changes with the "fill"

    public final double _dss[][];      // Aggregates: usually sum or sum*2
    public final long _ns[];         // row counts per aggregate, varies by NA handling and column
/*    int[] _medianCols;    // record which columns in reference to data frame
    double[] _medians;
    boolean[] _isMedian;
    int[] _newChunkCols;  // record which columns in newChunk to store group
    public NAHandling[] _na;*/
    public MedianResult medianR = null;

    public G(int ncolsNum, AGG[] aggs) {
      this(ncolsNum, 0, aggs, false);
    }
    
    public G(int ncolsNum, int ncolsStr, AGG[] aggs) {
      this(ncolsNum, ncolsStr, aggs, false);
    }

    public G(int ncolsNum, AGG[] aggs, boolean hasMedian) { this(ncolsNum, 0, aggs, hasMedian); }
    
    public G(int ncolsNum, int ncolsStr, AGG[] aggs, boolean hasMedian) {
      _gs = new double[ncolsNum];
      _gsStr = new BufferedString[ncolsStr];

      for (int i = 0; i < _gsStr.length; i++)
        _gsStr[i] = new BufferedString();

      int len = aggs == null ? 0 : aggs.length;
      _dss = new double[len][];
      _ns = new long[len];

      if (hasMedian) {
        medianR = new MedianResult(len);
      }

      for (int i = 0; i < len; i++) {
        _dss[i] = aggs[i].initVal();
        if (hasMedian && (aggs[i]._fcn.toString().equals("median"))) { // for median function only
          medianR._medianCols[i] = aggs[i]._col;    // which column in the data set to aggregate on
          medianR._isMedian[i] = true;
          medianR._na[i] = aggs[i]._na;
        }
      }
    }
    
    private int colsCount() { return _gs.length + _gsStr.length; }

    public G fill(int row, Chunk chks[]) {
      for (int c = 0; c < chks.length; c++) { // For all selection cols
        Vec vec = chks[c].vec();

        // Load into working array
        if (vec.isString())
          chks[c].atStr(_gsStr[c], row);
        else
          _gs[c] = chks[c].atd(row);
      }
      
      _hash = hash();
      return this;
    }

    public G fill(int row, Chunk[] chks, int[] colsNum) {
      byte[] gbColsTypes = new byte[colsNum.length];
      Arrays.fill(gbColsTypes, Vec.T_NUM);

      return fill(row, chks, colsNum, gbColsTypes);
    }

    public G fill(int row, Chunk[] chks, int[] cols, byte[] gbColsTypes) {
      int strIdx = 0;
      int numIdx = 0;

      for (int c = 0; c < cols.length; c++) // For all selection cols
        // Load into working array
        if (gbColsTypes[c] == Vec.T_STR)
          chks[cols[c]].atStr(_gsStr[strIdx++], row);
        else
          _gs[numIdx++] = chks[cols[c]].atd(row);

      _hash = hash();
      return this;
    }

    protected int hash() {
      long h = 0;                 // hash is sum of field bits
      for (double d : _gs) h += Double.doubleToRawLongBits(d);
      // Doubles are lousy hashes; mix up the bits some
      h ^= (h >>> 20) ^ (h >>> 12);
      h ^= (h >>> 7) ^ (h >>> 4);

      for (BufferedString str : _gsStr) h = 37 * h + str.hashCode();
      
      return (int) ((h ^ (h >> 32)) & 0x7FFFFFFF);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof G 
              && Arrays.equals(_gs, ((G) o)._gs)
              && Arrays.equals(_gsStr, ((G) o)._gsStr);
    }

    @Override
    public int hashCode() {
      return _hash;
    }

    @Override
    public String toString() {
      return Arrays.toString(_gsStr) + " - " + Arrays.toString(_gs);
    }
  }

  // --------------------------------------------------------------------------
  // For each groupG and each aggregate function (median only), we separate and
  // extract the column per groupG per aggregate function into a NewChunk column
  // here.
  private static class BuildGroup extends MRTask<BuildGroup> {
    final int[] _gbCols;
    final byte[] _gbColsTypes;
    final int _numericGbColsCnt;
    final int _stringGbColsCnt;
    private final AGG[] _aggs;   // Aggregate descriptions
    private final int _medianCols;
    IcedHashSet<G> _gss;
    private G[] _grps;


    BuildGroup(int[] gbCols, byte[] gbColsTypes, AGG[] aggs, IcedHashSet<G> gss, G[] grps, int medianCols) {
      _gbCols = gbCols;
      _gbColsTypes = gbColsTypes;
      _stringGbColsCnt = ArrayUtils.occurrenceCount(_gbColsTypes, Vec.T_STR);
      _numericGbColsCnt = gbColsTypes.length - _stringGbColsCnt;
      _aggs = aggs;
      _gss = gss;
      _grps = grps;
      _medianCols = medianCols;
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
      G gWork = new G(_numericGbColsCnt, _stringGbColsCnt, _aggs, _medianCols > 0); // Working Group
      G gOld;

      for (int row = 0; row < cs[0]._len; row++) {  // for each
        gWork.fill(row, cs, _gbCols, _gbColsTypes);
        gOld = _gss.get(gWork);
        for (int i = 0; i < gOld.medianR._isMedian.length; i++) { // Accumulate aggregate reductions
          if (gOld.medianR._isMedian[i]) {  // median action required on column and group
            double d1 = cs[gOld.medianR._medianCols[i]].atd(row);
            if (!Double.isNaN(d1) || gOld.medianR._na[i] != NAHandling.RM)
              ncs[gOld.medianR._newChunkCols[i]].addNum(d1);  // build up dataset for each group
          }
        }
      }
    }

    // For the data column collected for each G and each aggregate function, make a frame out of the data
    // newChunk column.  Sort the column and return median as the middle value of mean of two middle values.
    Vec[] close() {
      Futures fs = new Futures();

      int cCount = 0;
      Vec[] tempVgrps = new Vec[_medianCols];
      for (G oneG : _grps) {
        for (int index = 0; index < oneG.medianR._isMedian.length; index++) {
          if (oneG.medianR._isMedian[index]) {  // median action is needed
            // make a frame out of the NewChunk vector
            tempVgrps[cCount++] = _appendables[oneG.medianR._newChunkCols[index]].close(_appendables[oneG.medianR._newChunkCols[index]].compute_rowLayout(), fs);
          }
        }
      }
      fs.blockForPending();
      return tempVgrps;
    }

    public void calcMedian(Vec[] tempVgrps) {
      int cCount = 0;
      for (G oneG : _grps) {
        for (int index = 0; index < oneG.medianR._isMedian.length; index++) {
          if (oneG.medianR._isMedian[index]) {
            Vec[] vgrps = new Vec[1];
            vgrps[0] = tempVgrps[cCount++];
            long totalRows = vgrps[0].length();
            double medianVal;

            if (totalRows == 0) {
              medianVal = Double.NaN;  // return NAN for empty frames.  Should not have happened!
            } else {
              Frame myFrame = new Frame(Key.make(), vgrps);
              long midRow = totalRows / 2;
              Frame tempFrame = Merge.sort(myFrame, new int[]{0});
              medianVal = totalRows % 2 == 0 ? 0.5 * (tempFrame.vec(0).at(midRow - 1) +
                      tempFrame.vec(0).at(midRow)) : tempFrame.vec(0).at(midRow);
              tempFrame.delete();
              myFrame.delete();
            }
            oneG.medianR._medians[index] = medianVal;
          }
        }
      }
    }
  }
}
