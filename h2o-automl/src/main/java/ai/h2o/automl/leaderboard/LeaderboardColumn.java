package ai.h2o.automl.leaderboard;

/**
 * Meta info for a leaderboard column.
 */
public class LeaderboardColumn {

    private final String _name;
    private final String _type;
    private final String _format;

    public LeaderboardColumn(String name, String type, String format) {
        _name = name;
        _type = type;
        _format = format;
    }

    public String getName() { return _name; }
    public String getType() { return _type; }
    public String getFormat() { return _format; }
}
