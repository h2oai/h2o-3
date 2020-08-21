package water.network;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;

public interface WrappingSecurityManager {
  
  boolean isSecurityEnabled();

  ByteChannel wrapServerChannel(SocketChannel channel) throws IOException;

  ByteChannel wrapClientChannel(SocketChannel channel, String host, int port) throws IOException;

}
