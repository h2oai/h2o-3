package water.udf;

import water.fvec.Chunk;
import water.fvec.RawChunk;
import water.fvec.Vec;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * This column depends a plurality of columns
 */
public class FoldingColumn<X, Y> extends FunColumnBase<Y> {
  private final Foldable<X, Y> f;
  private final Iterable<Column<X>> columns;
      
  @Override public int rowLayout() { 
    Iterator<Column<X>> i = columns.iterator();
    return i.hasNext() ? i.next().rowLayout() : 0;
  }

  @Override public Vec vec() { return new VirtualVec<>(this); }
  
  public FoldingColumn(Foldable<X, Y> f, Column<X>... columns) {
    super(columns.length == 0 ? null : columns[0]);
    this.f = f;
    this.columns = Arrays.asList(columns);
  }

  public FoldingColumn(Foldable<X, Y> f, Iterable<Column<X>> columns) {
    super(columns.iterator().next());
    this.f = f;
    this.columns = columns;
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
    int length = 0;
    for (TypedChunk<X> c : chunks) length = c.length();
            
    return new FunChunk(chunks, length);
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

  private class FunChunk extends DependentChunk<Y> {
    private final List<TypedChunk<X>> chunks;
    private final int length;

//    public FunChunk() {
//      chunks = null;
//      length = 0;
//      System.out.println("oops");
//    }
    
    public FunChunk(List<TypedChunk<X>> chunks, int length) {
      super(chunks.get(0));
      this.chunks = chunks;
      this.length = length;
    }

    @Override public int length() { return length; }

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
}
