package water.persist;

import com.adobe.testing.s3mock.junit4.S3MockRule;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import hex.Model;
import org.junit.*;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestFrameCatalog;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.dummy.DummyModel;
import water.test.dummy.DummyModelBuilder;
import water.test.dummy.DummyModelParameters;

import java.io.*;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class PersistS3MockTest {

    @ClassRule
    public static S3MockRule S3_MOCK_RULE = new S3MockRule();

    private final AmazonS3 s3 = S3_MOCK_RULE.createS3Client();
    private final PersistS3 persistS3 = new PersistS3();

    @Before
    public void createBucket() {
        s3.createBucket("junit-bucket");
    }

    @Before
    public void injectClient() {
        PersistS3.setClient(s3);
    }

    @After
    public void removeClient() {
        PersistS3.setClient(null);
    }

    @Test
    public void testCreate() throws Exception {
        final String data = "any test data = 42";
        final List<String> lines = Collections.singletonList(data);
        try (OutputStream os = persistS3.create("s3://junit-bucket/h2o-unit-tests/output.csv", false); 
             PrintWriter wr = new PrintWriter(os)) {
            lines.forEach(wr::println);
        }
        try (InputStream is = persistS3.open("s3://junit-bucket/h2o-unit-tests/output.csv");
             BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
            List<String> actualLines = r.lines().collect(Collectors.toList());
            assertEquals(lines, actualLines);
        }
    }

    @Test
    public void testExportBinaryModel() throws IOException {
        try {
            Scope.enter();
            Frame train = TestFrameCatalog.oneChunkFewRows();
            DummyModelParameters p = new DummyModelParameters();
            p._makeModel = true;
            p._train = train._key;
            p._response_column = train.name(0);
            DummyModel model = new DummyModelBuilder(p).trainModel().get();
            assertNotNull(model);
            Scope.track_generic(model);

            URI modelURI = model.exportBinaryModel("s3://junit-bucket/h2o-unit-tests/dummymodel.bin", false);
            try (S3Object s3object = s3.getObject("junit-bucket", "h2o-unit-tests/dummymodel.bin")) {
                assertNotNull(s3object);
            }
            assertTrue(persistS3.exists(modelURI.toString()));

            model.remove();
            Model<?, ?, ?> imported = Model.importBinaryModel(modelURI.toString());
            assertTrue(imported instanceof DummyModel);
        } finally {
            Scope.exit();
        }
    }

}
