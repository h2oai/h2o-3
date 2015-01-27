package water.api;

/**
 * Summary page referencing all tutorials.
 *
 * @author michal
 */
class TutorialsHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public TutorialsV1 nop(int version, TutorialsV1 ignoreme) { return ignoreme; }
}
