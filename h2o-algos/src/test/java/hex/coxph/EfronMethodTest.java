package hex.coxph;

import org.junit.Test;
import water.LocalMR;

import static org.junit.Assert.*;

public class EfronMethodTest {

    @Test
    public void makeEfronMRTask() {
        LocalMR<EfronUpdateFun> mr = EfronMethod.makeEfronMRTask(null, 10);
        assertTrue(mr.isReproducible());
    }

}
