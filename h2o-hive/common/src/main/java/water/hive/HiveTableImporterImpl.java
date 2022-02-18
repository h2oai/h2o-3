package water.hive;

import org.apache.log4j.Logger;
import water.AbstractH2OExtension;
import water.H2O;
import water.Job;
import water.Key;
import water.api.ImportHiveTableHandler;
import water.fvec.Frame;
import water.parser.CsvParser;
import water.parser.ParseDataset;
import water.parser.ParseSetup;

import java.util.*;

import static water.fvec.Vec.*;
import static water.parser.DefaultParserProviders.GUESS_INFO;
import static water.parser.ParseSetup.NO_HEADER;

@SuppressWarnings("unused") // called via reflection
public class HiveTableImporterImpl extends AbstractH2OExtension implements ImportHiveTableHandler.HiveTableImporter {

  private static final Logger LOG = Logger.getLogger(HiveTableImporterImpl.class);

  private List<HiveMetadataSource> _metadataSources;
  
  @Override
  public String getExtensionName() {
    return NAME;
  }

  @Override
  public void init() {
    _metadataSources = findMetadataSources();
  }

  static synchronized List<HiveMetadataSource> findMetadataSources() {
    List<HiveMetadataSource> sources = new LinkedList<>();
    ServiceLoader<HiveMetadataSource> extensionsLoader = ServiceLoader.load(HiveMetadataSource.class);
    for (HiveMetadataSource src : extensionsLoader) {
      sources.add(src);
    }
    Collections.sort(sources);
    return sources;
  }

  private HiveMetaData getMetaDataClient(String database) {
    for (HiveMetadataSource src : _metadataSources) {
      if (src.canHandle(database)) {
        return src.makeMetadata(database);
      }
    }
    throw new IllegalArgumentException("No implementation is available to handle database: " + database + ".");
  }

  public Job<Frame> loadHiveTable(
      String database,
      String tableName,
      String[][] partitionFilter,
      boolean allowDifferentFormats
  ) throws Exception {
    HiveMetaData.Table table = getMetaDataClient(database).getTable(tableName);
    String targetFrame = "hive_table_" + tableName + Key.rand().substring(0, 10);
    if (!table.hasPartitions()) {
      return loadTable(table, targetFrame);
    } else {
      List<HiveMetaData.Partition> filteredPartitions = filterPartitions(table, partitionFilter);
      if (arePartitionsSameFormat(table, filteredPartitions)) {
        return loadPartitionsSameFormat(table, filteredPartitions, targetFrame);
      } else if (allowDifferentFormats) {
        return loadPartitions(table, filteredPartitions, targetFrame);
      } else {
        throw new IllegalArgumentException("Hive table contains partitions with differing formats. Use allow_multi_format if needed.");
      }
    }
  }
  
  private boolean arePartitionsSameFormat(HiveMetaData.Table table, List<HiveMetaData.Partition> partitions) {
    String tableLib = table.getSerializationLib();
    String tableInput = table.getInputFormat();
    Map<String, String> tableParams = table.getSerDeParams();
    for (HiveMetaData.Partition part : partitions) {
      if (!tableLib.equals(part.getSerializationLib()) ||
          !tableParams.equals(part.getSerDeParams()) ||
          !tableInput.equals(part.getInputFormat())
      ) {
        return false;
      }
    }
    return true;
  }

  private List<HiveMetaData.Partition> filterPartitions(HiveMetaData.Table table, String[][] partitionFilter) {
    if (partitionFilter == null || partitionFilter.length == 0) {
      return table.getPartitions();
    }
    List<List<String>> filtersAsLists = new ArrayList<>(partitionFilter.length);
    for (String[] f : partitionFilter) {
      filtersAsLists.add(Arrays.asList(f));
    }
    List<HiveMetaData.Partition> matchedPartitions = new ArrayList<>(table.getPartitions().size());
    for (HiveMetaData.Partition p : table.getPartitions()) {
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

  private byte getSeparator(HiveMetaData.Storable table) {
    Map<String, String> serDeParams = table.getSerDeParams();
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
  
  private ParseSetup guessTableSetup(Key[] filesKeys, HiveMetaData.Table table) {
    ParseSetup setup = guessSetup(filesKeys, table);
    List<HiveMetaData.Column> tableColumns = table.getColumns();
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
  
  private void checkTableNotEmpty(HiveMetaData.Table table, Key[] filesKeys) {
    if (filesKeys.length == 0) {
      throwTableEmpty(table);
    }
  }

  private void throwTableEmpty(HiveMetaData.Table table) {
    throw new IllegalArgumentException("Table " + table.getName() + " is empty. Nothing to import.");
  }

  private Job<Frame> loadTable(HiveMetaData.Table table, String targetFrame) {
    Key[] filesKeys = importFiles(table.getLocation());
    checkTableNotEmpty(table, filesKeys);
    ParseSetup setup = guessTableSetup(filesKeys, table);
    return parseTable(targetFrame, filesKeys, setup);
  }
  
  private Job<Frame> loadPartitionsSameFormat(HiveMetaData.Table table, List<HiveMetaData.Partition> partitions, String targetFrame) {
    List<Key> fileKeysList = new ArrayList<>();
    int keyCount = table.getPartitionKeys().size();
    List<String[]> partitionValuesMap = new ArrayList<>();
    for (HiveMetaData.Partition p : partitions) {
      Key[] partFileKeys = importFiles(p.getLocation());
      fileKeysList.addAll(Arrays.asList(partFileKeys));
      String[] keyValues = p.getValues().toArray(new String[0]);
      for (Key f : partFileKeys) {
        partitionValuesMap.add(keyValues);
      }
    }
    Key[] filesKeys = fileKeysList.toArray(new Key[0]);
    checkTableNotEmpty(table, filesKeys);
    ParseSetup setup = guessTableSetup(filesKeys, table);
    String[] partitionKeys = new String[table.getPartitionKeys().size()];
    for (int i = 0; i < table.getPartitionKeys().size(); i++) {
      partitionKeys[i] = table.getPartitionKeys().get(i).getName();
    }
    setup.setSyntheticColumns(partitionKeys, partitionValuesMap.toArray(new String[0][]), T_STR);
    return parseTable(targetFrame, filesKeys, setup);
  }
  
  private Job<Frame> loadPartitions(HiveMetaData.Table table, List<HiveMetaData.Partition> partitions, String targetFrame) {
    List<HiveMetaData.Column> partitionColumns = table.getPartitionKeys();
    List<HiveMetaData.Column> tableColumns = table.getColumns();
    String[] columnNames = new String[tableColumns.size()];
    byte[] columnTypes = new byte[columnNames.length];
    fillColumnNamesAndTypes(tableColumns, columnNames, columnTypes);
    List<Job<Frame>> parseJobs = new ArrayList<>(partitions.size());
    for (int i = 0; i < partitions.size(); i++) {
      String partitionKey = "_" + targetFrame + "_part_" + i;
      Job<Frame> job = parsePartition(partitionColumns, partitions.get(i), partitionKey, columnNames, columnTypes);
      if (job != null) {
        parseJobs.add(job);
      }
    }
    if (parseJobs.isEmpty()) {
      throwTableEmpty(table);
    }
    Job<Frame> job = new Job<>(Key.<Frame>make(targetFrame), Frame.class.getName(),"ImportHiveTable");
    PartitionFrameJoiner joiner = new PartitionFrameJoiner(job, table, partitions, targetFrame, parseJobs);
    return job.start(joiner, partitions.size()+1);
  }

  private Job<Frame> parsePartition(List<HiveMetaData.Column> partitionColumns, HiveMetaData.Partition part, String targetFrame, String[] columnNames, byte[] columnTypes) {
    Key[] files = importFiles(part.getLocation());
    if (files.length == 0) {
      return null;
    }
    ParseSetup setup = guessSetup(files, part);
    setup.setColumnNames(columnNames);
    setup.setColumnTypes(columnTypes);
    setup.setNumberColumns(columnNames.length);
    ParseDataset parse = ParseDataset.parse(Key.make(targetFrame), files, true, setup, false);
    return parse._job;
  }

  private void fillColumnNamesAndTypes(List<HiveMetaData.Column> columns, String[] columnNames, byte[] columnTypes) {
    for (int i = 0; i < columns.size(); i++) {
      HiveMetaData.Column col = columns.get(i);
      columnNames[i] = col.getName();
      columnTypes[i] = convertHiveType(col.getType());
    }
  }
  
  private ParseSetup guessSetup(Key[] keys, HiveMetaData.Storable sd) {
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

  static byte convertHiveType(String hiveType) {
    return convertHiveType(hiveType, false);
  }
  
  static byte convertHiveType(final String hiveType, final boolean strict) {
    final String sanitized = sanitizeHiveType(hiveType);
    switch (sanitized) {
      case "tinyint":
      case "smallint":
      case "int":
      case "bigint":
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
      case "binary": // binary could be a UTF8-encoded String (similar to what Parquet does)
        return T_STR;
      case "boolean":
        return T_CAT;
      default:
        if (strict)
          throw new IllegalArgumentException("Unsupported column type: " + hiveType);
        else {
          LOG.warn("Unrecognized Hive type '" + hiveType + "'. Using String type instead.");
          return T_STR;
        }
    }
  }

  static String sanitizeHiveType(String type) {
    int paramIdx = type.indexOf('(');
    if (paramIdx >= 0) {
      type = type.substring(0, paramIdx);
    }
    return type.trim().toLowerCase();
  }
  
}
