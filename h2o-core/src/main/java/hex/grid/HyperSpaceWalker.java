package hex.grid;

import java.util.Map;

import hex.Model;
import hex.ModelParametersBuilderFactory;

public interface HyperSpaceWalker<MP extends Model.Parameters> {

  /**
   * Get next model parameters.
   *
   * It should return model parameters for next point in hyper space.
   * Return null if there is no remaining point in space to walk.
   *
   * The method can optimize based on previousModel, but should be
   * able to handle null-model.
   * @param previousModel  model generated for the previous point in hyper space, can be null.
   *
   * @return model parameters for next point in hyper space or null if there is no such point.
   */
  public MP nextModelParameters(Model previousModel);

  /**
   * Returns hyper parameters names which are used for walking the hyper parameters space.
   *
   * The names have to match the names of attributes in model parameters MP.
   *
   * @return names of used hyper parameters
   */
  public String[] getHyperParamNames();

  /**
   * Return estimated size of hyperspace.
   *
   * Can return -1 if estimate is not available.
   *
   * @return size of hyper space to explore
   */
  public int getHyperSpaceSize();

  /**
   * Return initial model parameters for search.
   * @return  return model parameters
   */
  public MP getParams();

  /**
   *
   * The external Grid API uses a HashMap<String,Object> to describe a set of hyperparameter values,
   * where the String is a valid field name in the corresponding Model.Parameter, and the Object is
   * the field value (boxed as needed).
   */
  public static class CartesianWalker<MP extends Model.Parameters> implements HyperSpaceWalker<MP> {

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
     * Hyper params permutation.
     */
    private int[] _hidx;
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
      _hidx = null;
      _hyperSpaceSize = computeSizeOfHyperSpace();
    }

    @Override
    public synchronized MP nextModelParameters(Model previousModel) {
      // The method is synchronized since it is changing internal state of the walker
      Object[] hypers = new Object[_hyperParamNames.length];
      _hidx = _hidx != null ? nextModel(_hidx) : new int[_hyperParamNames.length];
      if (_hidx != null) {
        MP params = getModelParams((MP) _params.clone(), hypers(_hidx, hypers));
        return params;
      } else {
        return null;
      }
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
