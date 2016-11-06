package water.udf;

import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static water.udf.DataColumns.Enums;
import static water.udf.DataColumns.Factory;

/**
 * Single column frame that knows its data type
 */
public class TypednFrame<X> extends Frame {
  private final Factory<X> factory;
  private final long len;
  private final Unfoldable<Long, X> function;
  private final int width;
  private List<Column<X>> columns;  

  public TypednFrame(Factory<X> factory, long len, Unfoldable<Long, X> function, int width) {
    super();
    this.factory = factory;
    this.len = len;
    this.function = function;
    this.width = width;
    assert len   >= 0: "Frame must have a nonnegative length, but found"+len;
    assert width >= 0: "Multicolumn frame must have a nonnegative width, but found"+width;
  }

  final static class EnumnFrame extends TypednFrame<Integer> {
    private final String[] domain;
    
    public EnumnFrame(long length, Unfoldable<Long, Integer> function, int width, String[] domain) {
      super(Enums(domain), length, function, width);
      this.domain = domain;
      assert domain != null : "An enum must have a domain";
      assert domain.length > 0 : "Domain cannot be empty";
    }
  }
  
  protected Vec makeVec() throws IOException {
    for (int j = 0; j < width; j++) {
      columns.set(j, newColumn(Vec.makeZero(len, factory.typeCode())));
    }
    LinkedList<Object> x;
    throw H2O.unimpl("TODO(vlad): talk with Arno, Pasha");
//    x.iterator()
//    MRTask task = new MRTask() {
//      @Override
//      public void map(Chunk[] cs) {
//        for (Chunk c : cs) {
//          DataChunk<X> tc = factory.apply(c);
//          for (int r = 0; r < c._len; r++) {
//            long i = r + c.start();
//            List<X> values = function.apply(i);
//            for 
//            tc.set(r, function.apply(i));
//          }
//        }
//      }
//    };
//    Vec vec = column.vec();
//    MRTask mrTask = task.doAll(vec);
//    return mrTask._fr.vecs()[0];
  }

  protected DataColumn<X> newColumn(Vec vec) throws IOException {
    return factory.newColumn(vec);
  }

  public DataColumn<X> newColumn() throws IOException {
    return newColumn(makeVec());
  }
  
}
