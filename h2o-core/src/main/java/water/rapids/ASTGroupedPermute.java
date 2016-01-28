package water.rapids;

import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.IcedHashMap;
import water.util.Log;

import java.util.HashMap;


public class ASTGroupedPermute extends ASTPrim {
  //   .newExpr("grouped_permute", fr, permCol, permByCol, groupByCols, keepCol)
  @Override public String[] args() { return new String[]{"ary", "permCol", "groupBy", "permuteBy", "keepCol"}; }  // currently only allow 2 items in permuteBy
  @Override int nargs() { return 1 + 5; } // (trim x col groupBy permuteBy keepCol)
  @Override public String str() { return "grouped_permute"; }
  @Override Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    final int permCol = (int) asts[2].exec(env).getNum();
    ASTNumList groupby = ASTGroup.check(fr.numCols(), asts[3]);
    final int[] gbCols = groupby.expand4();
    final int permuteBy = (int) asts[4].exec(env).getNum();
    final int keepCol = (int) asts[5].exec(env).getNum();

    String[] names = new String[gbCols.length + 4];
    int i = 0;
    for (; i < gbCols.length; ++i)
      names[i] = fr.name(gbCols[i]);
    names[i++] = "In";
    names[i++] = "Out";
    names[i++] = "InAmnt";
    names[i] = "OutAmnt";

    String[][] domains = new String[names.length][];
    int d = 0;
    for (; d < gbCols.length; d++)
      domains[d] = fr.domains()[gbCols[d]];
    domains[d++] = fr.domains()[permCol];
    domains[d++] = fr.domains()[permCol];
    domains[d++] = fr.domains()[keepCol];
    domains[d] = fr.domains()[keepCol];
    long s = System.currentTimeMillis();
    BuildGroups t = new BuildGroups(gbCols, permuteBy, permCol, keepCol).doAll(fr);
    Log.info("Elapsed time: " + (System.currentTimeMillis() - s) / 1000. + "s");
    s = System.currentTimeMillis();
    SmashGroups sg;
    H2O.submitTask(sg=new SmashGroups(t._grps)).join();
    Log.info("Elapsed time: " + (System.currentTimeMillis() - s) / 1000. + "s");
    return new ValFrame(buildOutput(sg._res.values().toArray(new double[0][][]), names, domains));
  }

  private static Frame buildOutput(final double[][][] a, String[] names, String[][] domains) {
    Frame dVec = new Frame(Vec.makeSeq(0, a.length));
    long s = System.currentTimeMillis();
    Frame res = new MRTask() {
      @Override public void map(Chunk[] cs, NewChunk[] ncs) {
        for(int i=0;i<cs[0]._len;++i)
          for (double[] anAa : a[(int)cs[0].at8(i)])
            for (int k = 0; k < anAa.length; ++k)
              ncs[k].addNum(anAa[k]);
      }
    }.doAll(5, Vec.T_NUM, dVec).outputFrame(null, names, domains);
    Log.info("Elapsed time: " + (System.currentTimeMillis() - s) / 1000. + "s");
    dVec.delete();
    return res;
  }

  private static class BuildGroups extends MRTask<BuildGroups> {
    IcedHashMap<Long, IcedHashMap<Long, double[]>[]> _grps;  // shared per node (all grps with permutations atomically inserted)

    private final int _gbCols[];
    private final int _permuteBy;
    private final int _permuteCol;
    private final int _amntCol;

    BuildGroups(int[] gbCols, int permuteBy, int permuteCol, int amntCol) {
      _gbCols = gbCols;
      _permuteBy = permuteBy;
      _permuteCol = permuteCol;
      _amntCol = amntCol;
    }

    @Override public void setupLocal() { _grps = new IcedHashMap<>(); }
    @Override public void map(Chunk[] chks) {
      String[] dom = chks[_permuteBy].vec().domain();
      IcedHashMap<Long, IcedHashMap<Long, double[]>[]> grps = new IcedHashMap<>();
      for (int row = 0; row < chks[0]._len; ++row) {
        long jid = chks[_gbCols[0]].at8(row);
        long rid = chks[_permuteCol].at8(row);
        double[] aci = new double[]{rid,  chks[_amntCol].atd(row)};
        int type = dom[(int) chks[_permuteBy].at8(row)].equals("D") ? 0 : 1;
        if( grps.containsKey(jid) ) {
          IcedHashMap<Long, double[]>[] dcWork = grps.get(jid);
          if(dcWork[type].putIfAbsent(rid, aci)!=null)
            dcWork[type].get(rid)[1] += aci[1];
        } else {
          IcedHashMap<Long, double[]>[] dcAcnts = new IcedHashMap[2];
          dcAcnts[0] = new IcedHashMap<>();
          dcAcnts[1] = new IcedHashMap<>();
          dcAcnts[type].put(rid, aci);
          grps.put(jid,dcAcnts);
        }
      }
      reduce(grps);
    }

    @Override public void reduce(BuildGroups t) { if (_grps != t._grps) reduce(t._grps); }
    private void reduce(IcedHashMap<Long, IcedHashMap<Long, double[]>[]> r) {
      for (Long l : r.keySet()) {
        if (_grps.putIfAbsent(l, r.get(l)) != null) {
          IcedHashMap<Long, double[]>[] rdbls = r.get(l);
          IcedHashMap<Long, double[]>[] ldbls = _grps.get(l);

          for(Long rr: rdbls[0].keySet())
            if( ldbls[0].putIfAbsent(rr, rdbls[0].get(rr))!=null)
              ldbls[0].get(rr)[1]+=rdbls[0].get(rr)[1];

          for(Long rr: rdbls[1].keySet())
            if( ldbls[1].putIfAbsent(rr, rdbls[1].get(rr))!=null)
              ldbls[1].get(rr)[1]+=rdbls[1].get(rr)[1];
        }
      }
    }
  }

  private static class SmashGroups extends H2O.H2OCountedCompleter<SmashGroups> {
    private final IcedHashMap<Long, IcedHashMap<Long, double[]>[]> _grps;
    private final HashMap<Integer, Long> _map;
    private int _hi;
    private int _lo;

    SmashGroups _left;
    SmashGroups _rite;

    private IcedHashMap<Long, double[][]> _res;

    SmashGroups(IcedHashMap<Long, IcedHashMap<Long, double[]>[]> grps) {
      _grps = grps;
      _lo = 0;
      _hi = _grps.size();
      _res = new IcedHashMap<>();
      _map = new HashMap<>();
      int i=0;
      for(Long l: _grps.keySet())
        _map.put(i++,l);
    }

    @Override public void compute2() {
      assert _left == null && _rite == null;
      if ((_hi - _lo) >= 2) { // divide/conquer down to 1 IHM
        final int mid = (_lo + _hi) >>> 1; // Mid-point
        _left = copyAndInit();
        _rite = copyAndInit();
        _left._hi = mid;          // Reset mid-point
        _rite._lo = mid;          // Also set self mid-point
        addToPendingCount(1);     // One fork awaiting completion
        _left.fork();             // Runs in another thread/FJ instance
        _rite.compute2();         // Runs in THIS F/J thread
        return;
      }
      if( _hi > _lo ) {
        smash();
      }
      tryComplete();
    }

    private void smash() {
      long key = _map.get(_lo);
      IcedHashMap<Long, double[]>[] pair = _grps.get(key);
      double[][] res = new double[pair[0].size() * pair[1].size()][]; // all combos
      int d0=0;
      for( double[] ds0: pair[0].values()) {
        for(double[] ds1: pair[1].values())
          res[d0++] = new double[]{key, ds0[0], ds1[0], ds0[1], ds1[1]};
      }
      _res.put(key, res);
    }
    private SmashGroups copyAndInit() {
      SmashGroups x = SmashGroups.this.clone();
      x.setCompleter(this);
      x. _left = x. _rite = null;
      x.setPendingCount(0);
      return x;
    }
  }
}
