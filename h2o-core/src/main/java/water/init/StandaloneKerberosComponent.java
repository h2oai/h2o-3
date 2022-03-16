package water.init;

import water.H2O;

import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Interface of a component that needs to be initialized during boot of H2O running
 * in a Standalone mode in Kerberos environment
 */
public interface StandaloneKerberosComponent {

    /**
     * Name of the component
     * @return short identifier of the component
     */
    String name();

    /**
     * Initialization priority - components with higher priority will be initialized before
     * components with lower priority. Third parties can use 0-999, 1000+ is reserved for internal H2O components.
     * @return initialization priority of the component
     */
    int priority();

    /**
     * Initializes the component, called after Kerberos is initialized in Standalone mode
     * 
     * @param conf instance of Hadoop Configuration object
     * @param args parsed H2O arguments
     * @return flag indicating if component was successfully initialized 
     */
    boolean initComponent(Object conf, H2O.OptArgs args);

    static List<StandaloneKerberosComponent> loadAll() {
        ServiceLoader<StandaloneKerberosComponent> componentLoader = ServiceLoader.load(StandaloneKerberosComponent.class);
        return StreamSupport
                .stream(componentLoader.spliterator(), false)
                .sorted(Comparator.comparingInt(StandaloneKerberosComponent::priority).reversed())
                .collect(Collectors.toList());
    }

}
