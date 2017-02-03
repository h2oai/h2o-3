package water.rapids.ast.prims.reducers;

import water.H2O;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.ArrayUtils;

import java.util.Arrays;

/**
 */
public abstract class AstCumu extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary","axis"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (cumu x)

  @Override
  public String str() {
    throw H2O.unimpl();
  }

  public abstract double op(double l, double r);

  public abstract double init();

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame f = stk.track(asts[1].exec(env)).getFrame();
    AstRoot axisAR = asts[2];
    for (Vec v:f.vecs()) {
      if(v.isCategorical() || v.isString() || v.isUUID()) throw new IllegalArgumentException(
              "Cumulative functions not applicable to enum, string, or UUID values");
    }
    double axis = axisAR.exec(env).getNum();
    if (axis != 1.0 && axis != 0.0) throw new IllegalArgumentException("Axis must be 0 or 1");
    if (f.numCols() == 1) {
      if (axis == 0.0) {
        AstCumu.CumuTask t = new AstCumu.CumuTask(f.anyVec().nChunks(), init());
        t.doAll(new byte[]{Vec.T_NUM}, f.anyVec());
        final double[] chkCumu = t._chkCumu;
        Vec cumuVec = t.outputFrame().anyVec();
        new MRTask() {
          @Override
          public void map(Chunk c) {
            if (c.cidx() != 0) {
              double d = chkCumu[c.cidx() - 1];
              for (int i = 0; i < c._len; ++i)
                c.set(i, op(c.atd(i), d));
            }
          }
        }.doAll(cumuVec);
        Key<Frame> k = Key.make();
        return new ValFrame(new Frame(k, null, new Vec[]{cumuVec}));
      } else {
        return new ValFrame(new Frame(f));
      }
    }
    else {

      if (axis == 0.0) {  // down the column implementation

        AstCumu.CumuTaskWholeFrame t = new AstCumu.CumuTaskWholeFrame(f.anyVec().nChunks(), init(), f.numCols());
          Frame fr2 = t.doAll(f.numCols(), Vec.T_NUM, f).outputFrame(null, f.names(), null);
          final double[][] chkCumu = t._chkCumu;
          new MRTask() {
            @Override
            public void map(Chunk cs[]) {
              if (cs[0].cidx() != 0) {
                for (int i = 0; i < cs.length; i++) {
                  double d = chkCumu[i][cs[i].cidx() - 1];
                  for (int j = 0; j < cs[i]._len; ++j)
                    cs[i].set(j, op(cs[i].atd(j), d));
                }
              }
            }
          }.doAll(fr2);
          return new ValFrame(new Frame(fr2));

      } else {
        AstCumu.CumuTaskAxis1 t = new AstCumu.CumuTaskAxis1(init());
        Frame fr2 = t.doAll(f.numCols(), Vec.T_NUM, f).outputFrame(null, f.names(), null);
        return new ValFrame(new Frame(fr2));
      }
    }
  }

  protected class CumuTaskAxis1 extends MRTask<AstCumu.CumuTaskAxis1> {
    // apply function along the rows
    final double _init;
    CumuTaskAxis1(double init) {
      _init = init;
    }
    @Override
    public void map(Chunk cs[], NewChunk nc[]) {
      for (int i = 0; i < cs[0].len(); i++) {
        for (int j = 0; j < cs.length; j++) {
          double preVal = j == 0 ? _init : nc[j-1].atd(i);
          nc[j].addNum(op(preVal,cs[j].atd(i)));
        }
      }
    }
  }
  protected class CumuTaskWholeFrame extends MRTask<AstCumu.CumuTaskWholeFrame> {
    final int _nchks;   // IN
    final double _init; // IN
    final int _ncols; // IN
    double[][] _chkCumu;  // OUT, accumulation over each chunk

    CumuTaskWholeFrame(int nchks, double init, int ncols) {
      _nchks = nchks;
      _init = init;
      _ncols = ncols;
    }

    @Override
    public void setupLocal() {
      _chkCumu = new double[_ncols][_nchks];
    }

    @Override
    public void map(Chunk cs[], NewChunk nc[]) {
      double acc[] = new double[cs.length];
      Arrays.fill(acc,_init);
      for (int i = 0; i < cs.length; i++) {
        for (int j = 0; j < cs[i]._len; ++j)
          nc[i].addNum(acc[i] = op(acc[i], cs[i].atd(j)));
        _chkCumu[i][cs[i].cidx()] = acc[i];
      }
    }

    @Override
    public void reduce(AstCumu.CumuTaskWholeFrame t) {
      if (_chkCumu != t._chkCumu) ArrayUtils.add(_chkCumu, t._chkCumu);
    }

    @Override
    public void postGlobal() {
      for (int i = 1; i < _chkCumu.length; i++) {
        for (int j = 1; j < _chkCumu[i].length; ++j) {
          _chkCumu[i][j] = op(_chkCumu[i][j], _chkCumu[i][j - 1]);
        }

      }
    }
  }

  protected class CumuTask extends MRTask<AstCumu.CumuTask> {
    final int _nchks;   // IN
    final double _init; // IN
    double[] _chkCumu;  // OUT, accumulation over each chunk

    CumuTask(int nchks, double init) {
      _nchks = nchks;
      _init = init;
    }

    @Override
    public void setupLocal() {
      _chkCumu = new double[_nchks];
    }

    @Override
    public void map(Chunk c, NewChunk nc) {
      double acc = _init;
      for (int i = 0; i < c._len; ++i)
        nc.addNum(acc = op(acc, c.atd(i)));
      _chkCumu[c.cidx()] = acc;
    }

    @Override
    public void reduce(AstCumu.CumuTask t) {
      if (_chkCumu != t._chkCumu) ArrayUtils.add(_chkCumu, t._chkCumu);
    }

    @Override
    public void postGlobal() {
      for (int i = 1; i < _chkCumu.length; ++i) _chkCumu[i] = op(_chkCumu[i], _chkCumu[i - 1]);
    }
  }
}
