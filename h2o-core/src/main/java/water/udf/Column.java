package water.udf;

import water.Iced;
import water.fvec.Chunk;
import water.fvec.Vec;

import java.io.Serializable;

/**
 * Generic typed data column
 * 
 * This is a type-aware representation of id -> value accessors.
 * E.g. our Vec has no clue about the data type. This one, if built on a Vec,
 * does, because we provide the type.
 * More, type may be something totally different from the standard four data types
 * that are hard-coded in Vecs and Chunks.
 * 
 * So that's why we have this interface to be an extension of Function&lt;Long, T>.
 * Due to some hard-coded peculiar features, we need to hold a pointer to a Vec (that helps
 * us to materialize the data if needed).
 * 
 * Of course somewhere deep inside, the data are split into chunks; so we have here
 * a method chunkAt(i) that returns a <i>TypedChunk&lt;T></i>. 
 * 
 * In general, the interface is similar, in its api, to Vecs.
 * 
 * But, unlike Vec, any value T can be a type of column data. Does not have to be Serializable,
 * for instance.
 */
public interface Column<T> extends Function<Long, T>, Vec.Holder {
  T apply(long idx);
  TypedChunk<T> chunkAt(int i);
  
  boolean isNA(long idx);
  
  int rowLayout();
  long size();

  boolean isCompatibleWith(Column<?> ys);
}
