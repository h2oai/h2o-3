package hex;

import water.Key;
import water.Keyed;
import water.fvec.Frame;

/**
 * WARNING!
 * This is a temporary abstraction used to preprocess frames during training and scoring.
 * As such, this class can be deprecated or even removed at any time, so don't extend or use directly yet.
 */
public abstract class ModelPreprocessor<T extends ModelPreprocessor> extends Keyed<T> {
    
    public ModelPreprocessor() {
        super();
    }

    public ModelPreprocessor(Key<T> key) {
        super(key);
    }
    
    public abstract Frame processTrain(Frame fr, Model.Parameters params);
    
    public abstract Frame processValid(Frame fr, Model.Parameters params);
    
    public abstract Frame processScoring(Frame fr, Model model);
    
    public abstract Model asModel();
}
