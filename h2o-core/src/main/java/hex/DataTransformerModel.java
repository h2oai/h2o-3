package hex;

import water.Freezable;
import water.IKeyed;

/**
 * Interface to be implemented by data transformers that are implemented as a model.
 */
public interface DataTransformerModel<T extends DataTransformerModel> extends DataTransformer, IKeyed<T> {

  Model getModel(); 
}
