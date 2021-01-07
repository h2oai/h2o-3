package ai.h2o.targetencoding;

import org.apache.commons.lang.ArrayUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;
import static water.TestUtil.parse_test_file;
import static water.TestUtil.printOutFrameAsTable;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TargetEncoderFeatureInteractionTest {
    
    @Test
    public void test_interactions_encoded_column_is_created() {
        try {
            Scope.enter();
            Frame train = parse_test_file("./smalldata/testng/airlines_train.csv");
            Scope.track(train);
            Frame test = parse_test_file("./smalldata/testng/airlines_test.csv");
            Scope.track(test);

            TargetEncoderModel.TargetEncoderParameters params = new TargetEncoderModel.TargetEncoderParameters();
            params._train = train._key;
            params._response_column = "IsDepDelayed";
            params._columns_to_encode = new String[][] {
                    new String[]{"Origin"},
                    new String[]{"fYear", "fMonth"}
            };
            params._seed = 0XFEED;
            TargetEncoder te = new TargetEncoder(params);
            final TargetEncoderModel teModel = te.trainModel().get();
            Scope.track_generic(teModel);
            assertNotNull(teModel);
            printOutFrameAsTable(teModel._output._target_encoding_map.get("fYear~fMonth"));
            final Frame encoded = teModel.score(test);
            printOutFrameAsTable(encoded);
            Scope.track(encoded);

            assertNotNull(encoded);
            assertEquals(train.numCols() + 2, encoded.numCols());
            final int[] encodedColIdx = new int[] {
                    ArrayUtils.indexOf(encoded.names(), "Origin_te"),
                    ArrayUtils.indexOf(encoded.names(), "fYear~fMonth_te"),
            };
            for (int colIdx : encodedColIdx) {
                assertNotEquals(-1, colIdx);
                assertTrue(encoded.vec(colIdx).isNumeric());
            }
        } finally {
            Scope.exit();
        }
    }

}
