package hex.genmodel;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import hex.genmodel.algos.DrfRawModel;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;


/**
 * Prediction model based on the persisted binary data.
 */
abstract public class RawModel extends GenModel {
    protected ContentReader _reader;
    private hex.ModelCategory _category;
    private String _uuid;
    private boolean _supervised;
    private int _nfeatures;
    protected int _nclasses;

    /**
     * Primary factory method for constructing RawModel instances.
     * @param file Name of the zip file (or folder) with the model's data. This should be the data retrieved via
     *             `GET /3/Models/{model_id}/data` endpoint.
     * @return New `RawModel` object.
     * @throws IOException if `file` does not exist, or cannot be read, or does not represent a valid model.
     */
    static public RawModel load(String file) throws IOException {
        File f = new File(file);
        if (!f.exists())
            throw new FileNotFoundException("File " + file + " cannot be found.");
        ContentReader cr = f.isDirectory()? new FolderContentReader(file) : new ZipfileContentReader(file);
        Map<String, Object> info = parseModelInfo(cr);
        String[] columns = (String[]) info.get("[columns]");
        String[][] domains = parseModelDomains(cr, columns.length, info.get("[domains]"));
        String algo = (String) info.get("algorithm");
        if (algo == null)
            throw new IOException("Model file does not contain information about the model's algorithm.");

        // Create and return the class instance
        if (algo.equals("Distributed Random Forest"))
            return new DrfRawModel(cr, info, columns, domains);
        else
            throw new IOException("Unknown algorithm " + algo + " in model's info.");
    }

    @Override public String getUUID() { return _uuid; }
    @Override public hex.ModelCategory getModelCategory() { return _category; }
    @Override public boolean isSupervised() { return _supervised; }
    @Override public int nfeatures() { return _nfeatures; }
    @Override public int nclasses() { return _nclasses; }


    //------------------------------------------------------------------------------------------------------------------
    // (Private) initialization
    //------------------------------------------------------------------------------------------------------------------

    public RawModel(ContentReader cr, Map<String, Object> info, String[] columns, String[][] domains) {
        super(columns, domains);
        _reader = cr;
        _uuid = (String) info.get("uuid");
        _category = hex.ModelCategory.valueOf((String) info.get("category"));
        _supervised = info.get("supervised").equals("true");
        _nfeatures = (int) info.get("n_features");
        _nclasses = (int) info.get("n_classes");
    }

    static private Map<String, Object> parseModelInfo(ContentReader reader) throws IOException {
        BufferedReader br = reader.getTextFile("model.ini");
        Map<String, Object> info = new HashMap<>();
        String line;
        int section = 0;
        String[] columns = new String[0];  // array of column names, will be initialized later
        int ic = 0;  // Index for `columns` array
        Map<Integer, String> domains = new HashMap<>();  // map of (categorical column index => name of the domain file)
        while (true) {
            line = br.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) continue;
            if (line.equals("[info]"))
                section = 1;
            else if (line.equals("[columns]")) {
                section = 2;  // Enter the [columns] section
                Integer n_columns = (Integer) info.get("n_columns");
                if (n_columns == null)
                    throw new IOException("`n_columns` variable is missing in the model info.");
                columns = new String[n_columns];
                info.put("[columns]", columns);
            } else if (line.equals("[domains]")) {
                section = 3; // Enter the [domains] section
                info.put("[domains]", domains);
            } else if (section == 1) {
                // [info] section: just parse key-value pairs and store them into the `info` map.
                String[] res = line.split("\\s*=\\s*", 2);
                Object value;
                try {
                    // Try to see if the value is convertible to integer
                    value = Integer.parseInt(res[1]);
                } catch (NumberFormatException e) {
                    value = res[1];
                }
                info.put(res[0], value);
            } else if (section == 2) {
                // [columns] section
                columns[ic++] = line;
            } else if (section == 3) {
                // [domains] section
                String[] res = line.split(":\\s*", 2);
                int col_index = Integer.parseInt(res[0]);
                domains.put(col_index, res[1]);
            }
        }
        if (ic != (Integer) info.get("n_columns"))
            throw new IOException("Some of the column names are missing from the model.ini file");
        return info;
    }

    static private String[][] parseModelDomains(ContentReader reader, int n_columns, Object domains_assignment)
            throws IOException {
        String[][] domains = new String[n_columns][];
        // noinspection unchecked
        Map<Integer, String> domass = (Map<Integer, String>) domains_assignment;
        for (Map.Entry<Integer, String> e : domass.entrySet()) {
            int col_index = e.getKey();
            String[] info = e.getValue().split(" ", 2);
            int n_elements = Integer.parseInt(info[0]);
            String domfile = info[1];
            String[] domain = new String[n_elements];
            BufferedReader br = reader.getTextFile("domains/" + domfile);
            String line;
            int id = 0;  // domain elements counter
            while (true) {
                line = br.readLine();
                if (line == null) break;
                domain[id++] = line;
            }
            if (id != n_elements)
                throw new IOException("Not enough elements in the domain file");
            domains[col_index] = domain;
        }
        return domains;
    }



    //------------------------------------------------------------------------------------------------------------------
    // Utility classes for accessing model's data either from a zip file, or from a directory
    //------------------------------------------------------------------------------------------------------------------

    public interface ContentReader {
        BufferedReader getTextFile(String filename) throws IOException;
        byte[] getBinaryFile(String filename) throws IOException;
    }

    static private class FolderContentReader implements ContentReader {
        private String root;

        public FolderContentReader(String folder) {
            root = folder;
        }

        @Override
        public BufferedReader getTextFile(String filename) throws IOException {
            File f = new File(root, filename);
            FileReader fr = new FileReader(f);
            return new BufferedReader(fr);
        }

        @Override
        public byte[] getBinaryFile(String filename) throws IOException {
            File f = new File(root, filename);
            byte[] out = new byte[(int) f.length()];
            DataInputStream dis = new DataInputStream(new FileInputStream(f));
            dis.readFully(out);
            return out;
        }
    }

    static private class ZipfileContentReader implements ContentReader {
        private ZipFile zf;

        public ZipfileContentReader(String zipfile) throws IOException {
            zf = new ZipFile(zipfile);
        }

        @Override
        public BufferedReader getTextFile(String filename) throws IOException {
            InputStream input = zf.getInputStream(zf.getEntry(filename));
            return new BufferedReader(new InputStreamReader(input));
        }

        @Override
        public byte[] getBinaryFile(String filename) throws IOException {
            ZipArchiveEntry za = zf.getEntry(filename);
            byte[] out = new byte[(int) za.getSize()];
            DataInputStream dis = new DataInputStream(zf.getInputStream(za));
            dis.readFully(out);
            return out;
        }
    }
}
