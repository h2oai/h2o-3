package water.hive;

public class DirectHiveMetadataSource implements HiveMetadataSource {

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean canHandle(String databaseSpec) {
        return true;
    }

    @Override
    public HiveMetaData makeMetadata(String databaseSpec) {
        return new DirectHiveMetadata(databaseSpec);
    }
}
