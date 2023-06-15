//package hex.tree.sdt;
//
//import hex.tree.sdt.mrtasks.GetClassCountsMRTask;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import water.Scope;
//import water.TestUtil;
//import water.fvec.Frame;
//import water.fvec.TestFrameBuilder;
//import water.fvec.Vec;
//import water.runner.CloudSize;
//import water.runner.H2ORunner;
//
//
//import static hex.tree.sdt.binning.Histogram.getFeaturesLimitsForConditions;
//import static org.junit.Assert.*;
//
//@CloudSize(1)
//@RunWith(H2ORunner.class)
//public class ClassCountTest extends TestUtil {
//
//    @Test
//    public void testBinningBasicData() {
//        try {
//            Scope.enter();
//            Frame basicData = new TestFrameBuilder()
//                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
//                    .withDataForCol(0, ard(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0))
//                    .withDataForCol(1, ard(1, 1, 0, 1, 0, 1, 0, 1, 1, 1))
//                    .withDataForCol(2, ard(1.88, 1.5, 0.88, 1.5, 0.88, 1.5, 0.88, 1.5, 8.0, 9.0))
//
//                    .withColNames("First", "Prediction", "Second")
//                    .build();
//
//            Vec response = basicData.remove("Prediction");
//            basicData.add("Prediction", response);
//
//            Scope.track_generic(basicData);
//
//            DataFeaturesLimits wholeDataLimits = SDT.getInitialFeaturesLimits(basicData);
//
//            GetClassCountsMRTask task = new GetClassCountsMRTask(wholeDataLimits.toDoubles());
//            task.doAll(basicData);
//            
//            assertEquals(3, task._count0);
//            assertEquals(7, task._count1);
//
//            DataFeaturesLimits limit0FeaturesLimits = getFeaturesLimitsForConditions(basicData, 
//                    wholeDataLimits.updateMax(0, 5.0));
//            task = new GetClassCountsMRTask(limit0FeaturesLimits.toDoubles());
//            task.doAll(basicData);
//
//            assertEquals(2, task._count0);
//            assertEquals(4, task._count1);
//
//            DataFeaturesLimits limit1FeaturesLimits = getFeaturesLimitsForConditions(basicData,
//                    limit0FeaturesLimits.updateMin(1, 1.0));
//            task = new GetClassCountsMRTask(limit1FeaturesLimits.toDoubles());
//            task.doAll(basicData);
//
//            assertEquals(0, task._count0);
//            assertEquals(4, task._count1);
//            
//        } finally {
//            Scope.exit();
//        }
//
//    }
//}
