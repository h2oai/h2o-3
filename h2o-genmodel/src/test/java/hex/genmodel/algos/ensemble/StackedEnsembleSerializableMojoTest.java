package hex.genmodel.algos.ensemble;

import hex.genmodel.ModelMojoReader;
import hex.genmodel.MojoModel;
import hex.genmodel.MojoReaderBackend;
import hex.genmodel.MojoReaderBackendFactory;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.net.URL;


/**
 * Test if StackedEnsembleMojoModel is serializable
 */
public class StackedEnsembleSerializableMojoTest {

    private MojoModel deserialize(final byte[] buffer) throws IllegalStateException {
        final ByteArrayInputStream in = new ByteArrayInputStream(buffer);
        try (final ObjectInputStream objectIn = new ObjectInputStream(in)){
            return (MojoModel) objectIn.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] serialize(final Serializable input) throws IllegalStateException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try(final ObjectOutputStream objectOutput = new ObjectOutputStream(out)) {
            objectOutput.writeObject(input);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void testStackedEnsembleMojoSerialization() throws Exception {
        URL mojoSource = StackedEnsembleRegressionMojoTest.class.getResource("binomial.zip");
        Assert.assertNotNull(mojoSource);
        MojoReaderBackend reader = MojoReaderBackendFactory.createReaderBackend(mojoSource, MojoReaderBackendFactory.CachingStrategy.MEMORY);
        MojoModel model = ModelMojoReader.readFrom(reader);

        try {
            final byte[] buffer = serialize(model);
            Assert.assertNotNull(deserialize(buffer));
        } catch (IllegalStateException e){
            Assert.fail(e.toString());
        }
    }
}
