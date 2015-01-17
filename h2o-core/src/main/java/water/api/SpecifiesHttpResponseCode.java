package water.api;

/**
 * Interface which allows a Schema, if returned by a handler method, to specify the HTTP response code.
 */
public interface SpecifiesHttpResponseCode {
  public int httpStatus();
}
