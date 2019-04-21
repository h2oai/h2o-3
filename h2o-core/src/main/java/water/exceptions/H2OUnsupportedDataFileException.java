package water.exceptions;

import water.util.IcedHashMapGeneric;

/**
 * Exception thrown by a parser when a file format is recognized but a certain feature used
 * in the particular data file is not supported (eg. nested structures).
 */
public class H2OUnsupportedDataFileException extends H2OAbstractRuntimeException {

  public H2OUnsupportedDataFileException(String message, String dev_message, IcedHashMapGeneric.IcedHashMapStringObject values) {
    super(message, dev_message, values);
  }

  public H2OUnsupportedDataFileException(String message, String dev_message) {
    super(message, dev_message, new IcedHashMapGeneric.IcedHashMapStringObject());
  }

}
