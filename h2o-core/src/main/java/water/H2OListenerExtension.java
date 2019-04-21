package water;

public interface H2OListenerExtension {

  /** Name of listener extension */
  String getName();

  /** Initialize the extension */
  void init();

  void report(String ctrl, Object... data);
}
