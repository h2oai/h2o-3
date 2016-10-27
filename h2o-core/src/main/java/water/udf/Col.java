package water.udf;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.PrettyPrint;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

/**
 * An adapter to Vec, allows type-safe access to data
 */
public class Col {

  public interface Column<T> {
    T get(long idx);

    void set(long idx, T value);

    String getString(long idx);

    Vec vec();
  }

  public static abstract class TypedChunk<T> {
    private Chunk c;

    public TypedChunk(Chunk c) { this.c = c; }
    boolean isNA(int i) { return c.isNA(i); }
    abstract T get(int idx);
    abstract void set(int idx, T value);
  }

  public static abstract class Factory<T> implements Serializable {
    protected Factory(byte typeCode) {
      this.typeCode = typeCode;
    }

    public abstract TypedChunk<T> newChunk(final Chunk c);
    public abstract TypedVector<T> newColumn(Vec vec);
    public final byte typeCode;
    public TypedVector<T> newColumn(long len, final Function<Long, T> f) throws IOException {
      return newColumn(makeVec(len, f));
    }

    protected Vec makeVec(long len, final Function<Long, T> f) throws IOException {
      final Vec vec0 = Vec.makeZero(len, typeCode);

      return new MRTask() {
        @Override
        public void map(Chunk[] cs) {
          for (Chunk c : cs) {
            TypedChunk<T> tc = newChunk(c);
            for (int r = 0; r < c._len; r++) {
              long i = r + c.start();
              tc.set(r, f.apply(i));
            }
          }
        }
      }.doAll(vec0)._fr.vecs()[0];
    }

  }

  public abstract static class TypedVector<T> implements Column<T>, Vec.Holder {
    protected Vec vec;

    protected TypedVector(Vec vec, byte type) {
      this.vec = vec;
    }

    public boolean isNA(long idx) { return vec.isNA(idx); }

    public String getString(long idx) { return isNA(idx) ? "(N/A)" : String.valueOf(get(idx)); }

    public Vec vec() { return vec; }

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

    @Override public TypedChunk<Double> newChunk(final Chunk c) {
      return new TypedChunk<Double>(c) {
        @Override Double get(int idx) { return c.isNA(idx) ? null : c.atd(idx); }
        @Override void set(int idx, Double value) {
          if (value == null) c.setNA(idx); else c.set(idx, value);
        }
        public void set(int idx, double value) { c.set(idx, value); }
      };
    }

    @Override public TypedVector<Double> newColumn(final Vec vec) {
      return new TypedVector<Double>(vec, typeCode) {
        @Override public Double get(long idx) { return vec.at(idx); }

        @Override public void set(long idx, Double value) {
          if (value == null) vec.setNA(idx); else vec.set(idx, value);
        }

        public void set(long idx, double value) { vec.set(idx, value); }
      }; }

  };

  //-------------------------------------------------------------

  public static final Factory<String> Strings = new Factory<String>(Vec.T_STR) {

    @Override public TypedChunk<String> newChunk(final Chunk c) {
      return new TypedChunk<String>(c) {
        @Override String get(int idx) { return asString(c.atStr(new BufferedString(), idx)); }
        @Override void set(int idx, String value) { c.set(idx, value); }
      };
    }

    @Override public TypedVector<String> newColumn(final Vec vec) {
      return new TypedVector<String>(vec, typeCode) {
        @Override
        public String get(long idx) { return asString(vec.atStr(new BufferedString(), idx)); }

        @Override
        public void set(long idx, String value) {
          vec.set(idx, value);
        }
      };
    }

  };

  //-------------------------------------------------------------

  static class EnumFactory extends Factory<Integer> {
    protected EnumFactory() {
      super(Vec.T_CAT);
    }

    @Override public TypedChunk<Integer> newChunk(final Chunk c) {
      return new TypedChunk<Integer>(c) {
        @Override Integer get(int idx) { return c.isNA(idx) ? null : (int) c.at8(idx); }
        @Override void set(int idx, Integer value) {
          if (value == null) c.setNA(idx); else c.set(idx, value);
        }
        public void set(int idx, int value) { c.set(idx, value); }
      };
    }

    public TypedVector<Integer> newColumn(long len, String[] domain, final Function<Long, Integer> f) throws IOException {
      Vec vec = makeVec(len, f);
      vec.setDomain(domain);
      return newColumn(vec);
    }

    @Override public TypedVector<Integer> newColumn(final Vec vec) {
      return new TypedVector<Integer>(vec, typeCode) {
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

  public static final EnumFactory Enums = new EnumFactory();

  //-------------------------------------------------------------

  public static final Factory<UUID> UUIDs = new Factory<UUID>(Vec.T_UUID) {

    @Override public TypedChunk<UUID> newChunk(final Chunk c) {
      return new TypedChunk<UUID>(c) {
        @Override UUID get(int idx) { return isNA(idx) ? null : new UUID(c.at16h(idx), c.at16l(idx)); }
        @Override void set(int idx, UUID value) { c.set(idx, value); }
      };
    }

    @Override public TypedVector<UUID> newColumn(final Vec vec) {
      return new TypedVector<UUID>(vec, typeCode) {
        @Override public UUID get(long idx) { return isNA(idx) ? null : new UUID(vec.at16h(idx), vec.at16l(idx)); }
        @Override public String getString(long idx) { return PrettyPrint.uuid(get(idx)); }
        @Override public void set(long idx, UUID value) { vec.set(idx, value); }
      };
    }
  };

  //-------------------------------------------------------------

  public static final Factory<Date> Dates = new Factory<Date>(Vec.T_TIME) {

    @Override public TypedChunk<Date> newChunk(final Chunk c) {
      return new TypedChunk<Date>(c) {
        @Override Date get(int idx) { return isNA(idx) ? null : new Date(c.at8(idx)); }
        @Override void set(int idx, Date value) {
          if (value == null) c.setNA(idx); else c.set(idx, value.getTime());
        }
      };
    }

    @Override public TypedVector<Date> newColumn(final Vec vec) {
      return new TypedVector<Date>(vec, typeCode) {
        @Override public Date get(long idx) { return isNA(idx) ? null : new Date(vec.at8(idx)); }

        @Override public void set(long idx, Date value) {
          if (value == null) vec.setNA(idx); else vec.set(idx, value.getTime());
        }
      };
    }

  };

  static String asString(Object x) { return x == null ? null : x.toString(); }
}