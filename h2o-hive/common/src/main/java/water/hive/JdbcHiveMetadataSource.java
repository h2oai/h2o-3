package water.hive;

public class JdbcHiveMetadataSource implements HiveMetadataSource {

    @Override
    public int getPriority() {
        return 1000;
    }

    @Override
    public boolean canHandle(String databaseSpec) {
        return databaseSpec != null && databaseSpec.startsWith("jdbc:");
    }

    @Override
    public HiveMetaData makeMetadata(String databaseSpec) {
        return new JdbcHiveMetadata(databaseSpec);
    }
}
