package water.network;

import sun.security.x509.X500Name;

import java.security.*;
import java.security.cert.X509Certificate;

/**
 * Java guys decided that moving CertAndKeyGen to a different
 * package in Java8 is a good idea so we need to improvise
 */
class CertAndKeyGenWrapper {

    private final Object certAndKeyGen;
    private Class<?> cl;

    CertAndKeyGenWrapper(String algo, String prov, String val) throws Exception {
        try {
            // Java 6 and 7
            cl = ClassLoader.getSystemClassLoader().loadClass("sun.security.x509.CertAndKeyGen");
        } catch (ClassNotFoundException e) {
            // Java 8
            cl = ClassLoader.getSystemClassLoader().loadClass("sun.security.tools.keytool.CertAndKeyGen");
        }
        certAndKeyGen = cl.getConstructor(String.class, String.class, String.class).newInstance(algo, prov, val);
    }

    void generate(int b) throws Exception {
        cl.getMethod("generate", int.class).invoke(certAndKeyGen, b);
    }

    X509Certificate getSelfCertificate(X500Name name, long validity) throws Exception {
        return (X509Certificate) cl.getMethod("getSelfCertificate", X500Name.class, long.class).invoke(certAndKeyGen, name, validity);
    }

    PrivateKey getPrivateKey() throws Exception {
        return (PrivateKey) cl.getMethod("getPrivateKey").invoke(certAndKeyGen);
    }

}
