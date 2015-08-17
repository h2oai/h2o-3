package hex.schemas;

import hex.api.NaiveBayesGridSearchHandler;
import hex.naivebayes.NaiveBayesModel;

/**
 * End-point for GLRM grid search.
 *
 * @see GridSearchSchema
 */
public class NaiveBayesGridSearchV99 extends GridSearchSchema<NaiveBayesGridSearchHandler.NaiveBayesGrid,
    NaiveBayesGridSearchV99,
    NaiveBayesModel.NaiveBayesParameters,
    NaiveBayesV3.NaiveBayesParametersV3> {

}
