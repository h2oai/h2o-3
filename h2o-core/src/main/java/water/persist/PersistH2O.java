package water.persist;

import org.apache.commons.io.IOUtils;
import water.Key;
import water.util.FrameUtils;
import water.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PersistH2O extends EagerPersistBase {

    public static final String SCHEME = "h2o";
    public static final String PREFIX = SCHEME + "://";
    private static final String H2O_RESOURCE_PATH = "/extdata/";

    private static final List<String> CONTENTS; 

    static {
        List<String> contents = Collections.emptyList();
        try {
            contents = readExtDataContents();
        } catch (IOException e) {
            Log.trace(e);
        }
        CONTENTS = Collections.unmodifiableList(contents);
    }
   
    @Override
    public void importFiles(String path, String pattern,
            /*OUT*/ ArrayList<String> files, ArrayList<String> keys, ArrayList<String> fails, ArrayList<String> dels) {
        try {
            URL resourceURL = pathToURL(path);
            if (resourceURL == null) {
                fails.add(path);
                Log.err("Resource '" + path + "' is not available in H2O.");
                return;
            }
            Key<?> destination_key = FrameUtils.eagerLoadFromURL(path, resourceURL);
            files.add(path);
            keys.add(destination_key.toString());
        } catch (Exception e) {
            Log.err("Loading from `" + path + "` failed.", e);
            fails.add(path);
        }
    }

    static URL pathToURL(String path) {
        String[] pathItems = path.split("://", 2);
        if (!SCHEME.equalsIgnoreCase(pathItems[0])) {
            throw new IllegalArgumentException("Path is expected to start with '" + PREFIX + "', got '" + path + "'.");
        }
        URL resource = PersistH2O.class.getResource(H2O_RESOURCE_PATH + pathItems[1]);
        if (resource == null) {
            resource = PersistH2O.class.getResource(H2O_RESOURCE_PATH + pathItems[1] + ".csv");
        }
        return resource;
    }

    @Override
    public List<String> calcTypeaheadMatches(String filter, int limit) {
        return CONTENTS.stream()
                .filter(it -> it.startsWith(filter))
                .collect(Collectors.toList());
    }

    static List<String> readExtDataContents() throws IOException {
        List<String> contents = new ArrayList<>();
        InputStream is = PersistH2O.class.getResourceAsStream("/extdata.list");
        if (is == null) {
            return Collections.emptyList();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null)
            contents.add(PREFIX + line);
        } finally {
            IOUtils.closeQuietly(is);
        }
        return contents;
    }
    
}
