package water.rapids.ast.prims.reducers;

import water.H2O;
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

/**
 */
public abstract class AstCumu extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
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

    if (f.numCols() != 1) throw new IllegalArgumentException("Must give a single numeric column.");
    if (!f.anyVec().isNumeric()) throw new IllegalArgumentException("Column must be numeric.");

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
    return new ValFrame(new Frame(cumuVec));
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
