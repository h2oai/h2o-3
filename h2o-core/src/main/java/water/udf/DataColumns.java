package water.udf;

import water.fvec.Chunk;
import water.fvec.Vec;

import java.io.IOException;
import java.util.List;

/**
 * An adapter to Vec, allows type-safe access to data
 */
public class DataColumns {

  protected DataColumns(){}

  public static abstract class BaseFactory<T> 
      implements ColumnFactory<T> {
    public final byte typeCode;
    public final String name;
    protected BaseFactory(byte typeCode, String name) {
      this.typeCode = typeCode;
      this.name = name;
    }
    
    public byte typeCode() { return typeCode; }

    public Vec initVec(long length) {
      return Vec.makeZero(length, typeCode());
    }
    
    public Vec initVec(Column<?> master) {
      Vec vec = Vec.makeZero(master.size(), typeCode());
      vec.align(master.vec());
      return vec;
    }

    public abstract DataChunk<T> apply(final Chunk c);
    public abstract DataColumn<T> newColumn(Vec vec);

    public DataColumn<T> newColumn(long len, final Function<Long, T> f) throws IOException {
      return new TypedFrame<>(this, len, f).newColumn();
    }

    public DataColumn<T> materialize(Column<T> xs) throws IOException {
      return TypedFrame.forColumn(this, xs).newColumn();
    }

    public DataColumn<T> newColumn(final List<T> xs) throws IOException {
      return newColumn(xs.size(), Functions.onList(xs));
    }
    
    @Override public String toString() { return name; }
  }

  // We may never need BufferedStrings
//  public static class OfBS extends OnVector<BufferedString> {
//    public OfBS(Vec vec) {
//      super(vec, Vec.T_STR);
//    }
//
//    @Override
//    public BufferedString get(long idx) {
//      BufferedString bs = new BufferedString();
//      return vec.atStr(bs, idx);
//    }
//  }

  //-------------------------------------------------------------

// TODO(vlad): figure out if we should support UUIDs  
//  public static final Factory<UUID> UUIDs = new Factory<UUID>(Vec.T_UUID) {
//
//    @Override public DataChunk<UUID> apply(final Chunk c) {
//      return new DataChunk<UUID>(c) {
//        @Override public UUID get(int idx) { return isNA(idx) ? null : new UUID(c.at16h(idx), c.at16l(idx)); }
//        @Override public void set(int idx, UUID value) { c.set(idx, value); }
//      };
//    }
//
//    @Override public DataColumn<UUID> newColumn(final Vec vec) {
//      if (vec.get_type() != Vec.T_UUID)
//        throw new IllegalArgumentException("Expected a type UUID, got " + vec.get_type_str());
//      return new DataColumn<UUID>(vec, typeCode, this) {
//        @Override public UUID get(long idx) { return isNA(idx) ? null : new UUID(vec.at16h(idx), vec.at16l(idx)); }
//        @Override public void set(long idx, UUID value) { vec.set(idx, value); }
//      };
//    }
//  };

}