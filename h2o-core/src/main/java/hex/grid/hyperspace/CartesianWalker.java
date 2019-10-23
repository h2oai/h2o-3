package hex.grid.hyperspace;

import hex.Model;
import hex.ModelParametersBuilderFactory;

import java.util.*;

public class CartesianWalker<MP extends Model.Parameters> extends HyperSpaceWalker.BaseWalker<MP, HyperSpaceSearchCriteria.CartesianSearchCriteria> {
  public CartesianWalker(MP params,
                         Map<String, Object[]> hyperParams,
                         ModelParametersBuilderFactory<MP> paramsBuilderFactory,
                         HyperSpaceSearchCriteria.CartesianSearchCriteria search_criteria) {
    super(params, hyperParams, paramsBuilderFactory, search_criteria);
  }

  @Override
  public HyperSpaceIterator<MP> iterator() {

    return new HyperSpaceIterator<MP>() {
      /** Hyper params permutation.
       */
      private int[] _currentHyperparamIndices = null;


      @Override
      public List<MP> initialModelParameters(int numParams) {
        List<MP> parameters = new ArrayList<>(numParams);

        for (int i = 0; i < numParams; i++) {
          parameters.add(nextModelParameters(Optional.empty()));
        }

        return parameters;
      }

      @Override
      public MP nextModelParameters(Optional<Model> previousModel) {
        _currentHyperparamIndices = _currentHyperparamIndices != null ? nextModelIndices(_currentHyperparamIndices) : new int[_hyperParamNames.length];
        if (_currentHyperparamIndices != null) {
          // Fill array of hyper-values
          Object[] hypers = hypers(_currentHyperparamIndices, new Object[_hyperParamNames.length]);
          // Get clone of parameters
          MP commonModelParams = (MP) _params.clone();
          // Fill model parameters
          MP params = getModelParams(commonModelParams, hypers);

          return params;
        } else {
          throw new NoSuchElementException("No more elements to explore in hyper-space!");
        }
      }

      @Override
      public boolean hasNext(Optional<Model> previousModel) {
        if (_currentHyperparamIndices == null) {
          return true;
        }
        int[] hyperparamIndices = _currentHyperparamIndices;
        for (int i = 0; i < hyperparamIndices.length; i++) {
          if (hyperparamIndices[i] + 1 < _hyperParams.get(_hyperParamNames[i]).length) {
            return true;
          }
        }
        return false;
      }

      @Override
      public void reset() {
        _currentHyperparamIndices = null;
      }

      @Override
      public double time_remaining_secs() {
        return Double.MAX_VALUE;
      }

      @Override
      public double max_runtime_secs() {
        return Double.MAX_VALUE;
      }

      public int max_models() {
        return _maxHyperSpaceSize > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) _maxHyperSpaceSize;
      }

      @Override
      public void modelFailed(Model failedModel) {
        // nada
      }

      @Override
      public Object[] getCurrentRawParameters() {
        Object[] hyperValues = new Object[_hyperParamNames.length];
        return hypers(_currentHyperparamIndices, hyperValues);
      }
    };
  }

  /**
   * Cartesian iteration over the hyper-parameter space, varying one hyperparameter at a
   * time. Mutates the indices that are passed in and returns them.  Returns NULL when
   * the entire space has been traversed.
   */
  private int[] nextModelIndices(int[] hyperparamIndices) {
    // Find the next parm to flip
    int i;
    for (i = 0; i < hyperparamIndices.length; i++) {
      if (hyperparamIndices[i] + 1 < _hyperParams.get(_hyperParamNames[i]).length) {
        break;
      }
    }
    if (i == hyperparamIndices.length) {
      return null; // All done, report null
    }
    // Flip indices
    for (int j = 0; j < i; j++) {
      hyperparamIndices[j] = 0;
    }
    hyperparamIndices[i]++;
    return hyperparamIndices;
  }
}
