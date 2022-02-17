package water.hive;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.thrift.TException;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static water.api.ImportHiveTableHandler.HiveTableImporter.DEFAULT_DATABASE;

public class DirectHiveMetadata implements HiveMetaData {
    
    private final String database;

    public DirectHiveMetadata(String database) {
        if (database == null || database.isEmpty()) {
            this.database = DEFAULT_DATABASE;
        } else {
            this.database = database;
        }
    }

    static class HivePartition implements Partition {

        private final org.apache.hadoop.hive.metastore.api.Partition partition;

        HivePartition(org.apache.hadoop.hive.metastore.api.Partition partition) {
            this.partition = partition;
        }

        @Override
        public List<String> getValues() {
            return partition.getValues();
        }

        @Override
        public Map<String, String> getSerDeParams() {
            return partition.getSd().getSerdeInfo().getParameters();
        }

        @Override
        public String getLocation() {
            return partition.getSd().getLocation();
        }

        @Override
        public String getSerializationLib() {
            return partition.getSd().getSerdeInfo().getSerializationLib();
        }

        @Override
        public String getInputFormat() {
            return partition.getSd().getInputFormat();
        }
    }
    
    static class HiveColumn implements Column {

        private final FieldSchema column;

        HiveColumn(FieldSchema column) {
            this.column = column;
        }

        @Override
        public String getName() {
            return column.getName();
        }

        @Override
        public String getType() {
            return column.getType();
        }
    }
    
    static class HiveTable implements Table {
        
        private final org.apache.hadoop.hive.metastore.api.Table table;
        
        private final List<Partition> partitions;

        private final List<Column> columns;

        private final List<Column> partitionKeys;

        HiveTable(org.apache.hadoop.hive.metastore.api.Table table, List<org.apache.hadoop.hive.metastore.api.Partition> parts) {
            this.table = table;
            this.partitions = parts.stream().map(HivePartition::new).collect(toList());
            this.columns = table.getSd().getCols().stream().map(HiveColumn::new).collect(toList());
            this.partitionKeys = table.getPartitionKeys().stream().map(HiveColumn::new).collect(toList());
        }

        @Override
        public String getName() {
            return table.getTableName();
        }

        @Override
        public boolean hasPartitions() {
            return !partitions.isEmpty();
        }

        @Override
        public List<Partition> getPartitions() {
            return partitions;
        }

        @Override
        public List<Column> getColumns() {
            return columns;
        }

        @Override
        public Map<String, String> getSerDeParams() {
            return table.getSd().getSerdeInfo().getParameters();
        }

        @Override
        public String getLocation() {
            return table.getSd().getLocation();
        }

        @Override
        public String getSerializationLib() {
            return table.getSd().getSerdeInfo().getSerializationLib();
        }

        @Override
        public String getInputFormat() {
            return table.getSd().getInputFormat();
        }

        @Override
        public List<Column> getPartitionKeys() {
            return partitionKeys;
        }
    }
    

    @Override
    public Table getTable(String tableName) throws TException {
        Configuration conf = new Configuration();
        HiveConf hiveConf = new HiveConf(conf, HiveTableImporterImpl.class);
        HiveMetaStoreClient client = new HiveMetaStoreClient(hiveConf);
        org.apache.hadoop.hive.metastore.api.Table table = client.getTable(database, tableName);
        List<org.apache.hadoop.hive.metastore.api.Partition> partitions = client.listPartitions(database, tableName, Short.MAX_VALUE);
        return new HiveTable(table, partitions);
    }

}
