package water.hadoop.clouding.fs;

public enum CloudingEventType {
  NODE_STARTED("node", 0),
  NODE_CLOUDED("leader", 1),
  NODE_FAILED("exit", 3, true);

  String _code;
  int _priority;
  boolean _fatal;

  CloudingEventType(String code, int priority, boolean isFatal) {
    _code = code;
    _fatal = isFatal;
    _priority = priority;
  }

  CloudingEventType(String code, int priority) {
    this(code, priority, false);
  }

  static CloudingEventType fromCode(String code) {
    for (CloudingEventType v : CloudingEventType.values()) {
      if (v._code.equals(code)) {
        return v;
      }
    }
    return null;
  }

  public boolean isFatal() {
    return _fatal;
  }

}
