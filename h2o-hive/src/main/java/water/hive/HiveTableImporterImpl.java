package water.hive;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.metastore.api.StorageDescriptor;
import org.apache.hadoop.hive.metastore.api.Table;
import water.AbstractH2OExtension;
import water.H2O;
import water.Job;
import water.Key;
import water.api.ImportHiveTableHandler;
import water.fvec.Frame;
import water.parser.CsvParser;
import water.parser.ParseDataset;
import water.parser.ParseSetup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static water.fvec.Vec.*;
import static water.parser.DefaultParserProviders.GUESS_INFO;
import static water.parser.ParseSetup.NO_HEADER;

@SuppressWarnings("unused") // called via reflection
public class HiveTableImporterImpl extends AbstractH2OExtension implements ImportHiveTableHandler.HiveTableImporter {

  private static String NAME = "HiveTableImporter";

  @Override
  public String getExtensionName() {
    return NAME;
  }

  public Job<Frame> loadHiveTable(
      String database,
      String tableName,
      String[][] partitionFilter
  ) throws Exception {
    Configuration conf = new Configuration();
    HiveConf hiveConf = new HiveConf(conf, HiveTableImporterImpl.class);

    HiveMetaStoreClient client = new HiveMetaStoreClient(hiveConf);
    if (database == null) {
      database = DEFAULT_DATABASE;
    }
    Table table = client.getTable(database, tableName);
    String targetFrame = "hive_" + database + "_" + tableName;
    List<Partition> partitions = client.listPartitions(database, tableName, Short.MAX_VALUE);
    if (partitions.isEmpty()) {
      return loadTable(table, targetFrame);
    } else {
      List<Partition> filteredPartitions = filterPartitions(partitions, partitionFilter);
      if (arePartitionsSameFormat(table, filteredPartitions)) {
        return loadPartitionsSameFormat(table, filteredPartitions, targetFrame);
      } else {
        return loadPartitions(table, filteredPartitions, targetFrame);
      }
    }
  }
  
  private boolean arePartitionsSameFormat(Table table, List<Partition> partitions) {
    String tableLib = table.getSd().getSerdeInfo().getSerializationLib();
    String tableInput = table.getSd().getInputFormat();
    Map<String, String> tableParams = table.getSd().getSerdeInfo().getParameters();
    for (Partition part : partitions) {
      if (!tableLib.equals(part.getSd().getSerdeInfo().getSerializationLib()) ||
          !tableParams.equals(part.getSd().getSerdeInfo().getParameters()) ||
          !tableInput.equals(part.getSd().getInputFormat())
      ) {
        return false;
      }
    }
    return true;
  }

  private List<Partition> filterPartitions(List<Partition> partitions, String[][] partitionFilter) {
    if (partitionFilter == null || partitionFilter.length == 0) {
      return partitions;
    }
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
    return matchedPartitions;
  }

  private byte getSeparator(StorageDescriptor sd) {
    Map<String, String> serDeParams = sd.getSerdeInfo().getParameters();
    String explicitSeparator = serDeParams.get("field.delim"); // for basic TextFormat
    if (explicitSeparator != null && !explicitSeparator.isEmpty()) {
      return (byte) explicitSeparator.charAt(0);
    }
    
    explicitSeparator = serDeParams.get("separatorChar"); // for OpenCSV
    if (explicitSeparator != null && !explicitSeparator.isEmpty()) {
      return (byte) explicitSeparator.charAt(0);
    } else {
      return CsvParser.HIVE_SEP;
    }
  }
  
  private ParseSetup guessTableSetup(Key[] filesKeys, Table table) {
    ParseSetup setup = guessSetup(filesKeys, table.getSd());
    List<FieldSchema> tableColumns = table.getSd().getCols();
    String[] columnNames = new String[tableColumns.size()];
    byte[] columnTypes = new byte[tableColumns.size()];
    fillColumnNamesAndTypes(tableColumns, columnNames, columnTypes);
    setup.setColumnNames(columnNames);
    setup.setColumnTypes(columnTypes);
    setup.setNumberColumns(columnNames.length);
    return setup;
  }
  
  private Job<Frame> parseTable(String targetFrame, Key[] filesKeys, ParseSetup setup) {
    Key<Frame> destinationKey = Key.make(targetFrame);
    ParseDataset parse = ParseDataset.parse(destinationKey, filesKeys, true, setup, false);
    return parse._job;
  }

  private Job<Frame> loadTable(Table table, String targetFrame) {
    Key[] filesKeys = importFiles(table.getSd().getLocation());
    ParseSetup setup = guessTableSetup(filesKeys, table);
    return parseTable(targetFrame, filesKeys, setup);
  }
  
  private Job<Frame> loadPartitionsSameFormat(Table table, List<Partition> partitions, String targetFrame) {
    List<Key> fileKeysList = new ArrayList<>();
    int keyCount = table.getPartitionKeysSize();
    Map<String, String[]> partitionValuesMap = new HashMap<>();
    for (Partition p : partitions) {
      Key[] partFileKeys = importFiles(p.getSd().getLocation());
      fileKeysList.addAll(Arrays.asList(partFileKeys));
      String[] keyValues = p.getValues().toArray(new String[0]);
      for (Key f : partFileKeys) {
        partitionValuesMap.put(f.toString(), keyValues);
      }
    }
    Key[] filesKeys = fileKeysList.toArray(new Key[0]);
    ParseSetup setup = guessTableSetup(filesKeys, table);
    String[] partitionKeys = new String[table.getPartitionKeys().size()];
    for (int i = 0; i < table.getPartitionKeys().size(); i++) {
      partitionKeys[i] = table.getPartitionKeys().get(i).getName();
    }
    setup.setSyntheticColumns(partitionKeys, partitionValuesMap);
    return parseTable(targetFrame, filesKeys, setup);
  }
  
  private Job<Frame> loadPartitions(Table table, List<Partition> partitions, String targetFrame) {
    List<FieldSchema> partitionColumns = table.getPartitionKeys();
    List<FieldSchema> tableColumns = table.getSd().getCols();
    String[] columnNames = new String[tableColumns.size()];
    byte[] columnTypes = new byte[columnNames.length];
    fillColumnNamesAndTypes(tableColumns, columnNames, columnTypes);
    List<Job<Frame>> parseJobs = new ArrayList<>(partitions.size());
    for (int i = 0; i < partitions.size(); i++) {
      String partitionKey = "_" + targetFrame + "_part_" + i;
      Job<Frame> job = parsePartition(partitionColumns, partitions.get(i), partitionKey, columnNames, columnTypes);
      parseJobs.add(job);
    }
    Job<Frame> job = new Job<>(Key.<Frame>make(targetFrame), Frame.class.getName(),"ImportHiveTable");
    PartitionFrameJoiner joiner = new PartitionFrameJoiner(job, table, partitions, targetFrame, parseJobs);
    return job.start(joiner, partitions.size()+1);
  }

  private Job<Frame> parsePartition(List<FieldSchema> partitionColumns, Partition part, String targetFrame, String[] columnNames, byte[] columnTypes) {
    Key[] files = importFiles(part.getSd().getLocation());
    ParseSetup setup = guessSetup(files, part.getSd());
    setup.setColumnNames(columnNames);
    setup.setColumnTypes(columnTypes);
    setup.setNumberColumns(columnNames.length);
    ParseDataset parse = ParseDataset.parse(Key.make(targetFrame), files, true, setup, false);
    return parse._job;
  }

  private void fillColumnNamesAndTypes(List<FieldSchema> columns, String[] columnNames, byte[] columnTypes) {
    for (int i = 0; i < columns.size(); i++) {
      FieldSchema col = columns.get(i);
      columnNames[i] = col.getName();
      columnTypes[i] = convertHiveType(col.getType());
    }
  }
  
  private ParseSetup guessSetup(Key[] keys, StorageDescriptor sd) {
    ParseSetup parseGuess = new ParseSetup();
    parseGuess.setParseType(GUESS_INFO);
    parseGuess.setSeparator(getSeparator(sd));
    parseGuess.setCheckHeader(NO_HEADER); // TBLPROPERTIES "skip.header.line.count"="1" not supported in metastore API
    return ParseSetup.guessSetup(keys, parseGuess);
  }

  private Key[] stringsToKeys(List<String> strings) {
    Key[] keys = new Key[strings.size()];
    for (int i = 0; i < keys.length; i++) {
      keys[i] = Key.make(strings.get(i));
    }
    return keys;
  }

  private Key[] importFiles(String path) {
    ArrayList<String> files = new ArrayList<>();
    ArrayList<String> keys = new ArrayList<>();
    ArrayList<String> fails = new ArrayList<>();
    ArrayList<String> dels = new ArrayList<>();
    H2O.getPM().importFiles(path, null, files, keys, fails, dels);
    if (!fails.isEmpty()) {
      throw new RuntimeException("Failed to import some files: " + fails.toString());
    }
    return stringsToKeys(keys);
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
