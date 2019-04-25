package hex.svm;

import water.MRTask;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.Log;

public class PrimalDualIPM {

  static Vec solve(Frame rbicf, Vec label, Params params) {
    checkLabel(label);

    final double c_pos = params._weight_positive * params._hyper_parm;
    final double c_neg = params._weight_negative * params._hyper_parm;
    final long num_constraints = rbicf.numRows() * 2;

    Frame volatileWorkspace = new Frame(new String[]{"z", "xi", "dxi", "la", "dla", "tlx", "tux", "xilx", "laux", "d", "dx"}, label.makeVolatileDoubles(11));

    Frame workspace = new Frame(new String[]{"label"}, new Vec[]{label});
    workspace.add("x", label.makeZero());
    workspace.add(volatileWorkspace);

    new InitTask(c_pos, c_neg).doAll(workspace);

    Vec z = workspace.vec("z");
    Vec la = workspace.vec("la");
    Vec xi = workspace.vec("xi");
    Vec x = workspace.vec("x");
    Vec dxi = workspace.vec("dxi");
    Vec dla = workspace.vec("dla");
    Vec d = workspace.vec("d");
    Vec dx = workspace.vec("dx");

    double nu = 0;
    boolean converged = false;
    for (int iter = 0; iter < params._max_iter; iter++) {
      final double eta = new SurrogateGapTask(c_pos, c_neg).doAll(workspace)._sum;
      final double t = (params._mu_factor * num_constraints) / eta;
      Log.debug("sgap: " + eta + " t: " + t);

      computePartialZ(rbicf, x, params._tradeoff, z);
      CheckConvergenceTask cct = new CheckConvergenceTask(nu).doAll(workspace);
      converged = cct._resp <= params._feasible_threshold && cct._resd <= params._feasible_threshold && eta <= params._sgap_bound;
      if (converged) {
        break;
      }

      new UpdateVarsTask(c_pos, c_neg, params._x_epsilon, t).doAll(workspace);

      LLMatrix icfA = MatrixUtils.productMM(rbicf, d);
      LLMatrix lra = MatrixUtils.cf(icfA);

      final double dnu = computeDeltaNu(rbicf, d, label, z, x, lra);
      computeDeltaX(rbicf, d, label, dnu, lra, z, dx);
      
      LineSearchTask lst = new LineSearchTask(c_pos, c_neg).doAll(workspace);

      new MakeStepTask(lst._ap, lst._ad).doAll(x, dx, xi, dxi, la, dla);

      nu += lst._ad * dnu;
    }

    if (! converged) {
      Log.warn("The algorithm didn't converge in the maximum number of iterations. " +
              "Please consider changing the convergence parameters or increase the maximum number of iterations (" + params._max_iter + ").");
    }

    volatileWorkspace.remove();
    
    return x;
  }

  private static abstract class PDIPMTask<E extends PDIPMTask<E>> extends MRTask<E> {
    Chunk _label, _x;
    Chunk _z;
    Chunk _xi, _dxi, _la, _dla;
    Chunk _tlx, _tux, _xilx, _laux, _d;
    Chunk _dx;
    @Override
    public void map(Chunk[] cs) {
      _label = cs[0];
      _x = cs[1];
      _z = cs[2];
      _xi = cs[3];
      _dxi = cs[4];
      _la = cs[5];
      _dla = cs[6];
      _tlx = cs[7];
      _tux = cs[8];
      _xilx = cs[9];
      _laux = cs[10];
      _d = cs[11];
      _dx = cs[12];
      map();
    }

    abstract void map();
  }

  static class MakeStepTask extends MRTask<MakeStepTask> {
    double _ap;
    double _ad;

    MakeStepTask(double ap, double ad) {
      _ap = ap;
      _ad = ad;
    }
    
    @Override
    public void map(Chunk[] cs) {
      map(cs[0], cs[1], cs[2], cs[3], cs[4], cs[5]);
    }

    public void map(Chunk x, Chunk dx, Chunk xi, Chunk dxi, Chunk la, Chunk dla) {
      for (int i = 0; i < x._len; i++) {
        x.set(i, x.atd(i) + (_ap * dx.atd(i)));
        xi.set(i, xi.atd(i) + (_ad * dxi.atd(i)));
        la.set(i, la.atd(i) + (_ad * dla.atd(i)));
      }
    }
    
  }

  static class LineSearchTask extends PDIPMTask<LineSearchTask> {
    private final double _c_pos;
    private final double _c_neg;

    private double _ap;
    private double _ad;
    
    LineSearchTask(double c_pos, double c_neg) {
      _c_pos = c_pos;
      _c_neg = c_neg;
    }

    @Override
    public void map() {
      map(_label, _tlx, _tux, _xilx, _laux, _xi, _la, _dx, _x, ((C8DVolatileChunk) _dxi).getValues(), ((C8DVolatileChunk) _dla).getValues());
    }

    private void map(Chunk label, Chunk tlx, Chunk tux, Chunk xilx, Chunk laux, Chunk xi, Chunk la, Chunk dx, Chunk x, double[] dxi, double[] dla) {
      for (int i = 0; i < dxi.length; ++i) {
        dxi[i] = tlx.atd(i) - xilx.atd(i) * dx.atd(i) - xi.atd(i);
        dla[i] = tux.atd(i) + laux.atd(i) * dx.atd(i) - la.atd(i);
      }
      double ap = Double.MAX_VALUE;
      double ad = Double.MAX_VALUE;
      for (int i = 0; i < dxi.length; i++) {
        double c = (label.atd(i) > 0.0) ? _c_pos : _c_neg;
        if (dx.atd(i) > 0.0) {
          ap = Math.min(ap, (c - x.atd(i)) / dx.atd(i));
        }
        if (dx.atd(i) < 0.0) {
          ap = Math.min(ap, -x.atd(i)/dx.atd(i));
        }
        if (dxi[i] < 0.0) {
          ad = Math.min(ad, -xi.atd(i) / dxi[i]);
        }
        if (dla[i] < 0.0) {
          ad = Math.min(ad, -la.atd(i) / dla[i]);
        }
      }
      _ap = ap;
      _ad = ad;
    }

    @Override
    public void reduce(LineSearchTask mrt) {
      _ap = Math.min(_ap, mrt._ap);
      _ad = Math.min(_ad, mrt._ad);
    }

    @Override
    public void postGlobal() {
      _ap = Math.min(_ap, 1.0) * 0.99;
      _ad = Math.min(_ad, 1.0) * 0.99;
    }
  }
  
  private static void checkLabel(Vec label) {
    if (label.min() != -1 || label.max() != 1)
      throw new IllegalArgumentException("Expected a binary response encoded as +1/-1");
  }
  
  static class UpdateVarsTask extends PDIPMTask<UpdateVarsTask> {
    private final double _c_pos;
    private final double _c_neg;
    private final double _epsilon_x;
    private final double _t;

    UpdateVarsTask(double c_pos, double c_neg, double epsilon_x, double t) {
      _c_pos = c_pos;
      _c_neg = c_neg;
      _epsilon_x = epsilon_x;
      _t = t;
    }
    
    @Override
    void map() {
      for (int i = 0; i < _z._len; i++) {
        double c = (_label.atd(i) > 0) ?_c_pos : _c_neg;
        double m_lx = Math.max(_x.atd(i), _epsilon_x);
        double m_ux = Math.max(c - _x.atd(i), _epsilon_x);
        double tlxi = 1.0 / (_t * m_lx);
        double tuxi = 1.0 / (_t * m_ux);
        _tlx.set(i, tlxi);
        _tux.set(i, tuxi);
        
        double xilxi = Math.max(_xi.atd(i) / m_lx, _epsilon_x);
        double lauxi = Math.max(_la.atd(i) / m_ux, _epsilon_x);
        _d.set(i, 1.0 / (xilxi + lauxi));
        _xilx.set(i, xilxi);
        _laux.set(i, lauxi);
        
        _z.set(i, tlxi - tuxi - _z.atd(i));
      }
    }
  }
  
  static class CheckConvergenceTask extends PDIPMTask<CheckConvergenceTask> {
    private final double _nu;
    // OUT
    double _resd;
    double _resp;

    CheckConvergenceTask(double nu) {
      _nu = nu;
    }

    @Override
    void map() {
      for (int i = 0; i < _z._len; i++) {
        double zi = _z.atd(i);
        zi += _nu * (_label.atd(i) > 0 ? 1 : -1) - 1.0;
        double temp = _la.atd(i) - _xi.atd(i) + zi;
        _z.set(i, zi);
        _resd += temp * temp;
        _resp += _label.atd(i) * _x.atd(i);
      }
    }

    @Override
    public void reduce(CheckConvergenceTask mrt) {
      _resd += mrt._resd;
      _resp += mrt._resp;
    }

    @Override
    protected void postGlobal() {
      _resp = Math.abs(_resp);
      _resd = Math.sqrt(_resd);
    }
  }

  private static void computePartialZ(Frame rbicf, Vec x, final double tradeoff, Vec z) {
    final Vec[] vecs = ArrayUtils.append(rbicf.vecs(), x);
    final double vz[] = new MatrixMultVecTask().doAll(vecs)._row;
    new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        final int p = cs.length - 2;
        final Chunk x = cs[p];
        final Chunk z = cs[p + 1];
        for (int i = 0; i < cs[0]._len; i++) {
          double s = 0;
          for (int j = 0; j < p; j++) {
            s += cs[j].atd(i) * vz[j];
          }
          z.set(i, s - tradeoff * x.atd(i));
        }
      }
    }.doAll(ArrayUtils.append(vecs, z));
  }
  
  static class MatrixMultVecTask extends MRTask<MatrixMultVecTask> {
    double[] _row;

    @Override
    public void map(Chunk[] cs) {
      final int p = cs.length - 1;
      final Chunk x = cs[p];
      _row = new double[p];
      for (int j = 0; j < p; ++j) {
        double sum = 0.0;
        for (int i = 0; i < cs[0]._len; i++) {
          sum += cs[j].atd(i) * x.atd(i);
        }
        _row[j] = sum;
      }
    }

    @Override
    public void reduce(MatrixMultVecTask mrt) {
      ArrayUtils.add(_row, mrt._row);
    }
  }

  static class SurrogateGapTask extends PDIPMTask<SurrogateGapTask> {
    private final double _c_pos;
    private final double _c_neg;
    
    // OUT
    private double _sum;

    SurrogateGapTask(double c_pos, double c_neg) {
      _c_pos = c_pos;
      _c_neg = c_neg;
    }
    
    @Override
    void map() {
      double s = 0;
      for (int i = 0; i < _x._len; i++) {
        double c = (_label.atd(i) > 0.0) ? _c_pos : _c_neg;
        s += _la.atd(i) * c;
      }
      for (int i = 0; i < _x._len; i++) {
        s += _x.atd(i) * (_xi.atd(i) - _la.atd(i));
      }
      _sum = s;
    }

    @Override
    public void reduce(SurrogateGapTask mrt) {
      _sum += mrt._sum;
    }
  }
  
  static class InitTask extends PDIPMTask<InitTask> {
    private final double _c_pos;
    private final double _c_neg;

    InitTask(double c_pos, double c_neg) {
      _c_pos = c_pos;
      _c_neg = c_neg;
    }

    @Override
    public void map() {
      for (int i = 0; i < _label._len; i++) {
        double c = ((_label.atd(i) > 0) ? _c_pos : _c_neg) / 10;
        _la.set(i, c);
        _xi.set(i, c);
      }
    }
  }

  private static void computeDeltaX(Frame icf, Vec d, Vec label, final double dnu, LLMatrix lra, Vec z, Vec dx) {
    Vec tz = new MRTask() {
      @Override
      public void map(Chunk z, Chunk label, NewChunk tz) {
        for (int i = 0; i < z._len; i++) {
          tz.addNum(z.atd(i) - dnu * label.atd(i));
        }
      }
    }.doAll(Vec.T_NUM, z, label).outputFrame().anyVec();
    try {
      linearSolveViaICFCol(icf, d, tz, lra, dx);
    } finally {
      tz.remove(); // TODO: avoid calculating the `tz`
    }
  }
  
  private static double computeDeltaNu(Frame icf, Vec d, Vec label, Vec z, Vec x, LLMatrix lra) {
    Vec tw = label.makeVolatileDoubles(1)[0];  // TODO: do not allocate temporary vecs
    Vec tl = label.makeVolatileDoubles(1)[0];
    try {
      linearSolveViaICFCol(icf, d, z, lra, tw);
      linearSolveViaICFCol(icf, d, label, lra, tl);
      DeltaNuTask dnt = new DeltaNuTask().doAll(label, tw, tl, x);
      return dnt._sum1 / dnt._sum2;
    } finally {
      tw.remove();
      tl.remove();
    }
  }
  
  static class DeltaNuTask extends MRTask<DeltaNuTask> {
    double _sum1;
    double _sum2;

    @Override
    public void map(Chunk[] cs) {
      map(cs[0], cs[1], cs[2], cs[3]);
    }

    public void map(Chunk label, Chunk tw, Chunk tl, Chunk x) {
      for (int i = 0; i < label._len; i++) {
        _sum1 += label.atd(i) * (tw.atd(i) + x.atd(i));
        _sum2 += label.atd(i) * tl.atd(i);
      }
    }

    @Override
    public void reduce(DeltaNuTask mrt) {
      _sum1 += mrt._sum1;
      _sum2 += mrt._sum2;
    }
  }
  
  private static void linearSolveViaICFCol(Frame icf, Vec d, Vec b, LLMatrix lra, Vec out) {
    final double[] vz = new LSHelper1().doAll(ArrayUtils.append(icf.vecs(), d, b, out))._row;
    final double[] ty = new double[vz.length];
    MatrixUtils.cholForwardSub(lra, vz, ty);
    MatrixUtils.cholBackwardSub(lra, ty, vz);
    new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        final int p = cs.length - 2;
        Chunk d = cs[p];
        Chunk x = cs[p + 1];
        for (int i = 0; i < cs[0]._len; i++) {
          double s = 0.0;
          for (int j = 0; j < p; j++) {
            s += cs[j].atd(i) * vz[j] * d.atd(i);
          }
          x.set(i, x.atd(i) - s);
        }
      }
    }.doAll(ArrayUtils.append(icf.vecs(), d, out));
  }

  static class LSHelper1 extends MRTask<LSHelper1> {
    double[] _row;
    @Override
    public void map(Chunk[] cs) {
      final int p = cs.length - 3;
      _row = new double[p];
      Chunk d = cs[p];
      Chunk b = cs[p + 1];
      double[] z = ((C8DVolatileChunk) cs[p + 2]).getValues();
      for (int i = 0; i < z.length; i++) {
        z[i] = b.atd(i) * d.atd(i);
      }
      for (int j = 0; j < p; j++) {
        double s = 0.0;
        for (int i = 0; i < z.length; i++) {
          s += cs[j].atd(i) * z[i];
        }
        _row[j] = s;
      }
    }

    @Override
    public void reduce(LSHelper1 mrt) {
      ArrayUtils.add(_row, mrt._row);
    }
  }

  static class Params {
    int _max_iter = 30;
    double _weight_positive = 1.0;
    double _weight_negative = 1.0;
    double _hyper_parm = 1.0;
    double _mu_factor = 10.0;
    double _tradeoff = 0;
    double _feasible_threshold = 1.0e-3;
    double _sgap_bound = 1.0e-3;
    double _x_epsilon = 1.0e-9;
  }
  
    
}
