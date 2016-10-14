package water.fvec;

import water.parser.BufferedString;
import water.util.PrettyPrint;

import java.util.Date;
import java.util.UUID;

/**
 * An adapter to Vec, allows type-safe access to data
 */
public class Col {

  public interface Column<T> {
    abstract T get(long idx);
    String getString(long idx);
    Vec vec();
  }

  public abstract static class OnVector<T> implements Column<T> {
    Vec vec;

    protected OnVector(Vec vec, byte type) {
      this.vec = vec;
      vec._type = type;
    }

    public boolean isNA(long idx) { return vec.isNA(idx); }

    public String getString(long idx) {
      return isNA(idx) ? "(N/A)" : String.valueOf(get(idx));
    }

    public Vec vec() { return vec; }
  }

  public static class OfDoubles extends OnVector<Double> {
    public OfDoubles(Vec vec) {
      super(vec, Vec.T_NUM);
    }

    @Override
    public Double get(long idx) {
      return vec.at(idx);
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

  public static class OfStrings extends OnVector<String> {
    public OfStrings(Vec vec) {
      super(vec, Vec.T_STR);
    }

    @Override
    public String get(long idx) {
      BufferedString bs = new BufferedString();
      BufferedString res = vec.atStr(bs, idx);
      return res == null ? null : res.toString();
    }
  }

  public static class OfEnums extends OnVector<Integer> {
    private final String[] domain;

    public OfEnums(Vec vec) {
      super(vec, Vec.T_CAT);
      this.domain = vec.domain();
    }

    @Override
    public Integer get(long idx) {
      if (vec.isNA(idx)) return null;
      return (int) vec.at8(idx);
    }

    @Override public String getString(long idx) {
      Integer i = get(idx);
      boolean noname = (i == null || domain == null || i < 0 || i >= domain.length);
      return noname ? ""+i : domain[i];
    }
  }

  public static class OfUUID extends OnVector<UUID> {

    public OfUUID(Vec vec) {
      super(vec, Vec.T_UUID);
    }

    @Override
    public UUID get(long idx) {
      if (vec.isNA(idx)) return null;
      return new UUID(vec.at16h(idx), vec.at16l(idx));
    }

    @Override public String getString(long idx) {
      return PrettyPrint.uuid(get(idx));
    }
  }

  public static class OfDates extends OnVector<Date> {

    public OfDates(Vec vec) {
      super(vec, Vec.T_TIME);
    }

    @Override
    public Date get(long idx) {
      if (vec.isNA(idx)) return null;
      return new Date(vec.at8(idx));
    }
  }
}