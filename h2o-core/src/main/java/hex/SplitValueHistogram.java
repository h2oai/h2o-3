package hex;

import org.apache.commons.lang.mutable.MutableInt;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class SplitValueHistogram {
    
    private final TreeMap<Double, MutableInt> map;

    public SplitValueHistogram() {
        this.map = new TreeMap<>();
    }

    public void addValue(double splitValue, int count) {
        if (!map.containsKey(splitValue)) {
            map.put(splitValue, new MutableInt(0));
        }
        map.get(splitValue).add(count);
    }
    
    public void merge(SplitValueHistogram histogram) {
        for (Map.Entry<Double, MutableInt> entry: histogram.entrySet()) {
            this.addValue(entry.getKey(), entry.getValue().intValue());
        }
    }

    public Set<Map.Entry<Double, MutableInt>> entrySet() {
        return map.entrySet();
    }

    public MutableInt get(Object key) {
        return map.get(key);
    }
}
