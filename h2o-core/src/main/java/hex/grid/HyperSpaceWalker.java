package hex.grid;

import java.util.Map;
import java.util.NoSuchElementException;

import hex.Model;
import hex.ModelParametersBuilderFactory;
import water.Key;

public interface HyperSpaceWalker<MP extends Model.Parameters> {

  interface HyperSpaceIterator<MP extends Model.Parameters> {
    /**
     * Get next model parameters.
     *
     * <p>It should return model parameters for next point in hyper space.
     * Throws {@link java.util.NoSuchElementException} if there is no remaining point in space
     * to explore.</p>
     *
     * <p>The method can optimize based on previousModel, but should be
     * able to handle null-value.</p>
     *
     * @param previousModel  model generated for the previous point in hyper space, can be null.
     *
     * @return model parameters for next point in hyper space or null if there is no such point.
     *
     * @throws IllegalArgumentException  when model parameters cannot be constructed
     * @throws java.util.NoSuchElementException if the iteration has no more elements
     */
    MP nextModelParameters(Model previousModel);

    /**
     * Returns true if the iterator can continue.
     * @param previousModel  optional parameter which helps to determine next step, can be null
     * @return  true if the iterator can produce one more model parameters configuration.
     */
    boolean hasNext(Model previousModel);

    /**
     * Returns current "raw" state of iterator.
     *
     * The state is represented by a permutation of values of grid parameters.
     *
     * @return  array of "untyped" values representing configuration of grid parameters
     */
    Object[] getCurrentRawParameters();
  }

  /**
   * Returns an iterator to traverse this hyper-space.
   *
   * @return an iterator
   */
  HyperSpaceIterator<MP> iterator();

  /**
   * Returns hyper parameters names which are used for walking the hyper parameters space.
   *
   * The names have to match the names of attributes in model parameters MP.
   *
   * @return names of used hyper parameters
   */
  String[] getHyperParamNames();

  /**
   * Return estimated size of hyperspace.
   *
   * Can return -1 if estimate is not available.
   *
   * @return size of hyper space to explore
   */
  int getHyperSpaceSize();

  /**
   * Return initial model parameters for search.
   * @return  return model parameters
   */
  MP getParams();

  ModelParametersBuilderFactory<MP> getParametersBuilderFactory();

  /**
   *
   * The external Grid API uses a HashMap<String,Object> to describe a set of hyperparameter values,
   * where the String is a valid field name in the corresponding Model.Parameter, and the Object is
   * the field value (boxed as needed).
   */
  class CartesianWalker<MP extends Model.Parameters> implements HyperSpaceWalker<MP> {

    /**
     * Parameters builder factory to create new instance of parameters.
     */
    final transient ModelParametersBuilderFactory<MP> _paramsBuilderFactory;

    /**
     * Used "based" model parameters for this grid search.
     * The object is used as a prototype to create model parameters
     * for each point in hyper space.
     */
    final MP _params;

    /**
     * Hyper space description - in this case only dimension and possible values.
     */
    final private Map<String, Object[]> _hyperParams;
    /**
     * Cached names of used hyper parameters.
     */
    final private String[] _hyperParamNames;

    /**
     * Compute size of hyper space to walk. Includes duplicates (point in space specified multiple
     * times)
     */
    final private int _hyperSpaceSize;

    /**
     *
     * @param paramsBuilderFactory
     * @param hyperParams
     */
    public CartesianWalker(MP params,
                           Map<String, Object[]> hyperParams,
                           ModelParametersBuilderFactory<MP> paramsBuilderFactory) {
      _params = params;
      _hyperParams = hyperParams;
      _paramsBuilderFactory = paramsBuilderFactory;
      _hyperParamNames = hyperParams.keySet().toArray(new String[0]);
      _hyperSpaceSize = computeSizeOfHyperSpace();
    }

    @Override
    public HyperSpaceIterator<MP> iterator() {

      return new HyperSpaceIterator<MP>() {
        /** Hyper params permutation.
         */
        private int[] _hidx = null;

        @Override
        public MP nextModelParameters(Model previousModel) {
          _hidx = _hidx != null ? nextModel(_hidx) : new int[_hyperParamNames.length];
          if (_hidx != null) {
            // Fill array of hyper-values
            Object[] hypers = hypers(_hidx, new Object[_hyperParamNames.length]);
            // Get clone of parameters
            MP commonModelParams = (MP) _params.clone();
            // Fill model parameters
            MP params = getModelParams(commonModelParams, hypers);
            // We have another model parameters
            return params;
          } else {
            throw new NoSuchElementException("No more elements to explore in hyper-space!");
          }
        }

        @Override
        public boolean hasNext(Model previousModel) {
          if (_hidx == null) {
            return true;
          }
          int[] hidx = _hidx;
          for (int i = 0; i < hidx.length; i++) {
            if (hidx[i] + 1 < _hyperParams.get(_hyperParamNames[i]).length) {
              return true;
            }
          }
          return false;
        }

        @Override
        public Object[] getCurrentRawParameters() {
          Object[] hyperValues = new Object[_hyperParamNames.length];
          return hypers(_hidx, hyperValues);
        }
      };
    }

    @Override
    public String[] getHyperParamNames() {
      return _hyperParamNames;
    }

    @Override
    public int getHyperSpaceSize() {
      return _hyperSpaceSize;
    }

    @Override
    public MP getParams() {
      return _params;
    }

    @Override
    public ModelParametersBuilderFactory<MP> getParametersBuilderFactory() {
      return _paramsBuilderFactory;
    }

    // Dumb iteration over the hyper-parameter space.
    // Return NULL at end
    private int[] nextModel(int[] hidx) {
      // Find the next parm to flip
      int i;
      for (i = 0; i < hidx.length; i++) {
        if (hidx[i] + 1 < _hyperParams.get(_hyperParamNames[i]).length) {
          break;
        }
      }
      if (i == hidx.length) {
        return null; // All done, report null
      }
      // Flip indices
      for (int j = 0; j < i; j++) {
        hidx[j] = 0;
      }
      hidx[i]++;
      return hidx;
    }

    private MP getModelParams(MP params, Object[] hyperParams) {
      ModelParametersBuilderFactory.ModelParametersBuilder<MP>
          paramsBuilder = _paramsBuilderFactory.get(params);
      for (int i = 0; i < _hyperParamNames.length; i++) {
        String paramName = _hyperParamNames[i];
        Object paramValue = hyperParams[i];
        paramsBuilder.set(paramName, paramValue);
      }
      return paramsBuilder.build();
    }

    protected int computeSizeOfHyperSpace() {
      int work = 1;
      for (Map.Entry<String, Object[]> p : _hyperParams.entrySet()) {
        if (p.getValue() != null) {
          work *= p.getValue().length;
        }
      }
      return work;
    }

    private Object[] hypers(int[] hidx, Object[] hypers) {
      for (int i = 0; i < hidx.length; i++) {
        hypers[i] = _hyperParams.get(_hyperParamNames[i])[hidx[i]];
      }
      return hypers;
    }
  }

  /**
   * FIXME : finish random walk
   */
  abstract public static class RandomWalker<MP extends Model.Parameters>
      implements HyperSpaceWalker<MP> {

  }
}
