package water.udf;

import water.fvec.Chunk;
import water.fvec.Vec;
import water.parser.BufferedString;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * An adapter to Vec, allows type-safe access to data
 */
public class DataColumns {

  public static abstract class Factory<T> 
      implements ChunkFactory<T>, Serializable {
    public final byte typeCode;
    protected Factory(byte typeCode) {
      this.typeCode = typeCode;
    }
    
    public byte typeCode() { return typeCode; }

    public abstract DataChunk<T> apply(final Chunk c);
    public abstract DataColumn<T> newColumn(Vec vec);
    
    protected TypedFrame<T> newFrame1(long len, final Function<Long, T> f) throws IOException {
      return new TypedFrame<>(this, len, f);
    }
    
    public DataColumn<T> newColumn(long len, final Function<Long, T> f) throws IOException {
      return newFrame1(len, f).newColumn();
    }

    public DataColumn<T> materialize(Column<T> xs) throws IOException {
      return newColumn(xs.size(), xs);
    }

    public List<DataColumn<T>> materialize(UnfoldingFrame<T> fr) throws IOException {
      return fr.materialize();
    }

    public DataColumn<T> newColumn(final List<T> xs) throws IOException {
      return newColumn(xs.size(), Functions.onList(xs));
    }
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

  public static final Factory<Double> Doubles = new Factory<Double>(Vec.T_NUM) {

    @Override public DataChunk<Double> apply(final Chunk c) {
      return new DataChunk<Double>(c) {
        @Override public Double get(int idx) { return c.isNA(idx) ? null : c.atd(idx); }

        @Override public int length() { return c.len(); }

        @Override public void set(int idx, Double value) {
          if (value == null) c.setNA(idx); else c.set(idx, value);
        }
        public void set(int idx, double value) { c.set(idx, value); }
      };
    }

    @Override public DataColumn<Double> newColumn(final Vec vec) {
      if (vec.get_type() != Vec.T_NUM)
        throw new IllegalArgumentException("Expected type T_NUM, got " + vec.get_type_str());
      return new DataColumn<Double>(vec, typeCode, this) {

        public Double get(long idx) { return vec.at(idx); }

        @Override public Double apply(Long idx) { return get(idx); }

        @Override public Double apply(long idx) { return get(idx); }

        @Override public void set(long idx, Double value) {
          if (value == null) vec.setNA(idx); else vec.set(idx, value);
        }

        public void set(long idx, double value) { vec.set(idx, value); }
      }; }

  };

  //-------------------------------------------------------------

  public static final Factory<String> Strings = new Factory<String>(Vec.T_STR) {

    @Override public DataChunk<String> apply(final Chunk c) {
      return new DataChunk<String>(c) {
        @Override public String get(int idx) { return asString(c.atStr(new BufferedString(), idx)); }

        @Override public int length() { return c.len(); }

        @Override public void set(int idx, String value) { 
          c.set(idx, value); }
      };
    }

    @Override public DataColumn<String> newColumn(final Vec vec) {
      if (vec.get_type() != Vec.T_STR)
        throw new IllegalArgumentException("Expected type T_STR, got " + vec.get_type_str());
      return new DataColumn<String>(vec, typeCode, this) {
        @Override public String get(long idx) { 
          return vec.isNA(idx) ? null : asString(vec.atStr(new BufferedString(), idx)); 
        }

        @Override
        public void set(long idx, String value) {
          vec.set(idx, value);
        }
      };
    }

  };

  //-------------------------------------------------------------

  static class EnumFactory extends Factory<Integer> {
    private final String[] domain;
    
    protected EnumFactory(String[] domain) {
      super(Vec.T_CAT);
      this.domain = domain;
    }

    @Override public DataChunk<Integer> apply(final Chunk c) {
      return new DataChunk<Integer>(c) {
        @Override public Integer get(int idx) { return c.isNA(idx) ? null : (int) c.at8(idx); }
        @Override public void set(int idx, Integer value) {
          if (value == null) c.setNA(idx); else c.set(idx, value);
        }
        public void set(int idx, int value) { c.set(idx, value); }
      };
    }

    public DataColumn<Integer> newColumn(long len, final Function<Long, Integer> f) throws IOException {
      return new TypedFrame.EnumFrame1(len, f, domain).newColumn();
    }

    @Override public DataColumn<Integer> newColumn(final Vec vec) {
      if (vec.get_type() != Vec.T_CAT)
        throw new IllegalArgumentException("Expected type T_CAT, got " + vec.get_type_str());
      vec.setDomain(domain);
      return new DataColumn<Integer>(vec, typeCode, this) {
        private final String[] domain = vec.domain();
        {
          assert domain != null && domain.length > 0 : "Need a domain for enums";
        }

        @Override
        public Integer get(long idx) { return isNA(idx) ? null : (int) vec.at8(idx); }

        @Override
        public String getString(long idx) {
          Integer i = get(idx);
          boolean noname = (i == null || domain == null || i < 0 || i >= domain.length);
          return noname ? "" + i : domain[i];
        }

        @Override
        public void set(long idx, Integer value) {
          if (value == null) vec.setNA(idx); else vec.set(idx, value);
        }

        public void set(long idx, int value) { vec.set(idx, value); }
      };
    }
  }

  public static EnumFactory Enums(String[] domain) {
    return new EnumFactory(domain);
  }

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
//        @Override public String getString(long idx) { return PrettyPrint.uuid(get(idx)); }
//        @Override public void set(long idx, UUID value) { vec.set(idx, value); }
//      };
//    }
//  };

  //-------------------------------------------------------------

  public static final Factory<Date> Dates = new Factory<Date>(Vec.T_TIME) {

    @Override public DataChunk<Date> apply(final Chunk c) {
      return new DataChunk<Date>(c) {
        @Override public Date get(int idx) { return isNA(idx) ? null : new Date(c.at8(idx)); }
        @Override public void set(int idx, Date value) {
          if (value == null) c.setNA(idx); else c.set(idx, value.getTime());
        }
      };
    }

    @Override public DataColumn<Date> newColumn(final Vec vec) {
      if (vec.get_type() != Vec.T_TIME && vec.get_type() != Vec.T_NUM) 
        throw new IllegalArgumentException("Expected a type compatible with Dates, got " + vec.get_type_str());
      return new DataColumn<Date>(vec, typeCode, this) {
        @Override public Date get(long idx) { return isNA(idx) ? null : new Date(vec.at8(idx)); }

        @Override public void set(long idx, Date value) {
          if (value == null) vec.setNA(idx); else vec.set(idx, value.getTime());
        }
      };
    }

  };

  static String asString(Object x) { return x == null ? null : x.toString(); }
}