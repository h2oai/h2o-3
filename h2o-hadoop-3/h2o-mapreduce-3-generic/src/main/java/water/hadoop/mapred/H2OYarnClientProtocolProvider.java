package water.hadoop.mapred;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.MRConfig;
import org.apache.hadoop.mapreduce.protocol.ClientProtocol;
import org.apache.hadoop.mapreduce.protocol.ClientProtocolProvider;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * H2O specific yarn client provider.
 *
 * The provider can be selected by providing: `-Dmapreduce.framework.name=h2o-yarn`
 *
 * It is loaded using SPI. Different protocol provides are specified in
 * org.apache.hadoop.mapreduce.protocol.ClientProtocolProvider service file
 *
 */
public class H2OYarnClientProtocolProvider extends ClientProtocolProvider {

  @Override
  public ClientProtocol create(Configuration conf) throws IOException {
    if ("h2o-yarn".equals(conf.get(MRConfig.FRAMEWORK_NAME))) {
      return new H2OYARNRunner(conf);
    }
    return null;
  }

  @Override
  public ClientProtocol create(InetSocketAddress addr, Configuration conf)
      throws IOException {
    return create(conf);
  }

  @Override
  public void close(ClientProtocol clientProtocol) throws IOException {
    // nothing to do
  }
}
