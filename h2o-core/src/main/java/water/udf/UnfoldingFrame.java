package water.udf;

import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static water.udf.DataColumns.Enums;
import static water.udf.DataColumns.Factory;

/**
 * Single column frame that knows its data type
 */
public class UnfoldingFrame<X> extends Frame {
  private final Factory<X> factory;
  private final long len;
  private final Function<Long, List<X>> function;
  private final int width;

  public UnfoldingFrame(Factory<X> factory, long len, Function<Long, List<X>> function, int width) {
    super();
    this.factory = factory;
    this.len = len;
    this.function = function;
    this.width = width;
    assert len   >= 0: "Frame must have a nonnegative length, but found"+len;
    assert width >= 0: "Multicolumn frame must have a nonnegative width, but found"+width;
  }

  final static class UnfoldingEnumFrame extends UnfoldingFrame<Integer> {
    private final String[] domain;
    
    public UnfoldingEnumFrame(long length, Unfoldable<Long, Integer> function, int width, String[] domain) {
      super(Enums(domain), length, function, width);
      this.domain = domain;
      assert domain != null : "An enum must have a domain";
      assert domain.length > 0 : "Domain cannot be empty";
    }
  }
  
  protected List<Vec> makeVecs() throws IOException {
    Vec[] vecs = new Vec[width];

    for (int j = 0; j < width; j++) {
      vecs[j] = Vec.makeZero(len, factory.typeCode());
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
    List<DataColumn<X>> result = new ArrayList<DataColumn<X>>(width);

    for (Vec vec : vecs) result.add(factory.newColumn(vec));
    return result;
  }
  
}
