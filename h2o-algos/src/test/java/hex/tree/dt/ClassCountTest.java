package hex.tree.dt;

import hex.tree.dt.mrtasks.GetClassCountsMRTask;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
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
    public void testClassCountBasicData() {
        try {
            Scope.enter();
            Frame basicData = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT)
                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
                    // domain: ["one", "three", "two"] - encoding of categorical feature (alphabetically)
                    .withDataForCol(1, ar("two", "one", "three", "two", "two", "one", "one", "one", "three", "three"))
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
            
            DataFeaturesLimits limitCategoricalFeaturesLimits = getFeaturesLimitsForConditions(basicData,
                    limit0FeaturesLimits.updateMask(1, new boolean[]{true, false, true})); // one + two
            task = new GetClassCountsMRTask(limitCategoricalFeaturesLimits.toDoubles(), 2);
            task.doAll(basicData);

            assertEquals(1, task._countsByClass[0]);
            assertEquals(4, task._countsByClass[1]);
            
        } finally {
            Scope.exit();
        }

    }

    @Test
    public void testClassCountSmalldata() {
        try {
            Scope.enter();
            Frame data = Scope.track(parseTestFile("smalldata/testng/airlines_train.csv"));
//            data.replace(0, data.vec(0).toCategoricalVec()).remove();
            // manually put prediction column as the last one
            Vec response = data.remove("IsDepDelayed");
            data.add("IsDepDelayed", response);
            DKV.put(data);

            Scope.track_generic(data);

            DataFeaturesLimits wholeDataLimits = DT.getInitialFeaturesLimits(data);

            // data.IsDepDelayed.value_counts()
            GetClassCountsMRTask task = new GetClassCountsMRTask(wholeDataLimits.toDoubles(), 2);
            task.doAll(data);

            assertEquals(11066, task._countsByClass[0]);
            assertEquals(13355, task._countsByClass[1]);

            // data[data.Distance <= 1000].IsDepDelayed.value_counts()
            DataFeaturesLimits limit0FeaturesLimits = getFeaturesLimitsForConditions(data,
                    wholeDataLimits.updateMax(7, 1000));
            task = new GetClassCountsMRTask(limit0FeaturesLimits.toDoubles(), 2);
            task.doAll(data);

            assertEquals(8992, task._countsByClass[0]);
            assertEquals(10163, task._countsByClass[1]);

            // data[(data.Distance <= 1000) & (data.fDayOfMonth > 12)].IsDepDelayed.value_counts()
            DataFeaturesLimits limit1FeaturesLimits = getFeaturesLimitsForConditions(data,
                    // test updateMaskExcluded
                    limit0FeaturesLimits.updateMaskExcluded(2, 
                            // first 3 alphabetically - f1, f10, f11 (updateMaskExcluded reverses the mask if no other limitations)
                            new boolean[]{false, false, false, true, true, true, true, true, true, true, true, true, 
                                    true, true, true, true, true, true, true, true, true, true, true, true, true, 
                                    true, true, true, true, true, true}));
            task = new GetClassCountsMRTask(limit1FeaturesLimits.toDoubles(), 2);
            task.doAll(data);

            assertEquals(802, task._countsByClass[0]);
            assertEquals(992, task._countsByClass[1]);

        } finally {
            Scope.exit();
        }

    }
}
