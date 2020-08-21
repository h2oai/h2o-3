package hex.tree.xgboost.util;

import java.io.Serializable;

public class FeatureScore implements Serializable {
    
    public static final String GAIN_KEY = "gain";
    public static final String COVER_KEY = "cover";
    
    public int _frequency = 1;
    public float _gain;
    public float _cover;

    public void add(FeatureScore fs) {
        _frequency += fs._frequency;
        _gain += fs._gain;
        _cover += fs._cover;
    }
}
