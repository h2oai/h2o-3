package water.fvec;

import water.*;
import water.rapids.ast.AstPrimitive;
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
 * A TransformWrappedVec is actually a function of one or more Vec instances.
 *
 * This class exists here so that Chunk and NewChunk don't need to become fully public
 * (since java has no friends). Other packages (not just core H2O) depend on this class!
 *
 *
 * @author spencer
 */
public class TransformWrappedVec extends WrappedVec {

  private final Key<Vec>[] _masterVecKeys;
  private volatile transient Vec[] _masterVecs;
  private final TransformFactory<?> _tf;

  public TransformWrappedVec(Key<Vec> key, int rowLayout, TransformFactory<?> fact, Key<Vec>[] masterVecKeys) {
    super(key, rowLayout, null);
    _tf = fact;
    _masterVecKeys = masterVecKeys;
    DKV.put(this);
  }

  @SuppressWarnings("unchecked")
  public TransformWrappedVec(Vec v, AstPrimitive fun) {
    this(v.group().addVec(), v._rowLayout, fun, new Key[]{v._key});
  }

  @SuppressWarnings("unchecked")
  public TransformWrappedVec(Vec[] vecs, TransformFactory<?> fact) {
    this(vecs[0].group().addVec(), vecs[0]._rowLayout, fact, keys(vecs));
  }

  @SuppressWarnings("unchecked")
  private static Key<Vec>[] keys(Vec[] vecs) {
    Key[] keys = new Key[vecs.length];
    for (int i = 0; i < vecs.length; i++)
      keys[i] = vecs[i]._key;
    return keys;
  }
  
  public TransformWrappedVec(Key<Vec> key, int rowLayout, AstPrimitive fun, Key<Vec>[] masterVecKeys) {
    this(key, rowLayout, new AstTransformFactory(fun), masterVecKeys);
  }

  public Vec makeVec() {
    Vec v  = new MRTask() {
      @Override public void map(Chunk c, NewChunk nc) {
        c.extractRows(nc, 0,c._len);
      }
    }.doAll(Vec.T_NUM,this).outputFrame().anyVec();
    remove();
    return v;
  }

  @Override public Chunk chunkForChunkIdx(int cidx) {
    Vec[] masterVecs = _masterVecs;
    if (masterVecs == null) {
      masterVecs = new Vec[_masterVecKeys.length];
      for (int i = 0; i < masterVecs.length; i++) {
        DKV.prefetch(_masterVecKeys[i]);
      }
      for (int i = 0; i < masterVecs.length; i++) {
        masterVecs[i] = _masterVecKeys[i].get();
      }
      _masterVecs = masterVecs; // publish fetched Vecs
    }
    assert _masterVecs != null;
    Chunk[] cs = new Chunk[_masterVecs.length];
    for (int i = 0; i < cs.length; i++) {
      assert _masterVecs[i] != null;
      cs[i] = _masterVecs[i].chunkForChunkIdx(cidx);
      assert cs[i] != null;
    }
    return new TransformWrappedChunk(_tf, this, cs);
  }

  @Override public Vec doCopy() {
    Vec v = new TransformWrappedVec(group().addVec(), _rowLayout, _tf, _masterVecKeys);
    v.setDomain(domain()==null?null:domain().clone());
    return v;
  }

  public static class TransformWrappedChunk extends Chunk {
    
    public final transient Chunk _c[];
    public final transient Transform _t;
    public final TransformFactory<?> _fact;


    TransformWrappedChunk(TransformFactory<?> fact, Vec transformWrappedVec, Chunk... c) {
      // set all the chunk fields
      _c = c; set_len(_c[0]._len);
      _start = _c[0]._start; _vec = transformWrappedVec; _cidx = _c[0]._cidx;
      _fact = fact;
      _t = fact != null ? fact.create(c.length) : null;
    }

    @Override
    public ChunkVisitor processRows(ChunkVisitor nc, int from, int to) {
      throw H2O.unimpl();
    }

    @Override
    public ChunkVisitor processRows(ChunkVisitor nc, int... rows) {
      throw H2O.unimpl();
    }

    // applies the function to a row of doubles
    @Override public double atd_impl(int idx) {
      if( null==_fact ) return _c[0].atd(idx);  // simple wrapping of 1 vec
      _t.reset();
      for(int i = 0; i < _c.length; i++)
        _t.setInput(i, _c[i].atd(idx));
      return _t.apply();   // Make the call per-row
    }

    @Override public long at8_impl(int idx) { throw H2O.unimpl(); }
    @Override public boolean isNA_impl(int idx) { return Double.isNaN(atd_impl(idx)); }  // ouch, not quick! runs thru atd_impl
    // Returns true if the masterVec is missing, false otherwise
    @Override public boolean set_impl(int idx, long l)   { return false; }
    @Override public boolean set_impl(int idx, double d) { return false; }
    @Override public boolean set_impl(int idx, float f)  { return false; }
    @Override public boolean setNA_impl(int idx)         { return false; }

    @Override protected final void initFromBytes () { throw water.H2O.fail(); }

    public Chunk deepCopy() {
      return extractRows(new NewChunk(this),0,_len).compress();
    }
  }

  public interface Transform {
    void reset();
    void setInput(int i, double value);
    double apply();
  }

  public interface TransformFactory<T extends Freezable> extends Freezable<T> {
    Transform create(int n_inputs);
  }

  private static class AstTransformFactory extends Iced<AstTransformFactory> implements TransformFactory<AstTransformFactory> {
    private final AstPrimitive _fun;

    AstTransformFactory(AstPrimitive fun) {
      _fun = fun;
    }

    public AstTransformFactory() {
      this(null);
    }

    @Override
    public Transform create(int n_inputs) {
      return new AstTransform(_fun, n_inputs);
    }
  }

  private static class AstTransform implements Transform {
    private final AstPrimitive _fun;
    private final AstRoot[] _asts;
    private final Env _env;

    AstTransform(AstPrimitive fun, int n) {
      _fun = fun;
      _asts = new AstRoot[1 + n];
      _asts[0] = _fun;
      for (int i = 1; i < _asts.length; i++)
        _asts[i] = new AstNum(0);
      _env = new Env(null);
    }

    @Override
    public void setInput(int i, double value) {
      ((AstNum) _asts[i + 1]).setNum(value);
    }
    @Override
    public double apply() {
      return _fun.apply(_env,_env.stk(),_asts).getNum();
    }

    @Override
    public void reset() {
      // no need to do anything
    }
  }

}
