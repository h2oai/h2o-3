package water.network;

import water.H2O;
import water.util.Log;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.security.*;
import java.security.cert.CertificateException;

public class SSLSocketChannelFactory {

    private SSLContext sslContext = null;
    private SSLProperties properties = null;

    public SSLSocketChannelFactory() throws SSLContextException {
        try {
            SSLProperties props = new SSLProperties();
            props.load(new FileInputStream(H2O.ARGS.internal_security_conf));
            init(props);
        } catch (IOException e) {
            Log.err("Failed to initialized SSL context.", e);
            throw new SSLContextException("Failed to initialized SSL context.", e);
        }
    }

    public SSLSocketChannelFactory(SSLProperties props) throws SSLContextException {
        init(props);
    }

    private void init(SSLProperties props) throws SSLContextException {
        properties = props;
        try {
            if (requiredParamsPresent()) {
                this.sslContext = SSLContext.getInstance(properties.h2o_ssl_protocol());
                this.sslContext.init(keyManager(), trustManager(), null);
            } else {
                this.sslContext = SSLContext.getDefault();
            }
        } catch (NoSuchAlgorithmException e) {
            Log.err("Failed to initialized SSL context.", e);
            throw new SSLContextException("Failed to initialized SSL context.", e);
        } catch (IOException e) {
            Log.err("Failed to initialized SSL context.", e);
            throw new SSLContextException("Failed to initialized SSL context.", e);
        } catch (UnrecoverableKeyException e) {
            Log.err("Failed to initialized SSL context.", e);
            throw new SSLContextException("Failed to initialized SSL context.", e);
        } catch (KeyStoreException e) {
            Log.err("Failed to initialized SSL context.", e);
            throw new SSLContextException("Failed to initialized SSL context.", e);
        } catch (KeyManagementException e) {
            Log.err("Failed to initialized SSL context.", e);
            throw new SSLContextException("Failed to initialized SSL context.", e);
        } catch (CertificateException e) {
            Log.err("Failed to initialized SSL context.", e);
            throw new SSLContextException("Failed to initialized SSL context.", e);
        }
    }


    private boolean requiredParamsPresent() {
        return null != properties.h2o_ssl_jks_internal() &&
                null != properties.h2o_ssl_jks_password();
    }

    private TrustManager[] trustManager() throws
            KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore ksTrust = KeyStore.getInstance("JKS");

        ksTrust.load(
                new FileInputStream(properties.h2o_ssl_jts()),
                properties.h2o_ssl_jts_password().toCharArray()
        );
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ksTrust);
        return tmf.getTrustManagers();
    }

    private KeyManager[] keyManager() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore ksKeys = KeyStore.getInstance("JKS");

        ksKeys.load(new FileInputStream(properties.h2o_ssl_jks_internal()),
                properties.h2o_ssl_jks_password().toCharArray()
        );
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ksKeys, properties.h2o_ssl_jks_password().toCharArray());
        return kmf.getKeyManagers();
    }

    public ByteChannel wrapClientChannel(
            SocketChannel channel,
            String host,
            int port) throws IOException {
        SSLEngine sslEngine = sslContext.createSSLEngine(host, port);
        sslEngine.setUseClientMode(false);
        if (null != properties.h2o_ssl_enabled_algorithms()) {
            sslEngine.setEnabledCipherSuites(properties.h2o_ssl_enabled_algorithms());
        }
        return new SSLSocketChannel(channel, sslEngine);
    }

    public ByteChannel wrapServerChannel(SocketChannel channel) throws IOException {
        SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(true);
        if (null != properties.h2o_ssl_enabled_algorithms()) {
            sslEngine.setEnabledCipherSuites(properties.h2o_ssl_enabled_algorithms());
        }
        return new SSLSocketChannel(channel, sslEngine);
    }
}
