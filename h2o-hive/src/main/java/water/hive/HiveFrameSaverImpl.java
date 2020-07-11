package water.hive;

import hex.genmodel.utils.IOUtils;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;
import water.AbstractH2OExtension;
import water.H2O;
import water.Key;
import water.api.SaveToHiveTableHandler;
import water.fvec.Frame;
import water.jdbc.SQLManager;
import water.persist.Persist;
import water.persist.PersistHdfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static water.fvec.Vec.*;

public class HiveFrameSaverImpl extends AbstractH2OExtension implements SaveToHiveTableHandler.HiveFrameSaver {

    private static final Logger LOG = Logger.getLogger(HiveTableImporterImpl.class);

    private static final String SQL_DESCRIBE_TABLE = "DESCRIBE %s";

    @Override
    public String getExtensionName() {
        return NAME;
    }

    @Override
    public void saveFrameToHive(Key<Frame> frameKey, String jdbcUrl, String tableName, String configuredTmpPath) {
        String csvPath = null;
        try {
            String tmpPath = determineTmpPath(configuredTmpPath);
            csvPath = new Path(tmpPath, getRandomFileName()).toString();
            LOG.info("Save frame " + frameKey + " to table " + tableName + " in " + jdbcUrl);
            Frame frame = frameKey.get();
            if (frame == null) {
                throw new IllegalArgumentException("Frame with key " + frameKey + " not found.");
            }
            writeFrameToHdfs(frame, csvPath);
            loadCsvIntoTable(jdbcUrl, tableName, frame, csvPath);
        } catch (IOException e) {
            throw new RuntimeException("Writing to Hive failed.", e);
        } finally {
            if (csvPath != null) safelyRemoveCsv(csvPath);
        }
    }
    
    private String determineTmpPath(String configuredTmpPath) throws IOException {
        if (configuredTmpPath == null) {
            FileSystem fs = FileSystem.get(PersistHdfs.CONF);
            String res = fs.getUri().toString() + "/tmp";
            LOG.info("Using default temporary directory " + res);
            return res;
        } else if (!configuredTmpPath.startsWith("hdfs://")) {
            FileSystem fs = FileSystem.get(PersistHdfs.CONF);
            String res = fs.getUri().toString() + "/" + configuredTmpPath;
            LOG.info("Adding file system prefix to relative tmp_path " + res);
            return res;
        } else {
            return configuredTmpPath;
        }
    }
    
    private String getRandomFileName() {
        return "h2o_save_to_hive_" + UUID.randomUUID().toString() + ".csv";
    }

    private void safelyRemoveCsv(String csvPath) {
        try {
            Persist p = H2O.getPM().getPersistForURI(URI.create(csvPath));
            if (p.exists(csvPath)) {
                p.delete(csvPath);
            } else {
                LOG.debug("CSV file moved by Hive, doing nothing.");
            }
        } catch (Exception e) {
            LOG.error("Failed cleaning up CSV file.", e);
        }
    }

    private void writeFrameToHdfs(Frame f, String csvPath) throws IOException {
        Persist p = H2O.getPM().getPersistForURI(URI.create(csvPath));
        try (OutputStream os = p.create(csvPath, false)) {
            Frame.CSVStreamParams parms = new Frame.CSVStreamParams()
                .setHeaders(false)
                .setEscapeQuotes(true)
                .setEscapeChar('\\');
            InputStream is = f.toCSV(parms);
            IOUtils.copyStream(is, os);
        }
    }
    
    private void loadCsvIntoTable(String url, String table, Frame frame, String csvPath) throws IOException {
        try (Connection conn = SQLManager.getConnectionSafe(url, null, null)) {
            if (!doesTableExist(conn, table)) {
                createTable(conn, table, frame);
            } else {
                throw new IllegalArgumentException("Table " + table + " already exists.");
            }
            executeDataLoad(conn, table, csvPath);
        } catch (SQLException e) {
            throw new IOException("Failed to load data into Hive table.", e);
        }
    }

    private boolean doesTableExist(Connection conn, String table) {
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(String.format(SQL_DESCRIBE_TABLE, table))) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private void createTable(Connection conn, String table, Frame frame) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            String createQuery = makeCreateTableStatement(table, frame);
            LOG.info("Creating Hive table " + table + " with SQL: " + createQuery);
            stmt.execute(createQuery);
        }
    }

    /*
        OpenCSV serde will make all columns type string, so there is no reason to create the table
        with different column types. User can then create a view or cast the columns to different
        types when doing SELECT.
     */
    private String makeCreateTableStatement(String table, Frame frame) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(table).append(" (");
        for (int i = 0; i < frame.numCols(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append(frame.name(i)).append(" string");
        }
        sb.append(") ROW FORMAT SERDE 'org.apache.hadoop.hive.serde2.OpenCSVSerde'\n")
            .append("WITH SERDEPROPERTIES (\n")
            .append("   \"separatorChar\" = \",\",\n")
            .append("   \"quoteChar\"     = \"\\\"\",\n")
            .append("   \"escapeChar\"    = \"\\\\\") STORED AS TEXTFILE");
        return sb.toString();
    }

    private void executeDataLoad(Connection conn, String table, String csvPath) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            LOG.info("Loading CSV file " + csvPath + " into table " + table);
            stmt.execute("LOAD DATA INPATH '" + csvPath + "' OVERWRITE INTO TABLE " + table);
        }
    }

}
