package water.hive;

import org.apache.hadoop.conf.Configuration;
import water.H2O;
import water.init.StandaloneKerberosComponent;

public class HiveComponent implements StandaloneKerberosComponent {

    @Override
    public String name() {
        return "SecuredHive";
    }

    @Override
    public int priority() {
        return 1000;
    }

    @Override
    public boolean initComponent(Object conf, H2O.OptArgs args) {
        return DelegationTokenRefresher.startRefresher((Configuration) conf, args);
    }

}
