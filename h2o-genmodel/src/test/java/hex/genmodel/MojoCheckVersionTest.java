package hex.genmodel;

import org.junit.Test;
import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.*;

public class MojoCheckVersionTest {

    @Test
    public void testHigherMojoVersionAgainstReaderVersion() {
        try {
            URL mojo_zip = MojoReaderBackendFactoryTest.class.getResource("mojo_modified_version.zip");
            assertNotNull(mojo_zip);
            MojoReaderBackend backend = MojoReaderBackendFactory.createReaderBackend(mojo_zip, MojoReaderBackendFactory.CachingStrategy.MEMORY);
            ModelMojoReader.readFrom(backend);
            fail("Expects to throw IOException about MOJO incompatibility.");
        } catch (IOException e) {
            assertTrue("Expects to throw IOException about MOJO incompatibility.", e.toString().contains("MOJO version incompatibility"));
        }
    }

    @Test
    public void testCorrectMojoVersionAgainstReaderVersion(){
        try {
            URL mojo_zip = MojoReaderBackendFactoryTest.class.getResource("mojo.zip");
            assertNotNull(mojo_zip);
            MojoReaderBackend backend = MojoReaderBackendFactory.createReaderBackend(mojo_zip, MojoReaderBackendFactory.CachingStrategy.MEMORY);
            ModelMojoReader.readFrom(backend);
        } catch (IOException e){
            assertFalse("Expects not to throw IOException about MOJO incompatibility.", e.toString().contains("MOJO version incompatibility"));
            fail(e.toString());
        }
    }
}
