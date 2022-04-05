package water.persist.security;

import org.apache.hadoop.conf.Configuration;
import water.H2O;
import water.init.StandaloneKerberosComponent;
import water.util.Log;

import java.time.Duration;

public class HdfsComponent implements StandaloneKerberosComponent {

    @Override
    public String name() {
        return "SecuredHdfs";
    }

    @Override
    public int priority() {
        return 2000;
    }

    @Override
    public boolean initComponent(Object confObject, H2O.OptArgs args) {
        if (args.hdfs_token_refresh_interval == null) {
            return false;
        }
        Configuration conf = (Configuration) confObject;
        long refreshIntervalSecs = parseRefreshIntervalToSecs(args.hdfs_token_refresh_interval);
        Log.info("HDFS token will be refreshed every " + refreshIntervalSecs + 
                "s (user specified " + args.hdfs_token_refresh_interval + ").");
        HdfsDelegationTokenRefresher.startRefresher(conf, args.principal, args.keytab_path, refreshIntervalSecs);
        return true;
    }

    static long parseRefreshIntervalToSecs(String refreshInterval) {
        try {
            if (!refreshInterval.contains("P")) { // convenience - allow user to specify just "10M", instead of requiring "PT10M"
                refreshInterval = "PT" + refreshInterval;
            }
            return Duration.parse(refreshInterval.toLowerCase()).getSeconds();
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse refresh interval, got " + refreshInterval +
                    ". Example of correct specification '4H' (token will be refreshed every 4 hours).", e);
        }
    }

}
