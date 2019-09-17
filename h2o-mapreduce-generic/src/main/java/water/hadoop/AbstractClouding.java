package water.hadoop;

import org.apache.hadoop.conf.Configuration;

public abstract class AbstractClouding extends water.init.AbstractEmbeddedH2OConfig {

  private volatile int _mapperCallbackPort = -1;

  protected abstract void init(Configuration conf) throws Exception;
  
  public final void setMapperCallbackPort(int value) {
    _mapperCallbackPort = value;
  }

  public final void invokeExit(int status) {
    h2omapper.exit(_mapperCallbackPort, status);
  }

}
