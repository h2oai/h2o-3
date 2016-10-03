package water;

import water.network.SSLContextException;
import water.network.SSLSocketChannelFactory;
import water.util.Log;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;

/**
 * Takes care of security.
 *
 * In the long run this class should manage all security aspects of H2O but currently some parts are handled
 * in other parts of the codebase.
 *
 * An instance of this class should be instantiated for each H2O object
 * and should follow its lifecycle.
 *
 * At this stage we support a simple shared secret, handshake based, authentication, which can be turned
 * on with the h2o_ssl_enabled parameter. Should the communicating nodes not share a common shared secret
 * communication between them will not be possible. While using this parameter the user should *not* enable
 * the useUDP parameter, we do not support UDP encryption at this stage and all UDP datagrams will be
 * sent in an unencrypted form!
 *
 * Current state of data encryption:
 *
 *  - HTTP for FlowUI - currently we rely on Jetty's SSL capabilities, authentication can be performed with
 *   hash login, ldap login or kerberos. The location of secret keys used byt Jetty's SSL server should be
 *   passed to the jks parameter.
 *
 *  - inter node communication - all TCP based communication is being authenticated and encrypted using SSL
 *  using JSSE (Java Secure Socket Extension) when then h2o_ssl_enabled parameter is passed. Keystore related
 *  parameter should also be used as per the documentation. Secure UDP communication through DTLS is not supported
 *  at this point in time thus useUDP should not be used for SSL enabled clouds.
 *
 *  - in-memory data encryption - currently not supported, using an encrypted drive is recommended
 *  at least for the swap partition.
 *
 *  - data saved to disk - currently not supported, using an encrypted drive is recommended
 *
 */
public class H2OSecurityManager {

    public boolean securityEnabled = false;
    private SSLSocketChannelFactory sslSocketChannelFactory;

    H2OSecurityManager() {
        try {
            if (null != H2O.ARGS.internal_security_conf) {
                this.sslSocketChannelFactory = new SSLSocketChannelFactory();
                this.securityEnabled = true;
            }
        } catch (SSLContextException e) {
            Log.err("Node initialized with SSL enabled but failed to create SSLContext. " +
                    "Node initialization aborted.");
            Log.err(e);
            H2O.exit(1);
        }
    }

    public ByteChannel wrapServerChannel(SocketChannel channel) throws IOException {
        return sslSocketChannelFactory.wrapServerChannel(channel);
    }

    public ByteChannel wrapClientChannel(SocketChannel channel, String host, int port) throws IOException {
        return sslSocketChannelFactory.wrapClientChannel(channel, host, port);
    }
}
