package water.fvec.persist;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.AutoBuffer;
import water.TestUtil;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.IcedLong;

import java.io.File;
import java.net.URI;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class PersistUtilsTest extends TestUtil {

    @Rule
    public transient TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public transient ExpectedException ee = ExpectedException.none();
    
    @Test
    public void testReadWrite_noTypeMap_fail() {
        URI target = new File(temp.getRoot(), "serialized.ice").toURI();
        IcedDummy1 anything = new IcedDummy1(42L);
        PersistUtils.write(target, ab -> ab.put(anything));

        ee.expectMessage("java.lang.ClassNotFoundException:  BAD");
        PersistUtils.read(target, AutoBuffer::get);
    }

    @Test
    public void testReadWrite_noTypeMap_ok() {
        URI target = new File(temp.getRoot(), "serialized_ok.ice").toURI();
        URI typeMapURI = new File(temp.getRoot(), "serialized_ok.map").toURI();

        IcedDummy2 anything = new IcedDummy2(42L);
        PersistUtils.write(target, ab -> ab.put(anything), false);
        PersistUtils.write(typeMapURI, ab -> {}, true);

        short[] typeMap = PersistUtils.read(typeMapURI, AutoBuffer::getTypeMap); // init-typemap first
        IcedDummy2 deserialized = PersistUtils.read(target, AutoBuffer::get, typeMap);
        Assert.assertEquals(anything._val, deserialized._val);
    }


    private static class IcedDummy1 extends IcedLong {
        IcedDummy1(long v) {
            super(v);
        }
    }

    private static class IcedDummy2 extends IcedLong {
        IcedDummy2(long v) {
            super(v);
        }
    }
    
}
