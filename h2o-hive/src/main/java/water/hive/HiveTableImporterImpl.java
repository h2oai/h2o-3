package water.hive;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.metastore.api.Table;
import water.AbstractH2OExtension;
import water.H2O;
import water.Job;
import water.Key;
import water.api.ImportHiveTableHandler;
import water.fvec.Frame;
import water.parser.ParseDataset;
import water.parser.ParseSetup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static water.fvec.Vec.*;
import static water.parser.DefaultParserProviders.GUESS_INFO;
import static water.parser.ParseSetup.GUESS_SEP;
import static water.parser.ParseSetup.NO_HEADER;

@SuppressWarnings("unused") // called via reflection
public class HiveTableImporterImpl extends AbstractH2OExtension implements ImportHiveTableHandler.HiveTableImporter {

  public static String NAME = "HiveTableImporter";

  @Override
  public String getExtensionName() {
    return NAME;
  }

  public Job<Frame> loadHiveTable(
      String database,
      String tableName,
      String[][] partitionFilter
  ) throws Exception { //, String partitionFilter
    Configuration conf = new Configuration();
    HiveConf hiveConf = new HiveConf(conf, HiveTableImporterImpl.class);

    HiveMetaStoreClient client = new HiveMetaStoreClient(hiveConf);
    if (database == null) {
      database = DEFAULT_DATABASE;
    }
    Table table = client.getTable(database, tableName);
    String targetFrame = "hive_" + database + "_" + tableName;
    List<FieldSchema> columnsToImport = table.getSd().getCols();
    List<Partition> partitions = client.listPartitions(database, tableName, Short.MAX_VALUE);
    String[] paths = getPathsToImport(table, partitions, partitionFilter);
    return load(paths, columnsToImport, targetFrame);
  }

  private String[] getPathsToImport(Table table, List<Partition> partitions, String[][] partitionFilter) {
    if (partitionFilter == null || partitionFilter.length == 0) {
      return new String[]{table.getSd().getLocation()};
    } else {
      return getPartitionPaths(partitions, partitionFilter);
    }
  }

  private String[] getPartitionPaths(List<Partition> partitions, String[][] partitionFilter) {
    List<List<String>> filtersAsLists = new ArrayList<>(partitionFilter.length);
    for (String[] f : partitionFilter) {
      filtersAsLists.add(Arrays.asList(f));
    }
    List<Partition> matchedPartitions = new ArrayList<>(partitions.size());
    for (Partition p : partitions) {
      for (List<String> filter : filtersAsLists) {
        if (p.getValues().equals(filter)) {
          matchedPartitions.add(p);
          break;
        }
      }
    }
    if (matchedPartitions.isEmpty()) {
      throw new IllegalArgumentException("Partition filter did not match any partitions.");
    }
    String[] paths = new String[matchedPartitions.size()];
    for (int i = 0; i < matchedPartitions.size(); i++) {
      paths[i] = matchedPartitions.get(i).getSd().getLocation();
    }
    return paths;
  }

  private Job<Frame> load(String[] paths, List<FieldSchema> columnsToImport, String targetFrame) {
    List<String> importedKeyStrings = importFiles(paths);
    Key[] importedKeys = stringsToKeys(importedKeyStrings);

    ParseSetup parseGuess = new ParseSetup();
    parseGuess.setParseType(GUESS_INFO);
    parseGuess.setSeparator(GUESS_SEP);
    parseGuess.setCheckHeader(NO_HEADER); // TBLPROPERTIES "skip.header.line.count"="1" not supported in metastore API
    ParseSetup setup = ParseSetup.guessSetup(importedKeys, parseGuess);

    String[] columnNames = new String[columnsToImport.size()];
    byte[] columnTypes = new byte[columnsToImport.size()];
    for (int i = 0; i < columnsToImport.size(); i++) {
      FieldSchema col = columnsToImport.get(i);
      columnNames[i] = col.getName();
      columnTypes[i] = convertHiveType(col.getType());
    }
    setup.setColumnNames(columnNames);
    setup.setColumnTypes(columnTypes);
    setup.setNumberColumns(columnNames.length);

    Key destinationKey = Key.<Frame>make(targetFrame);
    ParseDataset parse = ParseDataset.parse(destinationKey, importedKeys, true, setup, false);
    return parse._job;
  }

  private Key[] stringsToKeys(List<String> strings) {
    Key[] keys = new Key[strings.size()];
    for (int i = 0; i < keys.length; i++) {
      keys[i] = Key.<Frame>make(strings.get(i));
    }
    return keys;
  }

  private List<String> importFiles(String[] paths) {
    ArrayList<String> files = new ArrayList<>();
    ArrayList<String> keys = new ArrayList<>();
    ArrayList<String> fails = new ArrayList<>();
    ArrayList<String> dels = new ArrayList<>();
    H2O.getPM().importFiles(paths, null, files, keys, fails, dels);
    if (!fails.isEmpty()) {
      throw new RuntimeException("Failed to import some files: " + fails.toString());
    }
    return keys;
  }

  private Set<String> parseColumnFilter(String filter) {
    Set<String> columnNames = new HashSet<>();
    for (String colName : filter.split(",")) {
      columnNames.add(colName.trim());
    }
    return columnNames;
  }

  private byte convertHiveType(String hiveType) {
    switch (hiveType) {
      case "tinyint":
      case "smallint":
      case "int":
      case "integer":
      case "float":
      case "double":
      case "double precision":
      case "decimal":
      case "numeric":
        return T_NUM;
      case "timestamp":
      case "data":
        return T_TIME;
      case "interval":
      case "string":
      case "varchar":
      case "char":
        return T_STR;
      case "boolean":
        return T_CAT;
      default:
        throw new IllegalArgumentException("Unsupported column type: " + hiveType);
    }
  }

}
