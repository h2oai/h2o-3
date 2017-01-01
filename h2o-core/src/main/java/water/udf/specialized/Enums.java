package water.udf.specialized;

import water.fvec.Chunk;
import water.fvec.Vec;
import water.udf.*;
import water.udf.fp.Function;

import java.io.IOException;

/**
 * Specialized factory for enums (aka Cats)
 */
public class Enums extends DataColumns.BaseFactory<Integer> {
  private final String[] domain;

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

  public static class EnumChunk extends DataChunk<Integer> {

    /**
     * deserialization :(
     */
    EnumChunk() {}
    
    EnumChunk(Chunk c) { super(c); }
    @Override
    public Integer get(long idx) {
      int i = indexOf(idx);
      try {
        return c.isNA(i) ? null : (int) c.at8(i);
      } catch (ArrayIndexOutOfBoundsException iae) {
        throw new ArrayIndexOutOfBoundsException(iae.getMessage() + " idx=" + Long.toHexString(idx));
      }
    }

    @Override
    protected void setValue(int at, Integer value) {
      c.set(at, value);
    }

    public void set(int at, int value) {
      c.set(at, value);
    }
  }

  @Override
  public DataChunk<Integer> apply(final Chunk c) {
    return new EnumChunk(c);
  }

  public DataColumn<Integer> newColumn(long length, final Function<Long, Integer> f) throws IOException {
    return new TypedFrame.EnumFrame(length, f, domain).newColumn();
  }

  static class Column extends DataColumn<Integer> {
    private final String[] domain;
    /**
     * deserialization :(
     */
    public Column() { domain = null; }
    
    Column(Vec v, Enums factory) { 
      super(v, factory);
      domain = factory.domain;
      assert domain != null && domain.length > 0 : "Need a domain for enums";
    }

    @Override
    public Integer get(long idx) {
      EnumChunk c = new EnumChunk(chunkAt(idx));
      return c.get(idx);
    }

    @Override
    public void set(long idx, Integer value) {
      EnumChunk c = new EnumChunk(chunkAt(idx));
      c.set(idx, value);
    }

    public void set(long idx, int value) {
      EnumChunk c = new EnumChunk(chunkAt(idx));
      c.set(idx, value);
    }
  }
  
  @Override
  public DataColumn<Integer> newColumn(final Vec vec) {
    if (vec.get_type() != Vec.T_CAT)
      throw new IllegalArgumentException("Expected type T_CAT, got " + vec.get_type_str());
    vec.setDomain(domain);
    return new Column(vec, this);
  }

}
