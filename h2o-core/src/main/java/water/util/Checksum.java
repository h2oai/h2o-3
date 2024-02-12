package water.util;

import water.Checksumable;
import water.H2O;
import water.Weaver;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

public final class Checksum {
  
  private Checksum() {}

  public static <T> long checksum(final T obj) {
    return checksum(obj, null);
  }
  
  public static <T> long checksum(final T obj, final Set<String> ignoredFields) {
    return checksum(obj, ignoredFields, 0x600DL);
  }
  
  /**
   * Compute a checksum based on all non-transient non-static ice-able assignable fields (incl. inherited ones).
   * Sort the fields first, since reflection gives us the fields in random order, and we don't want the checksum to be affected by the field order.
   * 
   * NOTE: if a field is added to a class the checksum will differ even when all the previous parameters have the same value.  If
   *       a client wants backward compatibility they will need to compare values explicitly.
   *
   * The method is motivated by standard hash implementation `hash = hash * P + value` but we use high prime numbers in random order.
   * 
   * @param ignoredFields A {@link Set} of fields to ignore. Can be empty or null.
   * @return checksum A 64-bit long representing the checksum of the object
   */
  public static <T> long checksum(final T obj, final Set<String> ignoredFields, final long initVal) {
    assert obj != null;
    long xs = initVal;
    int count = 0;
    Field[] fields = Weaver.getWovenFields(obj.getClass());
    Arrays.sort(fields, Comparator.comparing(Field::getName));
    for (Field f : fields) {
      if (ignoredFields != null && ignoredFields.contains(f.getName())) {
        // Do not include ignored fields in the final hash
        continue;
      }
      final long P = MathUtils.PRIMES[count % MathUtils.PRIMES.length];
      Class<?> c = f.getType();
      Object fvalue;
      try {
        f.setAccessible(true);
        fvalue = f.get(obj);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
      if (c.isArray()) {
        try {
          if (fvalue != null) {
            if (c.getComponentType() == Integer.TYPE){
              int[] arr = (int[]) fvalue;
              xs = xs * P + (long) Arrays.hashCode(arr);
            } else if (c.getComponentType() == Float.TYPE) {
              float[] arr = (float[]) fvalue;
              xs = xs * P + (long) Arrays.hashCode(arr);
            } else if (c.getComponentType() == Double.TYPE) {
              double[] arr = (double[]) fvalue;
              xs = xs * P + (long) Arrays.hashCode(arr);
            } else if (c.getComponentType() == Long.TYPE){
              long[] arr = (long[]) fvalue;
              xs = xs * P + (long) Arrays.hashCode(arr);
            } else if (c.getComponentType() == Boolean.TYPE){
              boolean[] arr = (boolean[]) fvalue;
              xs = xs * P + (long) Arrays.hashCode(arr);
            } else {
              Object[] arr = (Object[]) fvalue;
              if (Checksumable.class.isAssignableFrom(arr.getClass().getComponentType())) {
                for (Checksumable cs : (Checksumable[])arr) {
                  xs = xs * P + cs.checksum();
                }
              } else {
                xs = xs * P + (long) Arrays.deepHashCode(arr);
              }
            } //else lead to ClassCastException
          } else {
            xs = xs * P;
          }
        } catch (ClassCastException t) {
          throw H2O.fail("Failed to calculate checksum for the parameter object", t); //no support yet for int[][] etc.
        }
      } else {
        if (fvalue instanceof Enum) {
          // use string hashcode for enums, otherwise the checksum would be different each run
          xs = xs * P + (long) (fvalue.toString().hashCode());
        } else if (fvalue instanceof Checksumable) {
          xs = xs * P + ((Checksumable) fvalue).checksum();
        } else if (fvalue != null) {
          xs = xs * P + (long)(fvalue.hashCode());
        } else {
          xs = xs * P + P;
        }
      }
      count++;
    }
    return xs;
  }

}
