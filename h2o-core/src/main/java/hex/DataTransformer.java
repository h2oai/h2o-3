package hex;

import water.Key;
import water.Keyed;
import water.fvec.Frame;

/**
 * WARNING!
 * This is a temporary abstraction used to preprocess frames during training and scoring.
 * As such, this class can be deprecated or even removed at any time, so don't extend or use directly yet.
 */
public abstract class DataTransformer<T extends DataTransformer> extends Keyed<T> {
    
    public DataTransformer() {
        super();
    }

    public DataTransformer(Key<T> key) {
        super(key);
    }
    
    public abstract Frame transformTrain(Frame fr, Model.Parameters params);
    
    public abstract Frame transformValid(Frame fr, Model.Parameters params);
    
    public abstract Frame transformPredict(Frame fr, Model model);
    
    public abstract Model asModel();
}
