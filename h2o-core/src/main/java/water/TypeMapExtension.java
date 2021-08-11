package water;

/**
 * Allows to dynamically modify behavior of TypeMap
 */
public interface TypeMapExtension {

    /**
     * Userspace-defined bootstrap classes. Bootstrap classes have fixed and cluster-wide known
     * ids.
     * 
     * Extension can leverage this to facilitate data exchange of serialized objects between different
     * cluster instances. Because the set of bootstrap classes is known it mitigates possibility
     * of java deserialization attack.
     * 
     * This is used eg. in XGBoost external cluster.
     * 
     * @return class names stored in an array
     */
    String[] getBoostrapClasses();

}
