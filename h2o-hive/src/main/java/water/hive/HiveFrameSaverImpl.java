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
import water.fvec.Vec;
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
import java.util.UUID;

public class HiveFrameSaverImpl extends AbstractH2OExtension implements SaveToHiveTableHandler.HiveFrameSaver {

    private static final Logger LOG = Logger.getLogger(HiveTableImporterImpl.class);

    private static final String SQL_DESCRIBE_TABLE = "DESCRIBE %s";

    @Override
    public String getExtensionName() {
        return NAME;
    }

    @Override
    public void saveFrameToHive(
        Key<Frame> frameKey,
        String jdbcUrl,
        String tableName,
        Format format,
        String configuredTablePath,
        String configuredTmpPath
    ) {
        String filePath = null;
        try {
            String tmpPath = determineTmpPath(configuredTmpPath);
            String storagePath = addHdfsPrefixToPath(configuredTablePath);
            filePath = new Path(tmpPath, getRandomFileName(format)).toString();
            LOG.info("Save frame " + frameKey + " to table " + tableName + " in " + jdbcUrl);
            Frame frame = frameKey.get();
            if (frame == null) {
                throw new IllegalArgumentException("Frame with key " + frameKey + " not found.");
            }
            writeFrameToHdfs(frame, filePath, format);
            loadDataIntoTable(jdbcUrl, tableName, storagePath, frame, filePath, format);
        } catch (IOException e) {
            throw new RuntimeException("Writing to Hive failed: " + e.getMessage(), e);
        } finally {
            if (filePath != null) safelyRemoveDataFile(filePath);
        }
    }

    private String determineTmpPath(String configuredTmpPath) throws IOException {
        if (configuredTmpPath == null) {
            FileSystem fs = FileSystem.get(PersistHdfs.CONF);
            String res = fs.getUri().toString() + "/tmp";
            LOG.info("Using default temporary directory " + res);
            return res;
        } else {
            return addHdfsPrefixToPath(configuredTmpPath);
        }
    }
    
    private String addHdfsPrefixToPath(String path) throws IOException {
        if (path == null) {
            return null;
        } else if (!path.startsWith("hdfs://")) {
            FileSystem fs = FileSystem.get(PersistHdfs.CONF);
            String res = fs.getUri().toString() + "/" + path;
            LOG.info("Adding file system prefix to relative tmp_path " + res);
            return res;
        } else {
            return path;
        }
    }

    private String getRandomFileName(Format format) {
        return "h2o_save_to_hive_" + UUID.randomUUID().toString() + "." + format.toString().toLowerCase();
    }

    private void safelyRemoveDataFile(String filePath) {
        try {
            Persist p = H2O.getPM().getPersistForURI(URI.create(filePath));
            if (p.exists(filePath)) {
                p.delete(filePath);
            } else {
                LOG.debug("Data file moved by Hive, doing nothing.");
            }
        } catch (Exception e) {
            LOG.error("Failed cleaning up data file.", e);
        }
    }

    private void writeFrameToHdfs(Frame frame, String filePath, Format format) throws IOException {
        switch (format) {
            case CSV:
                writeFrameAsCsv(frame, filePath);
                break;
            case PARQUET:
                writeFrameAsParquet(frame, filePath);
                break;
            default:
                throw new IllegalArgumentException("Unsupported table format " + format);
        }
    }

    private void writeFrameAsParquet(Frame frame, String filePath) throws IOException {
        new FrameParquetWriter().write(frame, filePath);
    }

    private void writeFrameAsCsv(Frame f, String filePath) throws IOException {
        Persist p = H2O.getPM().getPersistForURI(URI.create(filePath));
        try (OutputStream os = p.create(filePath, false)) {
            Frame.CSVStreamParams parms = new Frame.CSVStreamParams()
                .setHeaders(false)
                .setEscapeQuotes(true)
                .setEscapeChar('\\');
            InputStream is = f.toCSV(parms);
            IOUtils.copyStream(is, os);
        }
    }

    private void loadDataIntoTable(
        String url,
        String table,
        String tablePath,
        Frame frame,
        String filePath,
        Format format
    ) throws IOException {
        try (Connection conn = SQLManager.getConnectionSafe(url, null, null)) {
            if (!doesTableExist(conn, table)) {
                createTable(conn, table, tablePath, frame, format);
            } else {
                throw new IllegalArgumentException("Table " + table + " already exists.");
            }
            executeDataLoad(conn, table, filePath);
        } catch (SQLException e) {
            throw new IOException("Failed to load data into Hive table: " + e.getMessage(), e);
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

    private void createTable(Connection conn, String table, String tablePath, Frame frame, Format format) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            String createQuery = makeCreateTableStatement(table, tablePath, frame, format);
            LOG.info("Creating Hive table " + table + " with SQL: " + createQuery);
            stmt.execute(createQuery);
        }
    }

    private String makeCreateTableStatement(String table, String tablePath, Frame frame, Format format) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE ");
        if (tablePath != null) {
            sb.append("EXTERNAL ");
        }
        sb.append("TABLE ").append(table).append(" (");
        switch (format) {
            case CSV:
                makeCreateCSVTableStatement(sb, frame);
                break;
            case PARQUET:
                makeCreateParquetTableStatement(sb, frame);
                break;
            default:
                throw new IllegalArgumentException("Unsupported table format " + format);
        }
        if (tablePath != null) {
            sb.append("\nLOCATION '").append(tablePath).append("'");
        }
        return sb.toString();
    }

    private void makeCreateCSVTableStatement(StringBuilder sb, Frame frame) {
        for (int i = 0; i < frame.numCols(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append(frame.name(i)).append(" string");
        }
        sb.append(") ROW FORMAT SERDE 'org.apache.hadoop.hive.serde2.OpenCSVSerde'\n")
            .append("WITH SERDEPROPERTIES (\n")
            .append("   \"separatorChar\" = \",\",\n")
            .append("   \"quoteChar\"     = \"\\\"\",\n")
            .append("   \"escapeChar\"    = \"\\\\\") STORED AS TEXTFILE");
    }

    private void makeCreateParquetTableStatement(StringBuilder sb, Frame frame) {
        for (int i = 0; i < frame.numCols(); i++) {
            if (i > 0) sb.append(",\n");
            sb.append(frame.name(i)).append(" ").append(sqlDataType(frame.vec(i)));
        }
        sb.append(") STORED AS parquet");
    }

    private String sqlDataType(Vec v) {
        if (v.isCategorical() || v.isUUID() || v.isString()) {
            return "STRING";
        } else if (v.isInt()) {
            return "BIGINT";
        } else {
            return "DOUBLE";
        }
    }

    private void executeDataLoad(Connection conn, String table, String filePath) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            LOG.info("Loading data file " + filePath + " into table " + table);
            stmt.execute("LOAD DATA INPATH '" + filePath + "' OVERWRITE INTO TABLE " + table);
        }
    }

}
