package hex.genmodel.attributes;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import hex.genmodel.GenModelTest;
import org.junit.Test;

import java.io.*;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModelJsonReaderTest {

    private JsonElement loadTestingJson() throws Exception {
        URL url = GenModelTest.class.getResource("mojo.zip");
        File file = new File(url.toURI());
        ZipFile zipFile = new ZipFile(file);
        ZipEntry zipEntry = zipFile.getEntry("experimental/modelDetails.json");
        try (InputStream inputStream = zipFile.getInputStream(zipEntry);
            Reader reader = new InputStreamReader(inputStream)) {
            JsonElement result = new Gson().fromJson(reader, JsonElement.class);
            return result;
        }
    }
    
    @Test
    public void testElementExistsOnExistingPath() throws Exception {
        JsonElement testingJson = loadTestingJson();
        assert ModelJsonReader.elementExists(testingJson, "output.training_metrics.model_category");
    }

    @Test
    public void testElementExistsOnNonExistingLeafNode() throws Exception {
        JsonElement testingJson = loadTestingJson();
        assert !ModelJsonReader.elementExists(testingJson, "output.training_metrics.non_existing");
    }

    @Test
    public void testElementExistsOnNullLeafNode() throws Exception {
        JsonElement testingJson = loadTestingJson();
        assert !ModelJsonReader.elementExists(testingJson, "output.training_metrics.description");
    }

    @Test
    public void testElementExistsOnNonExistingRegularNode() throws Exception {
        JsonElement testingJson = loadTestingJson();
        assert !ModelJsonReader.elementExists(testingJson, "output.validation_metrics.model_category");
    }

    @Test
    public void testElementExistsOnNonExistingRootNode() throws Exception {
        JsonElement testingJson = loadTestingJson();
        assert !ModelJsonReader.elementExists(testingJson, "non_existing.training_metrics.model_category");
    }

    @Test
    public void testReadTableOnExistingPath() throws Exception {
        JsonObject testingJson = loadTestingJson().getAsJsonObject();
        assert ModelJsonReader.readTable(testingJson, "output.scoring_history") != null;
    }

    @Test
    public void testReadTableOnNonExistingLeafNode() throws Exception {
        JsonObject testingJson = loadTestingJson().getAsJsonObject();
        assert ModelJsonReader.readTable(testingJson, "output.training_metrics.non_existing") == null;
    }

    @Test
    public void testReadTableOnNullLeafNode() throws Exception {
        JsonObject testingJson = loadTestingJson().getAsJsonObject();
        assert ModelJsonReader.readTable(testingJson, "output.training_metrics.description") == null;
    }

    @Test
    public void testReadTableOnNonExistingRegularNode() throws Exception {
        JsonObject testingJson = loadTestingJson().getAsJsonObject();
        assert ModelJsonReader.readTable(testingJson, "output.validation_metrics.cm") == null;
    }

    @Test
    public void testReadTableOnNonExistingRootNode() throws Exception {
        JsonObject testingJson = loadTestingJson().getAsJsonObject();
        assert ModelJsonReader.readTable(testingJson, "non_existing.training_metrics.cm") == null;
    }
}
