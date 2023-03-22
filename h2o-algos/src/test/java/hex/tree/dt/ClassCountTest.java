package hex.tree.dt;

import hex.tree.dt.mrtasks.GetClassCountsMRTask;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;


import static hex.tree.dt.binning.Histogram.getFeaturesLimitsForConditions;
import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class ClassCountTest extends TestUtil {

    @Test
    public void testBinningBasicData() {
        try {
            Scope.enter();
            Frame basicData = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    .withDataForCol(1, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
                    .withDataForCol(2, ar("1", "1", "0", "1", "0", "1", "0", "1", "1", "1"))
                    .withColNames("First", "Second", "Prediction")
                    .build();

            Scope.track_generic(basicData);

            DataFeaturesLimits wholeDataLimits = DT.getInitialFeaturesLimits(basicData);

            GetClassCountsMRTask task = new GetClassCountsMRTask(wholeDataLimits.toDoubles(), 2);
            task.doAll(basicData);
            
            assertEquals(3, task._countsByClass[0]);
            assertEquals(7, task._countsByClass[1]);

            DataFeaturesLimits limit0FeaturesLimits = getFeaturesLimitsForConditions(basicData, 
                    wholeDataLimits.updateMax(0, 5.0));
            task = new GetClassCountsMRTask(limit0FeaturesLimits.toDoubles(), 2);
            task.doAll(basicData);

            assertEquals(2, task._countsByClass[0]);
            assertEquals(4, task._countsByClass[1]);

            DataFeaturesLimits limit1FeaturesLimits = getFeaturesLimitsForConditions(basicData,
                    limit0FeaturesLimits.updateMin(1, 1.0));
            task = new GetClassCountsMRTask(limit1FeaturesLimits.toDoubles(), 2);
            task.doAll(basicData);

            assertEquals(0, task._countsByClass[0]);
            assertEquals(4, task._countsByClass[1]);
            
        } finally {
            Scope.exit();
        }

    }
}
