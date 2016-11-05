package water.util;

import water.AutoBuffer;
import water.Freezable;
import water.H2O;
import water.TypeMap;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Created by tomas on 3/2/16.
 */
public final class DistributedException extends RuntimeException  {
  public DistributedException(){truncateStackTrace(true);}
  public DistributedException(Throwable cause){ this(cause,true);}
  public DistributedException(Throwable cause, boolean keepStackTrace){
    super("DistributedException from " + H2O.SELF,cause);
    truncateStackTrace(keepStackTrace);
  }
  public DistributedException(String msg, Throwable cause){
    super(msg,cause);
    try {
      truncateStackTrace(true);
    }catch(Throwable t) {
      // just in case it throws, do nothing, truncating stacktrace not really that important
    }
  }
  public String toString(){return getMessage() + ", caused by " + getCause().toString();}
  private void truncateStackTrace(boolean keepStackTrace){
    if(keepStackTrace) {
      StackTraceElement[] stackTrace = getStackTrace();
      int i = 0;
      for (; i < stackTrace.length; ++i)
        if (stackTrace[i].getFileName() != null && stackTrace[i].getFileName().equals("JettyHTTPD.java"))
          break;
      setStackTrace(Arrays.copyOf(stackTrace, i));
    } else setStackTrace(new StackTraceElement[0]);
  }
}
