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
 * per Op per element (per ByteArraySupportedChunk). As has been noted (see e.g. RollupStats),
 * virtual calls are expensive, but the memory savings are substantial.
 *
 * AutoML can freely transform columns without ramification.
 *
 * Each wrapped Vec will track its own transformations, which makes it easy
 * when generating a POJO.
 *
 * A TransformWrappedVec is actually a function of one or more Vec instances.
 *
 * This class exists here so that ByteArraySupportedChunk and NewChunk don't need to become fully public
 * (since java has no friends). Other packages (not just core H2O) depend on this class!
 *
 *
 * @author spencer
 */
public class TransformWrappedVec extends WrappedVec {

  private final AstRoot _fun;

  public TransformWrappedVec(Key key, int rowLayout, AstRoot fun, VecAry masterVecs) {
    super(key, rowLayout, masterVecs);
    _fun=fun;
    DKV.put(this);
  }

  public TransformWrappedVec(VecAry v, AstRoot fun) {
    this(v.group().addVec(), v.rowLayout(), fun, v);
  }

  public Vec makeVec() {
    Vec v  = new MRTask() {
      @Override public void map(ChunkAry c, NewChunkAry nc) {
        for(int i=0;i<c._len;++i)
          nc.addNum(c.atd(i));
      }
    }.doAll(Vec.T_NUM,this).outputFrame().anyVec();
    remove();
    return v;
  }

  @Override public Vec doCopy() {
    Vec v = new TransformWrappedVec(group().addVec(), _rowLayout, _fun, _masterVec);
    v.setDomain(0,domain()==null?null:domain().clone());
    return v;
  }

  @Override
  public DBlock chunkIdx(int cidx) {
    return new TransformWrappedChunk(_fun, this, _masterVec.chunkForChunkIdx(cidx));
  }

  public static class TransformWrappedChunk extends Chunk {
    public final AstRoot _fun;
    public final transient ChunkAry _c;

    private final AstRoot[] _asts;
    private final Env _env;

    TransformWrappedChunk(AstRoot fun, Vec transformWrappedVec, ChunkAry c) {
      // set all the chunk fields
      _c = c;
      _fun=fun;
      _asts = new AstRoot[1+_c._numCols];
      _asts[0]=_fun;
      for(int i=1;i<_asts.length;++i)
        _asts[i] = new AstNum(0);
      _env = new Env(null);
    }


    @Override
    public Chunk deepCopy() {
      return add2Chunk(new NewChunk(Vec.T_NUM),0,_c._len).compress();
    }

    // applies the function to a row of doubles
    @Override public double atd(int idx) {
      if( null==_fun ) return _c.atd(idx);  // simple wrapping of 1 vec
      for(int i=1;i<_asts.length;++i)
        ((AstNum)_asts[i]).setNum(_c.atd(idx,i-1)); // = new AstNum(_c[i-1].atd(idx));
      return _fun.apply(_env,_env.stk(),_asts).getNum();   // Make the call per-row
    }

    @Override public long at8(int idx) { throw H2O.unimpl(); }
    @Override public boolean isNA(int idx) { return Double.isNaN(atd(idx)); }  // ouch, not quick! runs thru atd
    // Returns true if the masterVec is missing, false otherwise
    @Override public boolean set_impl(int idx, long l)   { return false; }
    @Override public boolean set_impl(int idx, double d) { return false; }
    @Override public boolean set_impl(int idx, float f)  { return false; }
    @Override public boolean setNA_impl(int idx)         { return false; }

    @Override
    public DVal getInflated(int i, DVal v) {
      v._t = DVal.type.D;
      v._d = atd(i);
      return v;
    }

    @Override
    public int len() {return _c._len;}
  }
}
