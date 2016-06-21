package water.api.schemas3;


import water.H2OModelBuilderError;
import water.api.API;
import water.api.SpecifiesHttpResponseCode;

/**
 * Schema which represents a back-end error from the model building process which will be
 * returned to the client.  Such errors may be caused by the user (specifying bad mode
 * building parameters) or due to a failure which is out of the user's control.
 *
 * <p> NOTE: parameters, validation_messages and error_count are in the schema in two
 * places.  This is intentional, so that a client can handle it like any other H2OErrorV1
 * by just rendering the values map, or like ModelBuilderSchema by looking at those fields
 * directly.
 * @see ModelBuilderJobV3
 */
public class H2OModelBuilderErrorV3 extends H2OErrorV3<H2OModelBuilderError, H2OModelBuilderErrorV3> implements SpecifiesHttpResponseCode {
  @API(help="Model builder parameters.", direction = API.Direction.OUTPUT)
  public ModelParametersSchemaV3 parameters;

  @API(help="Parameter validation messages", direction=API.Direction.OUTPUT)
  public ValidationMessageV3 messages[];

  @API(help="Count of parameter validation errors", direction=API.Direction.OUTPUT)
  public int error_count;
}
