package ai.h2o.automl.leaderboard;

import hex.Model;
import water.Iced;

/**
 * This provider allows a model generator (e.g. {@link ai.h2o.automl.AutoML}) to produce
 * optional columns for the leadeboard created from the models.
 */
public abstract class LeaderboardExtensionsProvider extends Iced<LeaderboardExtensionsProvider> {

    /**
     * Alias for "all extensions" where a list of extensions names is required.
     */
    public static final String ALL = "ALL";

    /**
     * Generates the extensions cells for a given model.
     * It is expected that all cells associated to a model are from a different extension,
     * i.e. they should all have a different {@link LeaderboardCell#getColumn()}.
     * @param model
     * @return an array of @{link LeaderboardCell} for the given model.
     */
    public abstract LeaderboardCell[] createExtensions(Model model);

}
