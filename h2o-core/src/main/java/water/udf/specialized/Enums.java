package water.udf.specialized;

import water.fvec.Chunk;
import water.fvec.Vec;
import water.udf.*;
import water.udf.fp.Function;
import water.udf.fp.Functions;

import java.io.IOException;
import java.util.List;

/**
 * Specialized factory for enums (aka Cats)
 */
public class Enums extends DataColumns.BaseFactory<Integer, EnumColumn> {
  final String[] domain;
  
  /**
   * deserialization :(
   */
  public Enums() {
    super(Vec.T_CAT, "Cats");
    domain = null;
  }

  public Enums(String[] domain) {
    super(Vec.T_CAT, "Cats");
    this.domain = domain;
  }

  public static Enums enums(String[] domain) {
    return new Enums(domain);
  }

  public static ColumnFactory<Integer, EnumColumn> enumsAlt(String[] domain) {
    return enums(domain);
  }



  public static class EnumChunk extends DataChunk<Integer> {

    /**
     * deserialization :(
     */
    EnumChunk() {}
    
    EnumChunk(Chunk c) { super(c); }
    @Override
    public Integer get(int idx) {
      return c.isNA(idx) ? null : (int) c.at8(idx);
    }

    @Override
    public void set(int idx, Integer value) {
      if (value == null) c.setNA(idx);
      else c.set(idx, value);
    }

    public void set(int idx, int value) {
      c.set(idx, value);
    }
  }

  @Override
  public DataChunk<Integer> apply(final Chunk c) {
    return new EnumChunk(c);
  }

  public EnumColumn newColumn(long length, final Function<Long, Integer> f) throws IOException {
    return new TypedFrame.EnumFrame(length, f, domain).newColumn();
  }

  public EnumColumn newColumn(List<Integer> source) throws IOException {
    return new TypedFrame.EnumFrame(source.size(), Functions.onList(source), domain).newColumn();
  }
  
  @Override
  public EnumColumn newColumn(final Vec vec) {
    if (vec.get_type() != Vec.T_CAT)
      throw new IllegalArgumentException("Expected type T_CAT, got " + vec.get_type_str());
    vec.setDomain(domain);
    return new EnumColumn(vec, this);
  }

}
