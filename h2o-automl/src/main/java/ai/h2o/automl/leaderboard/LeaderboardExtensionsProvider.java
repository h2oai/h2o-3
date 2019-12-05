package ai.h2o.automl.leaderboard;

import hex.Model;
import water.Iced;

public abstract class LeaderboardExtensionsProvider extends Iced<LeaderboardExtensionsProvider> {

    public static final String ALL = "ALL";

    public abstract LeaderboardColumn[] createExtensions(Model model);

}
