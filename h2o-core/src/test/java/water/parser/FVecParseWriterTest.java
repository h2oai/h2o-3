package water.parser;

import org.junit.Test;
import water.fvec.AppendableVec;
import water.fvec.Vec;

public class FVecParseWriterTest {

    @Test(timeout = 10000)
    public void addNumCol() {
        FVecParseWriter writer = new FVecParseWriter(
                Vec.VectorGroup.VG_LEN1,
                1,
                new Categorical[0],
                new byte[0],
                1,
                new AppendableVec[0]
        );
        writer.addNumCol(1, 2E19);
    }
}