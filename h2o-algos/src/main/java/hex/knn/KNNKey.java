package hex.knn;

import java.util.Objects;

/**
 * Class to save id and distance value for KNN calculation.
 * The key can be String or Integer. The value should be Double or class extends Double.
 * @param <K> String of Integer
 * @param <V> Double or class extends Double
 */
public class KNNKey<K, V extends Double> implements Comparable<KNNKey<K, V>>{
    
    K key;
    V value;
    
    KNNKey(K key, V value){
        this.key = key;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KNNKey<?, ?> knnKey = (KNNKey<?, ?>) o;
        return Objects.equals(key, knnKey.key) && Objects.equals(value, knnKey.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    /**
     * The compare method does not ensure sgn(x.compareTo(y)) == -sgn(y.compareTo(x))  for all cases:
     * in case the this.value  and o.value are the same but the keys are not same -> order of asking matters.
     * In this special case x.compareTo(y) == y.compareTo(x).
     * We need this functionality for ordering in TopNTreeMap, where order of asking is important to decide top N neighbours.
     */
    public int compareTo(KNNKey<K, V> o) {
        int sameValue = this.value.compareTo(o.value);
        if (sameValue == 0){
            boolean sameKey = this.key.equals(o.key);
            if (sameKey) {
                return 0;
            } else {
                return -1;
            }
        }
        return sameValue;
    }
}
