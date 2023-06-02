package hex;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.TestUtil;
import water.runner.H2ORunner;

@RunWith(H2ORunner.class)
public class ModelTest extends TestUtil {

    @Test
    public void testBooleanAryChecksum() {
        BooleanAryFieldParameter ps = new BooleanAryFieldParameter();
        ps._booleans = new boolean[]{true, false, true};

        ps.checksum(); // just check no exception is thrown
    }

    public static class BooleanAryFieldParameter extends Model.Parameters {
        boolean[] _booleans;

        @Override
        public String algoName() {
            return "b";
        }

        @Override
        public String fullName() {
            return "b";
        }

        @Override
        public String javaName() {
            return "B";
        }

        @Override
        public long progressUnits() {
            return 0;
        }
    }

}
