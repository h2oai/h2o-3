package water;


public class Job extends Iced {
  static class JobCancelledException extends RuntimeException {
  }
  public static class ProgressMonitor {
    public void update( int len ) { throw H2O.unimpl(); }
  }
}
