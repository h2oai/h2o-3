package water.api;

/**
 * Common interface for <s>all</s> some REST endpoint handlers.
 * <p>
 * This class is a preferred way for adding new REST endpoints.
 *
 * @param <IS> input schema class
 * @param <OS> output schema class
 */
public abstract class RestApiHandler<IS extends Schema, OS extends Schema> extends Handler {

  /** Suggested name for the endpoint in external libraries. */
  public abstract String name();

  /** Help for this endpoint (will be used in generated bindings). */
  public abstract String help();

  /**
   * Execute the endpoint, returning the result as the output schema.
   *
   * @param ignored  TODO: remove this parameter
   * @param input  input schema object
   * @return  output schema object
   */
  public abstract OS exec(int ignored, IS input);

}
