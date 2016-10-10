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
 * This kind of Vec uses a function to calculate its values in runtime.
 * It depends on one or more Vec instances.
 *
 * The function provided in serialized in Closure, to be called later on at the point of call.
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

  public static class FunChunk extends ImmutableChunk {
    public final Closure fun;
    public final transient Chunk dependencies[];

    FunChunk(Closure fun, Vec vec, Chunk... c) {

      // set all the chunk fields
      dependencies = c;
      set_len(dependencies[0]._len);
      _start = dependencies[0]._start;
      _vec = vec;
      _cidx = dependencies[0]._cidx;

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
  }
}
