package water.exceptions;

import hex.Model;
import hex.ModelBuilder;
import water.H2OModelBuilderError;
import water.util.IcedHashMap;

public class H2OModelBuilderIllegalArgumentException extends H2OIllegalArgumentException {
  /** Raw-message constructor for use by the factory method. */
  private H2OModelBuilderIllegalArgumentException(String message, String dev_message) {
    super(message, dev_message);
  }

  public static H2OModelBuilderIllegalArgumentException makeFromBuilder(ModelBuilder builder) {
    Model.Parameters parameters = builder._parms;
    String algo = builder._parms.algoName();
    String msg = "Illegal argument(s) for " + algo + " model: " + builder.dest() + ".  Details: " + builder.validationErrors();

    H2OModelBuilderIllegalArgumentException exception = new H2OModelBuilderIllegalArgumentException(msg, msg);

    exception.values = new IcedHashMap.IcedHashMapStringObject();
    exception.values.put("algo", algo);
    exception.values.put("parameters", parameters);
    exception.values.put("error_count", builder.error_count());
    exception.values.put("messages", builder._messages);

    return exception;
  }

  public H2OModelBuilderError toH2OError() {
    return new H2OModelBuilderError(timestamp, null, getMessage(), dev_message, HTTP_RESPONSE_CODE(), values, this);
  }

  public H2OModelBuilderError toH2OError(String error_url) {
    return new H2OModelBuilderError(timestamp, error_url, getMessage(), dev_message, HTTP_RESPONSE_CODE(), values, this);
  }
}
