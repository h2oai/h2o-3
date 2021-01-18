package hex.grid;

import water.AutoBuffer;
import water.H2O;
import water.Iced;

import java.util.Map;
import java.util.TreeMap;

/**
 * Wrapper class to make hyper-parameters serializable with Icer
 */
public class HyperParameters extends Iced<HyperParameters> {

    private transient Map<String, Object[]> values;

    public HyperParameters(Map<String, Object[]> values) {
        this.values = values;
    }

    public Map<String, Object[]> getValues() {
        return values;
    }

    public final AutoBuffer write_impl(AutoBuffer ab) {
        writeHyperParamsMap(ab, values);
        return ab;
    }

    private void writeHyperParamsMap(AutoBuffer ab, Map<String, Object[]> params) {
        ab.putInt(params.keySet().size());
        for (String key : params.keySet()) {
            ab.putStr(key);
            Object[] vals = params.get(key);
            if (vals.length > 0 && vals[0] instanceof Map) {
                ab.putInt(vals.length);
                for (int j = 0; j < vals.length; j++) {
                    writeHyperParamsMap(ab, (Map<String, Object[]>) vals[j]);
                }
            } else {
                ab.putInt(-1);
                ab.putASer(vals);
            }
        }
    }

    public final HyperParameters read_impl(AutoBuffer ab) {
        return new HyperParameters(readHyperParamsMap(ab));
    }

    private Map<String, Object[]> readHyperParamsMap(AutoBuffer ab) {
        Map<String, Object[]> map = new TreeMap<>();
        int len = ab.getInt();
        for (int i = 0; i < len; i++) {
            String key = ab.getStr();
            int subMapsCount = ab.getInt();
            Object[] vals;
            if (subMapsCount >= 0) {
                vals = new Object[subMapsCount];
                for (int j = 0; j < subMapsCount; j++) {
                    vals[j] = readHyperParamsMap(ab);
                }
            } else {
                vals = ab.getASer(Object.class);
            }
            map.put(key, vals);
        }
        return map;
    }

    public final AutoBuffer writeJSON_impl(AutoBuffer ab) {
        throw H2O.unimpl();
    }

    public final HyperParameters readJSON_impl(AutoBuffer ab) {
        throw H2O.unimpl();
    }

}
