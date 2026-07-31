package hex.knn;

import java.util.HashMap;
import java.util.Map;

public class KNNHashMap<K, V extends TopNTreeMap<KNNKey, Object>> extends HashMap<K, V> {
    
    public void reduce(KNNHashMap<K, V> map){
        for (Map.Entry<K, V> entry: map.entrySet()) {
            K key = entry.getKey();
            V valueMap = entry.getValue();
            if (this.containsKey(key)){
                V currentKeyMap = this.get(key);
                currentKeyMap.putAll(valueMap);
                this.put(key, currentKeyMap);
            }
        }
    }
}
