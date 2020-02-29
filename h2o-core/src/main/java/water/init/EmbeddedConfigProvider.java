package water.init;

public interface EmbeddedConfigProvider {

    default String getName() {
        return getClass().getName();
    }

    /**
     * Provider initialization. Guaranteed to be called before any other method is called, including the`isActive`
     * method.
     */
    void init();

    /**
     * Whether the provider is active and should be used by H2O.
     *
     * @return True if H2O should use this {@link EmbeddedConfigProvider}, otherwise false.
     */
    default boolean isActive() {
        return false;
    }

    /**
     * @return An instance of {@link AbstractEmbeddedH2OConfig} configuration. Never null.
     */
    AbstractEmbeddedH2OConfig getConfig();

}
