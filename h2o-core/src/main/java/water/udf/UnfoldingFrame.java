package water.udf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static water.udf.specialized.Enums.enums;

/**
 * Single column frame that knows its data type
 */
public class UnfoldingFrame<X> extends Frame {
  protected final ColumnFactory<X> factory;
  protected final long len;
  protected final Function<Long, List<X>> function;
  protected final int width;

  /** for deserialization (sigh) */
  public UnfoldingFrame() {
    factory = null;
    len = -1;
    function = null;
    width = -1;
  }
  
  public UnfoldingFrame(ColumnFactory<X> factory, long len, Function<Long, List<X>> function, int width) {
    super();
    this.factory = factory;
    this.len = len;
    this.function = function;
    this.width = width;
    assert len   >= 0: "Frame must have a nonnegative length, but found"+len;
    assert width >= 0: "Multicolumn frame must have a nonnegative width, but found"+width;
  }

  public static <X> UnfoldingFrame<X> unfoldingFrame(final ColumnFactory<X> factory, final Column<List<X>> master, int width) {
    return new UnfoldingFrame<X>(factory, master.size(), master, width) {
      @Override protected Vec initVec() {
        Vec v0 = Vec.makeZero(this.len, factory.typeCode());
        v0.align(master.vec());
        return v0;
      }
    };
  }

  static class UnfoldingEnumFrame extends UnfoldingFrame<Integer> {
    private final String[] domain;
    
    /** for deserialization */
    public UnfoldingEnumFrame() {domain = null; }
    
    public UnfoldingEnumFrame(long length, Function<Long, List<Integer>> function, int width, String[] domain) {
      super(enums(domain), length, function, width);
      this.domain = domain;
      assert domain != null : "An enum must have a domain";
      assert domain.length > 0 : "Domain cannot be empty";
    }
  }

  public static <X> UnfoldingEnumFrame UnfoldingEnumFrame(final Column<List<Integer>> master, int width, String[] domain) {
    return new UnfoldingEnumFrame(master.size(), master, width, domain) {
      @Override protected Vec initVec() {
        Vec v0 = Vec.makeZero(this.len, Vec.T_CAT);
        v0.align(master.vec());
        return v0;
      }
    };
  }
  
  protected Vec initVec() {
    return Vec.makeZero(len, factory.typeCode());
  }
  
  protected List<Vec> makeVecs() throws IOException {
    Vec[] vecs = new Vec[width];

    for (int j = 0; j < width; j++) {
      vecs[j] = initVec();
    }

    MRTask task = new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        int size = cs[0].len(); // TODO(vlad): find a solution for empty  
        long start = cs[0].start();
        for (int r = 0; r < size; r++) {
          long i = r + start;
          List<X> values = function.apply(i);
          for (int j = 0; j < cs.length; j++) {
            DataChunk<X> tc = factory.apply(cs[j]);
            tc.set(r, j < values.size() ? values.get(j) : null);
          }          
        }
      }
    };
    
    MRTask mrTask = task.doAll(vecs);
    return Arrays.asList(mrTask._fr.vecs());
  }

  public List<DataColumn<X>> materialize() throws IOException {
    List<Vec> vecs = makeVecs();
    List<DataColumn<X>> result = new ArrayList<>(width);

    for (Vec vec : vecs) result.add(factory.newColumn(vec));
    return result;
  }
  
}
