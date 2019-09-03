package water.hadoop;

import org.apache.hadoop.conf.Configuration;

abstract class AbstractClouding extends water.init.AbstractEmbeddedH2OConfig {

  private volatile int _mapperCallbackPort = -1;

  abstract void init(Configuration conf) throws Exception;
  
  void setMapperCallbackPort(int value) {
    _mapperCallbackPort = value;
  }

  void invokeExit(int status) {
    h2omapper.exit(_mapperCallbackPort, status);
  }

}
