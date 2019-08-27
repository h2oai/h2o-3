/*
Copyright 2007 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package hex.psvm.psvm;

import water.Iced;
import water.MRTask;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.Log;

/**
 * Implementation of Primal-Dual Interior Point Method based on https://static.googleusercontent.com/media/research.google.com/en//pubs/archive/34638.pdf
 *
 * This implementation is based on and takes clues from the reference PSVM implementation in C++: 
 *    https://code.google.com/archive/p/psvm/source/default/source
 *    original code: Copyright 2007 Google Inc., Apache License, Version 2.0   
 */
public class PrimalDualIPM {

  public static Vec solve(Frame rbicf, Vec label, Parms params, ProgressObserver observer) {
    checkLabel(label);

    Frame volatileWorkspace = makeVolatileWorkspace(label,
            "z", "xi", "dxi", "la", "dla", "tlx", "tux", "xilx", "laux", "d", "dx");
    try {
      return solve(rbicf, label, params, volatileWorkspace, observer);
    } finally {
      volatileWorkspace.remove();
    }
  }

  private static Vec solve(Frame rbicf, Vec label, Parms params, Frame volatileWorkspace, ProgressObserver observer) {
    Frame workspace = new Frame(new String[]{"label"}, new Vec[]{label});
    workspace.add("x", label.makeZero());
    workspace.add(volatileWorkspace);
    
    new InitTask(params).doAll(workspace);

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
    final long num_constraints = rbicf.numRows() * 2;
    for (int iter = 0; iter < params._max_iter; iter++) {
      final double eta = new SurrogateGapTask(params).doAll(workspace)._sum;
      final double t = (params._mu_factor * num_constraints) / eta;
      Log.info("Surrogate gap before iteration " + iter + ": " + eta + "; t: " + t);

      computePartialZ(rbicf, x, params._tradeoff, z);
      CheckConvergenceTask cct = new CheckConvergenceTask(params, nu).doAll(workspace);
      Log.info("Residual (primal): " + cct._resp + "; residual (dual): " + cct._resd + ". Feasible threshold: " + params._feasible_threshold);
      converged = cct._resp <= params._feasible_threshold && cct._resd <= params._feasible_threshold && eta <= params._sgap_threshold;
      if (observer != null) {
        observer.reportProgress(iter, eta, cct._resp, cct._resd, converged);
      }
      if (converged) {
        break;
      }

      new UpdateVarsTask(params, t).doAll(workspace);

      LLMatrix icfA = MatrixUtils.productMtDM(rbicf, d);
      icfA.addUnitMat();
      LLMatrix lra = icfA.cf();

      final double dnu = computeDeltaNu(rbicf, d, label, z, x, lra);
      computeDeltaX(rbicf, d, label, dnu, lra, z, dx);
      
      LineSearchTask lst = new LineSearchTask(params).doAll(workspace);

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
    transient Chunk _label, _x;
    transient Chunk _z;
    transient Chunk _xi, _dxi, _la, _dla;
    transient Chunk _tlx, _tux, _xilx, _laux, _d;
    transient Chunk _dx;

    final double _c_pos;
    final double _c_neg;

    PDIPMTask(Parms params) {
      _c_pos = params._c_pos;
      _c_neg = params._c_neg;
    }

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
    // OUT
    private double _ap;
    private double _ad;
    
    LineSearchTask(Parms params) {
      super(params);
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
    private final double _epsilon_x;
    private final double _t;

    UpdateVarsTask(Parms params, double t) {
      super(params);
      _epsilon_x = params._x_epsilon;
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

    CheckConvergenceTask(Parms params, double nu) {
      super(params);
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
    final double vz[] = MatrixUtils.productMtv(rbicf, x);
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
    }.doAll(ArrayUtils.append(rbicf.vecs(), x, z));
  }

  static class SurrogateGapTask extends PDIPMTask<SurrogateGapTask> {
    // OUT
    private double _sum;

    SurrogateGapTask(Parms params) {
      super(params);
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
    InitTask(Parms params) {
      super(params);
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
    Vec tz = new TransformWrappedVec(new Vec[]{z, label}, new LinearCombTransformFactory(1.0, -dnu));
    try {
      linearSolveViaICFCol(icf, d, tz, lra, dx);
    } finally {
      tz.remove();
    }
  }

  private static class LinearCombTransformFactory
          extends Iced<LinearCombTransformFactory>
          implements TransformWrappedVec.TransformFactory<LinearCombTransformFactory>  {

    private final double[] _coefs;

    public LinearCombTransformFactory() { // to avoid the "Externalizable" warning
      _coefs = new double[0];
    }

    LinearCombTransformFactory(double... coefs) {
      _coefs = coefs;
    }

    @Override
    public TransformWrappedVec.Transform create(int n_inputs) {
      if (n_inputs != _coefs.length) {
        throw new IllegalArgumentException("Expected " + _coefs.length + " inputs, got: " + n_inputs);
      }
      return new LinearCombTransform(_coefs);
    }
  }

  private static class LinearCombTransform implements TransformWrappedVec.Transform {
    private final double[] _coefs;
    double _sum;

    LinearCombTransform(double[] coefs) {
      _coefs = coefs;
    }

    @Override
    public void reset() {
      _sum = 0;
    }

    @Override
    public void setInput(int i, double value) {
      _sum += value * _coefs[i];
    }

    @Override
    public double apply() {
      return _sum;
    }
  }

  
  private static double computeDeltaNu(Frame icf, Vec d, Vec label, Vec z, Vec x, LLMatrix lra) {
    double[] vz = partialLinearSolveViaICFCol(icf, d, z, lra);
    double[] vl = partialLinearSolveViaICFCol(icf, d, label, lra);
    DeltaNuTask dnt = new DeltaNuTask(vz, vl).doAll(ArrayUtils.append(icf.vecs(), d, z, label, x));
    return dnt._sum1 / dnt._sum2;
  }

  static class DeltaNuTask extends MRTask<DeltaNuTask> {
    // IN
    private final double[] _vz;
    private final double[] _vl;
    // OUT
    double _sum1;
    double _sum2;

    DeltaNuTask(double[] vz, double[] vl) {
      _vz = vz;
      _vl = vl;
    }

    public void map(Chunk[] cs) {
      final int p = cs.length - 4;
      Chunk d = cs[p];
      Chunk z = cs[p + 1];
      Chunk label = cs[p + 2];
      Chunk x = cs[p + 3];

      for (int i = 0; i < label._len; i++) {
        double tw = z.atd(i);
        double tl = label.atd(i);
        for (int j = 0; j < p; j++) {
          tw -= cs[j].atd(i) * _vz[j];
          tl -= cs[j].atd(i) * _vl[j];
        }
        _sum1 += label.atd(i) * (tw * d.atd(i) + x.atd(i));
        _sum2 += label.atd(i) * tl * d.atd(i);
      }
    }

    @Override
    public void reduce(DeltaNuTask mrt) {
      _sum1 += mrt._sum1;
      _sum2 += mrt._sum2;
    }
  }

  private static double[] partialLinearSolveViaICFCol(Frame icf, Vec d, Vec b, LLMatrix lra) {
    final double[] vz = new LSHelper1(false).doAll(ArrayUtils.append(icf.vecs(), d, b))._row;
    return lra.cholSolve(vz);
  }
  
  private static void linearSolveViaICFCol(Frame icf, Vec d, Vec b, LLMatrix lra, Vec out) {
    final double tmp[] = new LSHelper1(true).doAll(ArrayUtils.append(icf.vecs(), d, b, out))._row;
    final double[] vz = lra.cholSolve(tmp);
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
    // IN
    private final boolean _output_z;
    // OUT
    double[] _row;

    LSHelper1(boolean output_z) {
      _output_z = output_z;
    }

    @Override
    public void map(Chunk[] cs) {
      final int p = cs.length - (_output_z ? 3 : 2);
      _row = new double[p];
      Chunk d = cs[p];
      Chunk b = cs[p + 1];
      double[] z = _output_z ? ((C8DVolatileChunk) cs[p + 2]).getValues() : new double[d._len];
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

  public static class Parms {
    
    public Parms() {
      super();
    }

    public Parms(double c_pos, double c_neg) {
      _c_pos = c_pos;
      _c_neg = c_neg;
    }

    public int _max_iter = 200;
    public double _mu_factor = 10.0;
    public double _tradeoff = 0;
    public double _feasible_threshold = 1.0e-3;
    public double _sgap_threshold = 1.0e-3;
    public double _x_epsilon = 1.0e-9;

    public double _c_neg = Double.NaN;
    public double _c_pos = Double.NaN;
  }

  private static Frame makeVolatileWorkspace(Vec blueprintVec, String... names) {
    return new Frame(names, blueprintVec.makeVolatileDoubles(names.length));
  }

  public interface ProgressObserver {
    void reportProgress(int iter, double sgap, double resp, double resd, boolean converged);
  }

}
