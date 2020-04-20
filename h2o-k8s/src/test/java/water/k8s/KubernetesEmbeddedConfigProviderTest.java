package water.k8s;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import static org.junit.Assert.*;

public class KubernetesEmbeddedConfigProviderTest {

    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();
    
    private KubernetesEmbeddedConfigProvider kubernetesEmbeddedConfigProvider;
    
    @Before
    public void beforeTest(){
        environmentVariables.set("H2O_KUBERNETES_SERVICE_DNS", "127.0.0.1");
        kubernetesEmbeddedConfigProvider = new KubernetesEmbeddedConfigProvider();
    }

    @Test
    public void testActiveOnK8SEnvVariablesSet(){
        environmentVariables.set("KUBERNETES_SERVICE_HOST", "127.0.0.1");
        environmentVariables.set("H2O_KUBERNETES_SERVICE_DNS", "127.0.0.1");
        environmentVariables.set("H2O_NODE_LOOKUP_TIMEOUT", "1");
        
        kubernetesEmbeddedConfigProvider.init();
        assertTrue(kubernetesEmbeddedConfigProvider.isActive());
    }
    
    @Test
    public void testInactiveOnK8SEnvVariablesSet(){
        environmentVariables.clear("H2O_KUBERNETES_SERVICE_DNS");
        kubernetesEmbeddedConfigProvider.init();
        assertFalse(kubernetesEmbeddedConfigProvider.isActive());
    }

}
