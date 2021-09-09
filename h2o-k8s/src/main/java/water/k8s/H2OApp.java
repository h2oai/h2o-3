package water.k8s;

import water.H2O;
import water.init.AbstractEmbeddedH2OConfig;

public class H2OApp {

    public static void main(String[] args) {

        if (H2O.checkUnsupportedJava())
            System.exit(1);

        KubernetesEmbeddedConfigProvider p = new KubernetesEmbeddedConfigProvider();
        
        p.init();
        
        if (p.isActive()) {
            AbstractEmbeddedH2OConfig config = p.getConfig();
            H2O.setEmbeddedH2OConfig(config);
        }
        
        water.H2OApp.start(args, System.getProperty("user.dir"));
    }

}
