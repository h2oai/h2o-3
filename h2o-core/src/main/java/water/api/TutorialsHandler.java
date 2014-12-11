package water.api;

/**
 * Summary page referencing all tutorials.
 *
 * @author michal
 */
class TutorialsHandler extends Handler {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public TutorialsV1 nop(int version, TutorialsV1 ignoreme) { return ignoreme; }
}
