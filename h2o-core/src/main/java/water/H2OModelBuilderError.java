package water;

import hex.Model;
import hex.ModelBuilder;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.util.IcedHashMap;

/**
 * Class which represents a ModelBuilder back-end error which will be returned to the client.
 * Such errors may be caused by the user (specifying bad parameters) or due
 * to a failure which is out of the user's control.
 */
public class H2OModelBuilderError extends H2OError {
  // Expose parameters, messages and error_count in the same was as ModelBuilder so they come out
  // in the H2OModelBuilderError JSON exactly the same way as in the ModelBuilderSchema.

  public Model.Parameters _parameters;
  public ModelBuilder.ValidationMessage[] _messages;
  public int _error_count;

  public H2OModelBuilderError(long timestamp, String error_url, String msg, String dev_msg, int http_status, IcedHashMap.IcedHashMapStringObject values, H2OModelBuilderIllegalArgumentException e) {
    super(timestamp, error_url, msg, dev_msg, http_status, values, e);
    this._parameters = (Model.Parameters) values.get("parameters");
    this._messages = (ModelBuilder.ValidationMessage[]) values.get("messages");
    this._error_count = (int) values.get("error_count");
  }
}
