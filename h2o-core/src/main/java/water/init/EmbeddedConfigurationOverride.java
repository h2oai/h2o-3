package water.init;

import java.util.Optional;

/**
 * Configuration overrides set by implementations of {@link AbstractEmbeddedH2OConfig}. Immutable.
 * Use the {@link Builder} class to create an instance.
 * <p>
 * All values are optional. If a value is an empty optional, this means the {@link AbstractEmbeddedH2OConfig} has no preference
 * of that value and H2O should decide such setting.
 */
public class EmbeddedConfigurationOverride {

    private Optional<Boolean> disableNonLeaderApi = Optional.empty();

    /**
     * Private constructor. Use the Builder pattern.
     */
    private EmbeddedConfigurationOverride() {
    }

    /**
     * @return True if API should be disabled on non-leader nodes. Otherwise false. An empty optional means
     * no preference.
     */
    public Optional<Boolean> getDisableNonLeaderApi() {
        return disableNonLeaderApi;
    }

    private void setDisableNonLeaderApi(final Optional<Boolean> disableNonLeaderApi) {
        this.disableNonLeaderApi = disableNonLeaderApi;
    }

    /**
     * Class implementing the builder pattern for {@link EmbeddedConfigurationOverride}.
     */
    public static class Builder {
        private final EmbeddedConfigurationOverride configurationOverride;

        /**
         * Constructs a new builder with clean {@link EmbeddedConfigurationOverride} status.
         */
        public Builder() {
            configurationOverride = new EmbeddedConfigurationOverride();
        }

        /**
         * @return A reference to an instance of {@link EmbeddedConfigurationOverride} in a state represented by this builder.
         * Returns reference to the same object if called multiple times.
         */
        public EmbeddedConfigurationOverride build() {
            return configurationOverride;
        }

        /**
         * @param disableNonLeaderNodeApi Set to true of non-leader node APIs should remain disabled. Set to false to
         *                                forcefully leave them open. Leave unset for no preference.
         * @return This instance of {@link Builder}
         */
        public Builder withDisableNonLeaderApi(final boolean disableNonLeaderNodeApi) {
            configurationOverride.setDisableNonLeaderApi(Optional.of(disableNonLeaderNodeApi));
            return this;
        }
    }

}
