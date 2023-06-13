package water.persist;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.s3a.S3AFileSystem;
import water.H2O;
import water.util.ReflectionUtils;

import java.io.IOException;
import java.net.URI;

public class S3AClientFactory implements S3ClientFactory {

    private static final String PROP_S3A_FACTORY_PROTOTYPE_URI = "persist.s3a.factoryPrototypeUri";

    @Override
    public <T> T getOrMakeClient(String bucket, Object configuration) {
        if (configuration != null && !(configuration instanceof Configuration)) {
            throw new IllegalArgumentException("Configuration not instance of org.apache.hadoop.conf.Configuration");
        }
        Configuration hadoopConf = configuration != null ? (Configuration) configuration : PersistHdfs.CONF;
        try {
            String path = bucket != null ? "s3a://" + bucket + "/" : getDefaultPrototypeUri();
            FileSystem fs = getFileSystem(URI.create(path), hadoopConf);
            if (fs instanceof S3AFileSystem) {
                return ReflectionUtils.getFieldValue(fs, "s3"); 
            } else {
                throw new IllegalStateException("File system corresponding to schema s3a is not an instance of S3AFileSystem, " +
                        "it is " + (fs != null ? fs.getClass().getName() : "undefined") + ".");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getDefaultPrototypeUri() {
        return H2O.getSysProperty(PROP_S3A_FACTORY_PROTOTYPE_URI, "s3a://www.h2o.ai/");
    }

    protected FileSystem getFileSystem(URI uri, Configuration conf) throws IOException {
        return FileSystem.get(uri, conf);
    }
    
}
