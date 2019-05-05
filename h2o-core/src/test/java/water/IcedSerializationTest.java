package water;

import org.junit.Before;
import org.junit.Test;


public class IcedSerializationTest extends TestUtil {

    @Before
    public void setUp() throws Exception {
        TestUtil.stall_till_cloudsize(1);
    }

    private static final class Outer extends Lockable<Outer> {
        
        private Inner f;
        
        public final class Inner extends Iced<Inner>{
            
        }
        
        /**
         * Create a Lockable object, if it has a {@link Key}.
         *
         * @param key
         */
        public Outer(Key<Outer> key) {
            super(key);
            f = new Inner();
        }
    }

    @Test
    public void test() {
        try {
            Scope.enter();
            final Key<Outer> key = Key.make();
            final Outer outer = new Outer(key);
            Scope.track_generic(outer);

            outer.write_lock();
            outer.unlock();
        } finally {
            Scope.exit();
        }
    }
}
