package hex;

/**
 * Factory for creating model parameters builders.
 *
 * @param <MP>  type of produced model parameters object
 */
public interface ModelParametersBuilderFactory<MP extends Model.Parameters> {

  /** Get parameters builder for initial parameters.
   *
   * <p>
   *   The builder modifies passed parameters object, so the caller
   *   is responsible for cloning it if it is necessary.
   * </p>
   *
   * @param initialParams  initial model parameters which will be modified
   * @return  this parameters builder
   */
  public ModelParametersBuilder<MP> get(MP initialParams);

  /** A generic interface to configure a given initial parameters object
   * via sequence of {@link #set} method calls.
   *
   * <p>
   * The usage is sequence of <code>set</code> calls finalized by
   * <code>build</code> call which produces final version of parameters.
   * </p>
   *
   * @param <MP>  type of produced model parameters object
   */
  public static interface ModelParametersBuilder<MP extends Model.Parameters> {

    public ModelParametersBuilder<MP> set(String name, Object value);

    public MP build();
  }
}
