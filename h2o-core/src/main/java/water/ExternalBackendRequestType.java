package water;

/**
 * Main task codes
 */
enum ExternalBackendRequestType {

  WRITE_TO_CHUNK((byte) 0),
  DOWNLOAD_FRAME((byte) 1),
  INIT_FRAME((byte) 2),
  FINALIZE_FRAME((byte) 3);

  private final byte num;

  ExternalBackendRequestType(byte num) {
    this.num = num;
  }

  byte getByte() {
    return num;
  }

  static ExternalBackendRequestType fromByte(byte num) {

    for (ExternalBackendRequestType type : values()) {
      if (type.getByte() == num) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unsupported Request Type");
  }
}
    
