package water.fvec;

import water.DKV;
import water.H2O;
import water.Key;
import water.MRTask;
import water.rapids.AST;
import water.rapids.ASTParameter;
import water.rapids.Env;

import static water.rapids.ASTParameter.makeNum;

/**
 * This wrapper pushes a transform down into each chunk so that
 * transformations will happen on-the-fly. When wrapped and there
 * are Op instances to be applied, the atd call will supersede the
 * usual chunk-at retrieval with a "special" atd call.
 *
 * Overhead added per element fetch per chunk is another virtual call
 * per Op per element (per Chunk). As has been noted (see e.g. RollupStats),
 * virtual calls are expensive, but the memory savings are substantial.
 *
 * AutoML can freely transform columns without ramification.
 *
 * Each wrapped Vec will track its own transformations, which makes it easy
 * when generating a POJO.
 *
 * A TransformWrappedVec is actually a function of one or more Vec instances.
 *
 * This class exists here so that Chunk and NewChunk don't need to become fully public
 * (since java has no friends). Other packages (not just core H2O) depend on this class!
 *
 *
 * @author spencer
 */
public class TransformWrappedVec extends WrappedVec {

  private final AST _fun;

  public TransformWrappedVec(Key key, int rowLayout, AST fun, VecAry mastreVecs) {
    super(key, rowLayout, mastreVecs);
    _fun=fun;
    DKV.put(this);
  }

  public TransformWrappedVec(Vec v, AST fun) {
    this(v.group().addVec(), v._rowLayout, fun, new VecAry(v));
  }

  public Vec makeVec() {
    Vec v  = (Vec) new MRTask() {
      @Override public void map(Chunk c, NewChunk nc) {
        for(int i=0;i<c._len;++i)
          nc.addNum(c.atd(i));
      }
    }.doAll(1,Vec.T_NUM,new VecAry(this)).outputVecs(null).getVecRaw(0);
    remove();
    return v;
  }



  @Override public SingleChunk chunkForChunkIdx(int cidx) {
    Chunk[] cs = _masterVec.getChunks(cidx).chks();
    SingleChunk sc = new SingleChunk(this,cidx);
    sc._c = new TransformWrappedChunk(_fun, sc, cs);
    return sc;
  }

  @Override public Vec doCopy() {
    throw H2O.unimpl();
  }

  public static class TransformWrappedChunk extends Chunk {
    public final AST _fun;
    public final transient Chunk _c[];

    private final AST[] _asts;
    private final Env _env;

    TransformWrappedChunk(AST fun, SingleChunk sc, Chunk... c) {
      // set all the chunk fields
      _c = c; set_len(_c[0]._len);
      _achunk = sc;
      _fun=fun;
      _asts = new AST[1+_c.length];
      _asts[0]=_fun;
      for(int i=1;i<_asts.length;++i)
        _asts[i] = makeNum(0);
      _env = new Env(null);
    }


    @Override
    public NewChunk add2NewChunk_impl(NewChunk nc, int from, int to) {
      throw H2O.unimpl();
    }

    @Override
    public NewChunk add2NewChunk_impl(NewChunk nc, int[] lines) {
      throw H2O.unimpl();
    }

    // applies the function to a row of doubles
    @Override public double atd_impl(int idx) {
      if( null==_fun ) return _c[0].atd(idx);  // simple wrapping of 1 vec
      for(int i=1;i<_asts.length;++i)
        ((ASTParameter)_asts[i]).setNum(_c[i-1].atd(idx)); // = makeNum(_c[i-1].atd(idx));
      return _fun.apply(_env,_env.stk(),_asts).getNum();   // Make the call per-row
    }

    @Override public long at8_impl(int idx) { throw H2O.unimpl(); }
    @Override public boolean isNA_impl(int idx) { return Double.isNaN(atd_impl(idx)); }  // ouch, not quick! runs thru atd_impl
    // Returns true if the masterVec is missing, false otherwise
    @Override public boolean set_impl(int idx, long l)   { return false; }
    @Override public boolean set_impl(int idx, double d) { return false; }
    @Override public boolean set_impl(int idx, float f)  { return false; }
    @Override public boolean setNA_impl(int idx)         { return false; }
    @Override protected final void initFromBytes () { throw water.H2O.fail(); }
  }
}
