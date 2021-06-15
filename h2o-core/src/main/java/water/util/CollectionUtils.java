package water.util;

import java.util.*;

/**
 */
public class CollectionUtils {

  public static <K, V> Map<K, V> createMap(K[] keys, V[] values) {
    assert keys.length == values.length : "Lengths of keys and values should be the same";
    Map<K, V> res = new HashMap<>(keys.length);
    for (int i = 0; i < keys.length; i++)
      res.put(keys[i], values[i]);
    return res;
  }

  /** Convert a Collection of Bytes to a primitive array byte[]. */
  public static byte[] unboxBytes(Collection<Byte> coll) {
    byte[] res = new byte[coll.size()];
    int i = 0;
    for (Byte elem : coll)
      res[i++] = elem;
    return res;
  }

  /** Convert a Collection of Strings to a plain array String[]. */
  public static String[] unboxStrings(Collection<String> coll) {
    return coll.toArray(new String[coll.size()]);
  }

  /** Convert a Collection of Strings[] to a plain array String[][]. */
  public static String[][] unboxStringArrays(Collection<String[]> coll) {
    return coll.toArray(new String[coll.size()][]);
  }

  /**
   *  See {@link #setOfUniqueRandomNumbers(int, long, Random)}
   */
  public static long[] setOfUniqueRandomNumbers(int sizeOfSet, long upperBound, long seed) {
    return setOfUniqueRandomNumbers(sizeOfSet, upperBound, RandomUtils.getRNG(seed));
  }

  /**
   * @return Set of unique random numbers from range [0, upperBound]
   */
  public static long[] setOfUniqueRandomNumbers(int sizeOfSet, long upperBound, Random random) {
    Set<Long> uniqueRandomNumber = new TreeSet<>();
    while (uniqueRandomNumber.size() < sizeOfSet) {
      long generatedLong = (long) (random.nextFloat() * (upperBound));
      uniqueRandomNumber.add(generatedLong);
    }
    return uniqueRandomNumber.stream().mapToLong(Long::longValue).toArray();
  }

  /**
   *  See {@link #setOfUniqueRandomNumbers(int, int, Random)}
   */
  public static int[] setOfUniqueRandomNumbers(int sizeOfSet, int upperBound, long seed) {
    return setOfUniqueRandomNumbers(sizeOfSet, upperBound, RandomUtils.getRNG(seed));
  }

  /**
   * @return array of unique random numbers from range [0, upperBound]
   */
  public static int[] setOfUniqueRandomNumbers(int sizeOfSet, int upperBound, Random random) {
    HashSet<Integer> uniqueRandomNumber = new HashSet<>(sizeOfSet);
    while (uniqueRandomNumber.size() < sizeOfSet) {
      uniqueRandomNumber.add(random.nextInt(upperBound));
    }
    return uniqueRandomNumber.stream().mapToInt(Integer::intValue).toArray();
  }
}
