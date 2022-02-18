package water.hive;

public interface HiveMetadataSource extends Comparable<HiveMetadataSource> {

    int getPriority();

    boolean canHandle(String databaseSpec);

    HiveMetaData makeMetadata(String databaseSpec);

    @Override
    default int compareTo(HiveMetadataSource o) {
        return Integer.compare(o.getPriority(), getPriority());
    }
}
