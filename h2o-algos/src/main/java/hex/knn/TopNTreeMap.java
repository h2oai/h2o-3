package hex.knn;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * The map for saving distances. 
 * @param <K> Key is composed of data id and distance value.
 * @param <V> Value is the class of the data point.
 */
public class TopNTreeMap<K extends KNNKey, V> extends TreeMap<K, V> {
    
    public int n;
    
    TopNTreeMap(int n){
        this.n = n;
    }
    
    @Override
    public V put(K key, V value) {
        if(size() < n) {
            return super.put(key, value);
        }
        K lastKey = lastEntry().getKey();
        int compare = comparator().compare(lastKey, key);
        if(compare > 0 ) {
            V returnValue = super.put(key, value);
            if (size() > n){
                remove(lastKey);
            }
            return returnValue;
        } else {
            return null;
        }
    }

    @Override
    public Comparator<? super K> comparator() {
        return new Comparator<K>() {
            @Override
            public int compare(K o1, K o2) {
                return o1.compareTo(o2);
            }
        };
    }
}
