package water.rapids.ast.prims.assign;

import water.Iced;
import water.fvec.Chunk;
import water.fvec.Vec;

import java.util.UUID;

public class AstRecAsgnHelper {

  /**
   * Generic abstraction over Chunk setter methods.
   */
  public static abstract class ValueSetter extends Iced<ValueSetter> {
    /**
     * Sets a value (possibly a constant) to a position of the Chunk.
     * @param idx Chunk-local index
     */
    public abstract void setValue(Chunk chk, int idx);
    /**
     * Sets a value (possibly a constant) to a given index of a Vec.
     * @param vec Vec
     * @param idx absolute index
     */
    public abstract void setValue(Vec vec, long idx);
  }

  /**
   * Create an instance of ValueSetter for a given scalar value.
   * It creates setter of the appropriate type based on the type of the underlying Vec.
   * @param v Vec
   * @param value scalar value
   * @return instance of ValueSetter
   */
  public static ValueSetter createValueSetter(Vec v, Object value) {
    if (value == null) {
      return new NAValueSetter();
    }
    switch (v.get_type()) {
      case Vec.T_CAT:
        return new CatValueSetter(v.domain(), value);
      case Vec.T_NUM:
      case Vec.T_TIME:
        return new NumValueSetter(value);
      case Vec.T_STR:
        return new StrValueSetter(value);
      case Vec.T_UUID:
        return new UUIDValueSetter(value);
      default:
        throw new IllegalArgumentException("Cannot create ValueSetter for a Vec of type = " + v.get_type_str());
    }
  }

  private static class NAValueSetter extends ValueSetter {
    public NAValueSetter() {} // for Externalizable
    @Override
    public void setValue(Chunk chk, int idx) { chk.setNA(idx); }
    @Override
    public void setValue(Vec vec, long idx) { vec.setNA(idx); }
  }

  private static class CatValueSetter extends ValueSetter {
    private int _val;

    public CatValueSetter() {} // for Externalizable

    private CatValueSetter(String[] domain, Object val) {
      if (! (val instanceof String)) {
        throw new IllegalArgumentException("Value needs to be categorical, value = " + val);
      }
      int factorIdx = -1;
      for (int i = 0; i < domain.length; i++)
        if (val.equals(domain[i])) {
          factorIdx = i;
          break;
        }
      if (factorIdx == -1)
        throw new IllegalArgumentException("Value is not in the domain of the Vec, value = " + val);
      _val = factorIdx;
    }

    @Override
    public void setValue(Chunk chk, int idx) { chk.set(idx, _val); }

    @Override
    public void setValue(Vec vec, long idx) { vec.set(idx, (double) _val); }
  }

  private static class NumValueSetter extends ValueSetter {
    private double _val;

    public NumValueSetter() {} // for Externalizable

    private NumValueSetter(Object val) {
      if (! (val instanceof Number)) {
        throw new IllegalArgumentException("Value needs to be numeric, value = " + val);
      }
      _val = ((Number) val).doubleValue();
    }

    @Override
    public void setValue(Chunk chk, int idx) { chk.set(idx, _val); }

    @Override
    public void setValue(Vec vec, long idx) { vec.set(idx, _val); }
  }

  private static class StrValueSetter extends ValueSetter {
    private String _val;

    public StrValueSetter() {} // for Externalizable

    private StrValueSetter(Object val) {
      if (! (val instanceof String)) {
        throw new IllegalArgumentException("Value needs to be string, value = " + val);
      }
      _val = (String) val;
    }

    @Override
    public void setValue(Chunk chk, int idx) { chk.set(idx, _val); }

    @Override
    public void setValue(Vec vec, long idx) { vec.set(idx, _val); }
  }

  private static class UUIDValueSetter extends ValueSetter {
    private UUID _val;

    public UUIDValueSetter() {} // for Externalizable

    private UUIDValueSetter(Object val) {
      if (val instanceof String) {
        val = UUID.fromString((String) val);
      } else if (! (val instanceof UUID)) {
        throw new IllegalArgumentException("Value needs to be an UUID, value = " + val);
      }
      _val = (UUID) val;
    }

    @Override
    public void setValue(Chunk chk, int idx) { chk.set(idx, _val); }

    @Override
    public void setValue(Vec vec, long idx) { vec.set(idx, _val); }
  }

}
