package water;


/**  A Distributed Exception - an exception originally thrown on one node
 *  and passed to another.
 */
public class DException extends Iced {
  final H2ONode _h2o;           // Original throwing node
  final String _exClass;        // Structural breakdown of the original exception
  final String _throwerClass;   // Class of thrower (probably subclass of MRTask)
  final DException _cause;
  final String _msg;
  final Stk[] _stk;

  DException( Throwable ex, Class thrower_clz ) {
    _h2o = H2O.SELF;
    Throwable cex = ex.getCause();
    while( ex instanceof DistributedException && cex != null )
      { ex = cex; cex = ex.getCause(); }
    _throwerClass = thrower_clz.toString();
    _exClass = ex.getClass().toString();
    _cause = cex==null ? null : new DException(cex,thrower_clz);
    _msg = ex.getMessage();
    StackTraceElement stk[] = ex.getStackTrace();
    _stk = new Stk[stk.length];
    for( int i=0; i<stk.length; i++ )
      _stk[i] = new Stk(stk[i]);
  }

  DistributedException toEx() {
    String msg = "from "+_h2o+"; by "+_throwerClass+"; "+_exClass+": "+_msg;
    DistributedException e = new DistributedException(msg,_cause==null ? null : _cause.toEx());
    StackTraceElement stk[] = new StackTraceElement[_stk.length];
    for( int i=0; i<_stk.length; i++ )
      stk[i] = _stk[i].toSTE();
    e.setStackTrace(stk);
    return e;
  }

  private static class Stk extends Iced {
    String _cls, _mth, _fname;
    int _line;
    Stk( StackTraceElement stk ) {
      _cls = stk.getClassName();
      _mth = stk.getMethodName();
      _fname = stk.getFileName();
      _line = stk.getLineNumber();
    }
    public StackTraceElement toSTE() { return new StackTraceElement(_cls,_mth,_fname,_line); }
  }
  /** Simple named exception class, inflated from a deserialized {@link DException}. */
  public static class DistributedException extends RuntimeException {
    DistributedException( String msg, Throwable cause ) { super(msg,cause); }
  }
}
