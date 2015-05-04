package water.api;


import water.H2OModelBuilderError;

/**
 * Schema which represents a back-end error from the model building process which will be
 * returned to the client.  Such errors may be caused by the user (specifying bad mode
 * building parameters) or due to a failure which is out of the user's control.
 *
 * <p> NOTE: parameters, validation_messages and error_count are in the schema in two
 * places.  This is intentional, so that a client can handle it like any other H2OErrorV1
 * by just rendering the values map, or like ModelBuilderSchema by looking at those fields
 * directly.
 */
public class H2OModelBuilderErrorV3 extends H2OErrorV3<H2OModelBuilderError, H2OModelBuilderErrorV3> implements SpecifiesHttpResponseCode {
  @API(help="Model builder parameters.", direction = API.Direction.OUTPUT)
  public ModelParametersSchema parameters;

  @API(help="Parameter validation messages", direction=API.Direction.OUTPUT)
  public ModelParametersSchema.ValidationMessageV3 validation_messages[];

  @API(help="Count of parameter validation errors", direction=API.Direction.OUTPUT)
  public int validation_error_count;
}
