package water;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class KeyTest {
    
    @Test
    public void testBuild_cache() {
        Assert.assertEquals(10762, Key.build_cache(10, 42));
        Assert.assertEquals(16777215, Key.build_cache(255, 65535));
        Assert.assertEquals(16777215, Key.build_cache(255, 65535*2+1));
    }

    @Test
    public void testCloud() {
        Assert.assertEquals(0, Key.cloud(Key.build_cache(0, 42)));
        Assert.assertEquals(10, Key.cloud(Key.build_cache(10, 42)));
        Assert.assertEquals(255, Key.cloud(Key.build_cache(255, 42)));
        Assert.assertEquals(0, Key.cloud(Key.build_cache(255+1, 42)));
    }

    @Test
    public void testHome() {
        Assert.assertEquals(0, Key.home(Key.build_cache(10, 0)));
        Assert.assertEquals(42, Key.home(Key.build_cache(10, 42)));
        Assert.assertEquals(65535, Key.home(Key.build_cache(10, 65535)));
        Assert.assertEquals(0, Key.home(Key.build_cache(10, 65535+1)));
    }

    @Test
    public void testHomedKeyConsistency() { // to show that refactored code behaves like the old one for the key-homing use case
        H2ONode other = H2O.CLOUD.members()[(H2O.SELF.index() + 1) % H2O.CLOUD.size()];
        Key<?> key = Key.make("homed key", Key.JOB, true, other);
        byte[] bytes = key._kb;
        Assert.assertEquals(Key.JOB, bytes[0]);
        Assert.assertEquals(1, bytes[1]); // indicator that key is specifically homed
        byte[] oldBytes = old_make_with_replicas("homed key".getBytes(), Key.JOB, true, other);
        Assert.assertArrayEquals(oldBytes, bytes);
        Assert.assertEquals(key.D(), other.index());
    }

    @Test
    public void testInvalidHomedKeyConsistency() {
        H2ONode leader = H2O.CLOUD.leader();
        H2ONode invalid = H2ONode.intern(
                leader._key.getAddress(), 
                leader._key.getApiPort() - 2
        );
        Key<?> key = Key.make("homed key", Key.JOB, false, invalid);
        byte[] bytes = key._kb;
        Assert.assertEquals(Key.JOB, bytes[0]);
        Assert.assertEquals(0, bytes[1]); // indicator that key is NOT specifically homed
        byte[] oldBytes = old_make_with_replicas("homed key".getBytes(), Key.JOB, false, invalid);
        Assert.assertArrayEquals(oldBytes, bytes);
        Assert.assertNotEquals(-1, key.D()); // just check it won't crash
    }

    // this is the original version of the method that supported up to 3 replicas
    private static byte[] old_make_with_replicas(byte[] kb, byte systemType, boolean required, H2ONode... replicas) {
        // no more than 3 replicas allowed to be stored in the key
        assert replicas.length <= 3;
        assert systemType<32; // only system keys allowed
        boolean inCloud=true;
        for( H2ONode h2o : replicas ) if( !H2O.CLOUD.contains(h2o) ) inCloud = false;
        if( required ) assert inCloud; // If required placement, error to find a client as the home
        else if( !inCloud ) replicas = new H2ONode[0]; // If placement is a hint & cannot be placed, then ignore

        // Key byte layout is:
        // 0 - systemType, from 0-31
        // 1 - replica-count, plus up to 3 bits for ip4 vs ip6
        // 2-n - zero, one, two or 3 IP4 (4+2 bytes) or IP6 (16+2 bytes) addresses
        // 2-5- 4 bytes of chunk#, or -1 for masters
        // n+ - repeat of the original kb
        AutoBuffer ab = new AutoBuffer();
        ab.put1(systemType).put1(replicas.length);
        for( H2ONode h2o : replicas )
            h2o.write(ab);
        ab.put4(-1);
        ab.putA1(kb,kb.length);
        return Arrays.copyOf(ab.buf(),ab.position());
    }

    @Test
    public void testMakeHomedKey() {
        for (H2ONode h2o : H2O.CLOUD.members()) {
            HomedKeyTask task = RPC.call(h2o, new HomedKeyTask()).get();
            Assert.assertEquals(h2o, task._self.home_node());
            Assert.assertEquals(h2o, task._job_self.home_node());
        }
    }

    static class HomedKeyTask extends DTask<HomedKeyTask> {

        Key<?> _self;
        Key<?> _job_self;

        @Override
        public void compute2() {
            _self = Key.make(H2O.SELF);
            _job_self = Key.make(Key.JOB, true, H2O.SELF);
            tryComplete();
        }
    }

}
