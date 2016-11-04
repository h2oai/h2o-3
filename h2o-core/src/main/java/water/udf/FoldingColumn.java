package water.udf;

import water.fvec.Vec;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * This column depends a plurality of columns
 */
public class FoldingColumn<X, Y> implements Column<Y> {
  private final Foldable<X, Y> f;
  private final Iterable<Column<X>> columns;
      
  @Override public int rowLayout() { 
    Iterator<Column<X>> i = columns.iterator();
    return i.hasNext() ? i.next().rowLayout() : 0;
  }

  @Override public Vec vec() { return new VirtualVec(this); }
  
  public FoldingColumn(Foldable<X, Y> f, Column<X>... columns) {
    this.f = f;
    this.columns = Arrays.asList(columns);
  }

  public FoldingColumn(Foldable<X, Y> f, Iterable<Column<X>> columns) {
    this.f = f;
    this.columns = columns;
  }
  
  @Override public Y get(long idx) {
    Y y = f.initial();
    for (Column<X> col : columns) y = f.apply(y, col.get(idx));
    return y; 
  }

  @Override
  public TypedChunk<Y> chunkAt(int i) {
    List<TypedChunk<X>> chunks = new LinkedList<>();
    for (Column<X> c : columns) chunks.add(c.chunkAt(i));
            
    return new FunChunk(chunks);
  }

  @Override public boolean isNA(long idx) {
    for (Column<X> col : columns) if (col.isNA(idx)) return true;
    return false;
  }

  @Override
  public String getString(long idx) { 
    Y y = get(idx);
    return y == null ? "(N/A)" : String.valueOf(y); 
  }

  private class FunChunk implements TypedChunk<Y> {
    private final List<TypedChunk<X>> chunks;

    public FunChunk(List<TypedChunk<X>> chunks) {
      this.chunks = chunks;
    }

    @Override
    public Y get(int idx) {
      Y y = f.initial();
      for (TypedChunk<X> c : chunks) y = f.apply(y, c.get(idx));
      return y;
    }
  }
}
