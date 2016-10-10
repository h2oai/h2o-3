package water.fvec;

import water.DKV;
import water.H2O;
import water.Key;
import water.MRTask;
import water.rapids.ast.AstRoot;
import water.rapids.Env;
import water.rapids.ast.params.AstNum;

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
 * A AstVec is actually a function of one or more Vec instances.
 *
 * This class exists here so that Chunk and NewChunk don't need to become fully public
 * (since java has no friends). Other packages (not just core H2O) depend on this class!
 *
 *
 * @author spencer
 */
public class AstVec extends Vec {

  private final Key<Vec>[] _masterVecKeys;
  private transient Vec[] _masterVecs;
  private final AstRoot _fun;

  public AstVec(Key<Vec> key, int rowLayout, AstRoot fun, Key<Vec>... masterVecKeys) {
    super(key, rowLayout, null);
    _fun=fun;
    _masterVecKeys = masterVecKeys;
    DKV.put(this);
  }

  public AstVec(Vec v, AstRoot fun) {
    this(v.group().addVec(), v._rowLayout, fun, v._key);
  }

  @Override public Chunk chunkForChunkIdx(int cidx) {
    Chunk[] cs = new Chunk[_masterVecKeys.length];
    if( _masterVecs==null )
      _masterVecs = new Vec[_masterVecKeys.length];
    for(int i=0; i<cs.length;++i) {
      final Vec vec = _masterVecs[i] != null ? _masterVecs[i] : _masterVecKeys[i].get();
      _masterVecs[i] = vec;
      cs[i] = vec.chunkForChunkIdx(cidx);
    }

    return new AstChunk(_fun, this, cs);
  }

  @Override public Vec doCopy() {
    Vec v = new AstVec(group().addVec(), _rowLayout, _fun, _masterVecKeys);
    v.setDomain(domain()==null?null:domain().clone());
    return v;
  }

  public static class AstChunk extends ImmutableChunk {
    public final AstRoot _fun;
    public final transient Chunk _c[];

    private final AstRoot[] _asts;
    private final Env _env;

    AstChunk(AstRoot fun, Vec vec, Chunk... c) {

      // set all the chunk fields
      _c = c; set_len(_c[0]._len);
      _start = _c[0]._start; _vec = vec; _cidx = _c[0]._cidx;

      _fun=fun;
      _asts = new AstRoot[1+_c.length];
      _asts[0]=_fun;
      for(int i=1;i<_asts.length;++i)
        _asts[i] = new AstNum(0);
      _env = new Env(null);
    }


    // applies the function to a row of doubles
    @Override public double atd_impl(int idx) {
      if( null==_fun ) return _c[0].atd(idx);  // simple wrapping of 1 vec
      for(int i=1;i<_asts.length;++i)
        ((AstNum)_asts[i]).setNum(_c[i-1].atd(idx)); // = new AstNum(_c[i-1].atd(idx));
      return _fun.apply(_env,_env.stk(),_asts).getNum();   // Make the call per-row
    }

    @Override public long at8_impl(int idx) { throw H2O.unimpl(); }
    @Override public boolean isNA_impl(int idx) { return Double.isNaN(atd_impl(idx)); }  // ouch, not quick! runs thru atd_impl
  }
}
