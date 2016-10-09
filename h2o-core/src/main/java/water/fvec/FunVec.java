package water.fvec;

import com.google.common.base.Function;
import water.DKV;
import water.H2O;
import water.Key;
import water.MRTask;
import water.exceptions.H2OFailException;
import water.rapids.Env;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNum;
import water.util.Closure;

import java.io.IOException;

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
public class FunVec extends Vec {

  private transient Vec[] dependencies;
  private final Closure fun;

  public FunVec(Key<Vec> key, int rowLayout, Function<?,?> fun, Vec... dependencies) throws IOException {
    super(key, rowLayout, null);
    this.fun=Closure.enclose(fun);
    this.dependencies = dependencies;
    DKV.put(this);
  }

  public FunVec(Vec v, Function<?,?> fun) throws IOException {
    this(v.group().addVec(), v._rowLayout, fun, v);
  }

  public Vec makeVec() {
    Vec v  = new MRTask() {
      @Override public void map(Chunk c, NewChunk nc) {
        for(int i=0;i<c._len;++i)
          nc.addNum(c.atd(i));
      }
    }.doAll(Vec.T_NUM,this).outputFrame().anyVec();
    remove();
    return v;
  }



  @Override public Chunk chunkForChunkIdx(int cidx) {
    Chunk[] cs = new Chunk[dependencies.length];
    for(int i=0; i<cs.length;++i)
      cs[i] = dependencies[i].chunkForChunkIdx(cidx);
    return new FunChunk(fun, this, cs);
  }

  @Override public Vec doCopy() {
    try {
      Vec v = new FunVec(group().addVec(), _rowLayout, fun, dependencies);
      v.setDomain(domain()==null?null:domain().clone());
      return v;
    } catch (IOException ioe) {
      throw new H2OFailException("Copy failed", ioe);
    }
  }

  public static class FunChunk extends Chunk {
    public final Closure fun;
    public final transient Chunk dependencies[];

//    private final Env _env;

    FunChunk(Closure fun, Vec vec, Chunk... c) {

      // set all the chunk fields
      dependencies = c; set_len(dependencies[0]._len);
      _start = dependencies[0]._start; _vec = vec; _cidx = dependencies[0]._cidx;

      this.fun=fun;
    }


    // TODO(vlad): implement other methods, take care of type safety
    // applies the function to a row of doubles
    @SuppressWarnings("unchecked")
    @Override public double atd_impl(int idx) {
      Double neat = (Double) fun.apply(dependencies[0].atd(idx));
      return neat == null ? Double.NaN : neat;
    }

    @Override public long at8_impl(int idx) { throw H2O.unimpl(); }
    @Override public boolean isNA_impl(int idx) { return Double.isNaN(atd_impl(idx)); }  // ouch, not quick! runs thru atd_impl
    // Returns true if the masterVec is missing, false otherwise
    @Override public boolean set_impl(int idx, long l)   { return false; }
    @Override public boolean set_impl(int idx, double d) { return false; }
    @Override public boolean set_impl(int idx, float f)  { return false; }
    @Override public boolean setNA_impl(int idx)         { return false; }
    @Override public NewChunk inflate_impl(NewChunk nc) {
      nc.set_sparseLen(nc.set_len(0));
      for( int i=0; i< _len; i++ )
        if( isNA(i) ) nc.addNA();
        else          nc.addNum(atd(i));
      return nc;
    }
    @Override protected final void initFromBytes () { throw H2O.fail(); }
  }
}
