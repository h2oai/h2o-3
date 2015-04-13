package water.api;

/**
 * Summary page referencing all tutorials.
 *
 * @author michal
 */
class TutorialsHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public TutorialsV3 nop(int version, TutorialsV3 ignoreme) { return ignoreme; }
}
