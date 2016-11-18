package water.udf;

import com.google.common.collect.Lists;
import water.fvec.Chunk;
import water.fvec.RawChunk;
import water.fvec.Vec;

import java.util.*;

/**
 * This column depends a plurality of columns
 */
public class FoldingColumn<X, Y> extends FunColumnBase<Y> {
  private final Foldable<X, Y> f;
  private final Column<X>[] columns;
      
  @Override public int rowLayout() { 
    return columns.length > 0 ? columns[0].rowLayout() : 0;
  }

  /**
   * deserialization :(
   */
  public FoldingColumn() {
    f = null; columns = null;
  }
  
  public FoldingColumn(Foldable<X, Y> f, Column<X>... columns) {
    super(columns.length == 0 ? null : columns[0]);
    this.f = f;
    this.columns = columns;
    if (columns.length > 1) {
      Column<X> c0 = columns[0];
      for (int i = 1; i < columns.length; i++) {
        Column<X> c = columns[i];
        assert c0.isCompatibleWith(c) : "Columns must be compatible; " + c0 + " vs #" + i + ": " + c;
      }
    }
  }


  @SuppressWarnings("unchecked")
  public FoldingColumn(Foldable<X, Y> f, Iterable<Column<X>> columns) {
    super(columns.iterator().next());
    this.f = f;
    this.columns = (Column<X>[])Lists.newArrayList(columns).toArray();
  }
  
  @Override public Y get(long idx) {
    Y y = f.initial();
    for (Column<X> col : columns) y = f.apply(y, col.apply(idx));
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

  private class FunChunk extends DependentChunk<Y> {
    private final List<TypedChunk<X>> chunks;
    
    public FunChunk(List<TypedChunk<X>> chunks) {
      super(chunks.get(0));
      this.chunks = chunks;
    }

    private RawChunk myChunk = new RawChunk(this);

    @Override public Vec vec() { return FoldingColumn.this.vec(); }

    @Override public Chunk rawChunk() { return myChunk; }

    @Override public boolean isNA(int i) {
      for (TypedChunk<X> c : chunks) if (c.isNA(i)) return true;
      return false;
    }

    @Override
    public Y get(int idx) {
      Y y = f.initial();
      for (TypedChunk<X> c : chunks) y = f.apply(y, c.get(idx));
      return y;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof FoldingColumn) {
      FoldingColumn other = (FoldingColumn) o;
      return equal(f, other.f) && Arrays.equals(columns, other.columns);
    }
    return false;

  }

  @Override
  public int hashCode() {
    return 61 * Arrays.hashCode(columns) + hashCode(f);
  }
}
