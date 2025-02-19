package hex.knn;

import water.Iced;

import java.util.Objects;

/**
 * Class to save id and distance value for KNN calculation.
 * The key can be String or Integer. The value should be Double or class extends Double.
 * @param <K> String of Integer
 * @param <V> Double or class extends Double
 */
public class KNNKey<K extends Comparable<K>, V extends Double> extends Iced<KNNKey> implements Comparable<KNNKey<K, V>> {
    
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
    public int compareTo(KNNKey<K, V> o) {
        if (o == null) return 1;
        int sameValue = this.value.compareTo(o.value);
        if (sameValue == 0){
             return this.key.compareTo(o.key);
        }
        return sameValue;
    }
}
