package hex.glm;

import water.Iced;
import water.MemoryManager;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.ArrayUtils;
import water.util.FrameUtils;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by tomas on 9/15/17.
 */
public final class BetaConstraint extends Iced {
  private GLM glm;
  double[] _betaStart;
  double[] _betaGiven;
  double[] _rho;
  double[] _betaLB;
  double[] _betaUB;

  public BetaConstraint(GLM glm) {
    this.glm = glm;
    if (glm._parms._non_negative) setNonNegative();
  }
  private BetaConstraint(){}

  public void setNonNegative() {
    if (_betaLB == null) {
      _betaLB = MemoryManager.malloc8d(glm._dinfo.fullN() + 1);
      _betaLB[glm._dinfo.fullN()] = Double.NEGATIVE_INFINITY;
    } else for (int i = 0; i < _betaLB.length - 1; ++i)
      _betaLB[i] = Math.max(0, _betaLB[i]);
    if (_betaUB == null) {
      _betaUB = MemoryManager.malloc8d(glm._dinfo.fullN() + 1);
      Arrays.fill(_betaUB, Double.POSITIVE_INFINITY);
    }
  }

  public double applyBounds(double d, int i) {
    if (_betaLB != null && d < _betaLB[i])
      return _betaLB[i];
    if (_betaUB != null && d > _betaUB[i])
      return _betaUB[i];
    return d;
  }

  public BetaConstraint(GLM glm, Frame beta_constraints) {
    this.glm = glm;
    Vec v = beta_constraints.vec("names");
    String[] dom;
    int[] map;
    if (v.isString()) {
      dom = new String[(int) v.length()];
      map = new int[dom.length];
      BufferedString tmpStr = new BufferedString();
      for (int i = 0; i < dom.length; ++i) {
        dom[i] = v.atStr(tmpStr, i).toString();
        map[i] = i;
      }
      // check for dups
      String[] sortedDom = dom.clone();
      Arrays.sort(sortedDom);
      for (int i = 1; i < sortedDom.length; ++i)
        if (sortedDom[i - 1].equals(sortedDom[i]))
          throw new IllegalArgumentException("Illegal beta constraints file, got duplicate constraint for predictor '" + sortedDom[i - 1] + "'!");
    } else if (v.isCategorical()) {
      dom = v.domain();
      map = FrameUtils.asInts(v);
      // check for dups
      int[] sortedMap = MemoryManager.arrayCopyOf(map, map.length);
      Arrays.sort(sortedMap);
      for (int i = 1; i < sortedMap.length; ++i)
        if (sortedMap[i - 1] == sortedMap[i])
          throw new IllegalArgumentException("Illegal beta constraints file, got duplicate constraint for predictor '" + dom[sortedMap[i - 1]] + "'!");
    } else
      throw new IllegalArgumentException("Illegal beta constraints file, names column expected to contain column names (strings)");
    // for now only categoricals allowed here
    String[] names = ArrayUtils.append(glm._dinfo.coefNames(), "Intercept");
    if (!Arrays.deepEquals(dom, names)) { // need mapping
      HashMap<String, Integer> m = new HashMap<String, Integer>();
      for (int i = 0; i < names.length; ++i)
        m.put(names[i], i);
      int[] newMap = MemoryManager.malloc4(dom.length);
      for (int i = 0; i < map.length; ++i) {
        if (glm._removedCols.contains(dom[map[i]])) {
          newMap[i] = -1;
          continue;
        }
        Integer I = m.get(dom[map[i]]);
        if (I == null) {
          throw new IllegalArgumentException("Unrecognized coefficient name in beta-constraint file, unknown name '" + dom[map[i]] + "'");
        }
        newMap[i] = I;
      }
      map = newMap;
    }
    final int numoff = glm._dinfo.numStart();
    String[] valid_col_names = new String[]{"names", "beta_given", "beta_start", "lower_bounds", "upper_bounds", "rho", "mean", "std_dev"};
    Arrays.sort(valid_col_names);
    for (String s : beta_constraints.names())
      if (Arrays.binarySearch(valid_col_names, s) < 0)
        glm.error("beta_constraints", "Unknown column name '" + s + "'");
    if ((v = beta_constraints.vec("beta_start")) != null) {
      _betaStart = MemoryManager.malloc8d(glm._dinfo.fullN() + (glm._dinfo._intercept ? 1 : 0));
      for (int i = 0; i < (int) v.length(); ++i)
        if (map[i] != -1)
          _betaStart[map[i]] = v.at(i);
    }
    if ((v = beta_constraints.vec("beta_given")) != null) {
      _betaGiven = MemoryManager.malloc8d(glm._dinfo.fullN() + (glm._dinfo._intercept ? 1 : 0));
      for (int i = 0; i < (int) v.length(); ++i)
        if (map[i] != -1)
          _betaGiven[map[i]] = v.at(i);
    }
    if ((v = beta_constraints.vec("upper_bounds")) != null) {
      _betaUB = MemoryManager.malloc8d(glm._dinfo.fullN() + (glm._dinfo._intercept ? 1 : 0));
      Arrays.fill(_betaUB, Double.POSITIVE_INFINITY);
      for (int i = 0; i < (int) v.length(); ++i)
        if (map[i] != -1)
          _betaUB[map[i]] = v.at(i);
    }
    if ((v = beta_constraints.vec("lower_bounds")) != null) {
      _betaLB = MemoryManager.malloc8d(glm._dinfo.fullN() + (glm._dinfo._intercept ? 1 : 0));
      Arrays.fill(_betaLB, Double.NEGATIVE_INFINITY);
      for (int i = 0; i < (int) v.length(); ++i)
        if (map[i] != -1)
          _betaLB[map[i]] = v.at(i);
    }
    if ((v = beta_constraints.vec("rho")) != null) {
      _rho = MemoryManager.malloc8d(glm._dinfo.fullN() + (glm._dinfo._intercept ? 1 : 0));
      for (int i = 0; i < (int) v.length(); ++i)
        if (map[i] != -1)
          _rho[map[i]] = v.at(i);
    }
    // mean override (for data standardization)
    if ((v = beta_constraints.vec("mean")) != null) {
      glm._parms._stdOverride = true;
      for (int i = 0; i < v.length(); ++i) {
        if (!v.isNA(i) && map[i] != -1) {
          int idx = map == null ? i : map[i];
          if (idx > glm._dinfo.numStart() && idx < glm._dinfo.fullN()) {
            glm._dinfo._normSub[idx - glm._dinfo.numStart()] = v.at(i);
          } else {
            // categorical or Intercept, will be ignored
          }
        }
      }
    }
    // standard deviation override (for data standardization)
    if ((v = beta_constraints.vec("std_dev")) != null) {
      glm._parms._stdOverride = true;
      for (int i = 0; i < v.length(); ++i) {
        if (!v.isNA(i) && map[i] != -1) {
          int idx = map == null ? i : map[i];
          if (idx > glm._dinfo.numStart() && idx < glm._dinfo.fullN()) {
            glm._dinfo._normMul[idx - glm._dinfo.numStart()] = 1.0 / v.at(i);
          } else {
            // categorical or Intercept, will be ignored
          }
        }
      }
    }
    if (glm._dinfo._normMul != null) {
      double normG = 0, normS = 0, normLB = 0, normUB = 0;
      for (int i = numoff; i < glm._dinfo.fullN(); ++i) {
        double s = glm._dinfo._normSub[i - numoff];
        double d = 1.0 / glm._dinfo._normMul[i - numoff];
        if (_betaUB != null && !Double.isInfinite(_betaUB[i])) {
          normUB *= s;
          _betaUB[i] *= d;
        }
        if (_betaLB != null && !Double.isInfinite(_betaUB[i])) {
          normLB *= s;
          _betaLB[i] *= d;
        }
        if (_betaGiven != null) {
          normG += _betaGiven[i] * s;
          _betaGiven[i] *= d;
        }
        if (_betaStart != null) {
          normS += _betaStart[i] * s;
          _betaStart[i] *= d;
        }
      }
      if (glm._dinfo._intercept) {
        int n = glm._dinfo.fullN();
        if (_betaGiven != null)
          _betaGiven[n] += normG;
        if (_betaStart != null)
          _betaStart[n] += normS;
        if (_betaLB != null)
          _betaLB[n] += normLB;
        if (_betaUB != null)
          _betaUB[n] += normUB;
      }
    }
    if (_betaStart == null && _betaGiven != null)
      _betaStart = _betaGiven.clone();
    if (_betaStart != null) {
      if (_betaLB != null || _betaUB != null) {
        for (int i = 0; i < _betaStart.length; ++i) {
          if (_betaLB != null && _betaLB[i] > _betaStart[i])
            _betaStart[i] = _betaLB[i];
          if (_betaUB != null && _betaUB[i] < _betaStart[i])
            _betaStart[i] = _betaUB[i];
        }
      }
    }
    if (glm._parms._non_negative) setNonNegative();
    check();
  }

  public String toString() {
    double[][] ary = new double[_betaGiven.length][3];

    for (int i = 0; i < _betaGiven.length; ++i) {
      ary[i][0] = _betaGiven[i];
      ary[i][1] = _betaLB[i];
      ary[i][2] = _betaUB[i];
    }
    return ArrayUtils.pprint(ary);
  }

  public boolean hasBounds() {
    if (_betaLB != null)
      for (double d : _betaLB)
        if (!Double.isInfinite(d)) return true;
    if (_betaUB != null)
      for (double d : _betaUB)
        if (!Double.isInfinite(d)) return true;
    return false;
  }

  public boolean hasProximalPenalty() {
    return _betaGiven != null && _rho != null && ArrayUtils.countNonzeros(_rho) > 0;
  }

  public void adjustGradient(double[] beta, double[] grad) {
    if (_betaGiven != null && _rho != null) {
      for (int i = 0; i < _betaGiven.length; ++i) {
        double diff = beta[i] - _betaGiven[i];
        grad[i] += _rho[i] * diff;
      }
    }
  }

  double proxPen(double[] beta) {
    double res = 0;
    if (_betaGiven != null && _rho != null) {
      for (int i = 0; i < _betaGiven.length; ++i) {
        double diff = beta[i] - _betaGiven[i];
        res += _rho[i] * diff * diff;
      }
      res *= .5;
    }
    return res;
  }

  public void check() {
    if (_betaLB != null && _betaUB != null)
      for (int i = 0; i < _betaLB.length; ++i)
        if (!(_betaLB[i] <= _betaUB[i]))
          throw new IllegalArgumentException("lower bounds must be <= upper bounds, " + _betaLB[i] + " !<= " + _betaUB[i]);
  }


  public BetaConstraint filterExpandedColumns(int[] activeCols) {
    BetaConstraint res = new BetaConstraint();
    if (_betaLB != null)
      res._betaLB = ArrayUtils.select(_betaLB, activeCols);
    if (_betaUB != null)
      res._betaUB = ArrayUtils.select(_betaUB, activeCols);
    if (_betaGiven != null)
      res._betaGiven = ArrayUtils.select(_betaGiven, activeCols);
    if (_rho != null)
      res._rho = ArrayUtils.select(_rho, activeCols);
    if (_betaStart != null)
      res._betaStart = ArrayUtils.select(_betaStart, activeCols);
    return res;
  }
}
