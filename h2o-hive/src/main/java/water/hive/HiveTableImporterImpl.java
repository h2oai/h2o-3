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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static water.fvec.Vec.*;
import static water.parser.DefaultParserProviders.GUESS_INFO;
import static water.parser.ParseSetup.GUESS_SEP;

@SuppressWarnings("unused") // called via reflection
public class HiveTableImporterImpl extends AbstractH2OExtension implements ImportHiveTableHandler.HiveTableImporter {

  public static String NAME = "HiveTableImporter";

  @Override
  public String getExtensionName() {
    return NAME;
  }

  public Job<Frame> loadHiveTable(String database, String tableName) throws Exception {
    Configuration conf = new Configuration();
    HiveConf hiveConf = new HiveConf(conf, HiveTableImporterImpl.class);

    HiveMetaStoreClient client = new HiveMetaStoreClient(hiveConf);
    if (database == null) {
      database = DEFAULT_DATABASE;
    }
    Table table = client.getTable(database, tableName);
    String targetFrame = "hive_" + database + "_" + tableName;
    List<FieldSchema> columnsToImport = table.getSd().getCols();
    String path = table.getSd().getLocation();

    List<Partition> partitions = client.listPartitions(database, tableName, Short.MAX_VALUE);
    for (Partition partition : partitions) {
      System.out.println();
    }

    return load(path, columnsToImport, targetFrame);
  }
  
  private Job<Frame> load(String path, List<FieldSchema> columnsToImport, String targetFrame) {
    List<String> importedKeyStrings = importFiles(path);
    Key[] importedKeys = stringsToKeys(importedKeyStrings);

    ParseSetup parseGuess = new ParseSetup();
    parseGuess.setParseType(GUESS_INFO);
    parseGuess.setSeparator(GUESS_SEP);
    ParseSetup setup = ParseSetup.guessSetup(importedKeys, parseGuess);
    
    // TODO setup.setCheckHeader();
    // TODO "skip.header.line.count"="1"
    // TODO "skip.footer.line.count"="2"

    String[] columnNames = new String[columnsToImport.size()];
    byte[] columnTypes = new byte[columnsToImport.size()];
    for (int i = 0; i < columnsToImport.size(); i++) {
      FieldSchema col = columnsToImport.get(i);
      columnNames[i] = col.getName();
      columnTypes[i] = convertHiveType(col.getType());
    }
    setup.setColumnNames(columnNames);
    setup.setColumnTypes(columnTypes);

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
  
  private List<String> importFiles(String path) {
    ArrayList<String> files = new ArrayList<>();
    ArrayList<String> keys = new ArrayList<>();
    ArrayList<String> fails = new ArrayList<>();
    ArrayList<String> dels = new ArrayList<>();
    H2O.getPM().importFiles(path, null, files, keys, fails, dels);
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
