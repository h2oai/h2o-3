package water.network;

import water.SecurityManager;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * Creates either a raw or an SSL/TLS wrapped socket depending on
 * the node's configuration. All sockets used in the application should be
 * created using this class.
 */
public class SocketChannelFactory {

    private SecurityManager sm;

    public SocketChannelFactory(SecurityManager sm) {
        this.sm = sm;
    }

    public ByteChannel serverChannel(ByteChannel channel) throws IOException {
        if(sm.securityEnabled && !(channel instanceof SSLSocketChannel)) {
            return sm.wrapServerChannel((SocketChannel)channel);
        } else {
            return channel;
        }
    }

    public ByteChannel clientChannel(ByteChannel channel, String host, int port) throws IOException {
        if(sm.securityEnabled && !(channel instanceof SSLSocketChannel)) {
            return sm.wrapClientChannel((SocketChannel)channel, host, port);
        } else {
            return channel;
        }
    }

}
