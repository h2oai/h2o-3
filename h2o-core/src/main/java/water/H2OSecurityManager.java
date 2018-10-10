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
 * communication between them will not be possible.
 *
 * Current state of data encryption:
 *
 *  - HTTP for FlowUI - currently we rely on Jetty's SSL capabilities, authentication can be performed with
 *   hash login, ldap login or kerberos. The location of secret keys used byt Jetty's SSL server should be
 *   passed to the jks parameter.
 *
 *  - inter node communication - all TCP based communication is being authenticated and encrypted using SSL
 *  using JSSE (Java Secure Socket Extension) when then h2o_ssl_enabled parameter is passed. Keystore related
 *  parameter should also be used as per the documentation.
 *
 *  - in-memory data encryption - currently not supported, using an encrypted drive is recommended
 *  at least for the swap partition.
 *
 *  - data saved to disk - currently not supported, using an encrypted drive is recommended
 *
 */
public class H2OSecurityManager {

    private volatile static H2OSecurityManager INSTANCE = null;

    public final boolean securityEnabled;
    private SSLSocketChannelFactory sslSocketChannelFactory;

    private H2OSecurityManager() {
        this.securityEnabled = H2O.ARGS.internal_security_conf != null;
        try {
            if (null != H2O.ARGS.internal_security_conf) {
                this.sslSocketChannelFactory = new SSLSocketChannelFactory();
                Log.info("H2O node running in encrypted mode using config file [" + H2O.ARGS.internal_security_conf + "]");
            } else {
                Log.info("H2O node running in unencrypted mode.");
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

    public static H2OSecurityManager instance() {
        if(null == INSTANCE) {
            synchronized (H2OSecurityManager.class) {
                if (null == INSTANCE) {
                    INSTANCE = new H2OSecurityManager();
                }
            }
        }
        return INSTANCE;
    }
}
