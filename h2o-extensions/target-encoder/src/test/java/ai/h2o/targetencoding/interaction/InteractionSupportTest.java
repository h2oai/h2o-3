package ai.h2o.targetencoding.interaction;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static ai.h2o.targetencoding.interaction.InteractionSupport.addFeatureInteraction;
import static org.junit.Assert.*;
import static water.TestUtil.*;
import static water.TestUtil.assertVecEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class InteractionSupportTest {
    
    @Test
    public void test_addFeatureInteraction() {
        try {
            Scope.enter();
            Frame fr = new TestFrameBuilder()
                    .withColNames("cat1", "cat2", "cat3")
                    .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_CAT)
                    .withDataForCol(0, ar("a", "b",  "c", "a",  "b", "c", null, "a"))
                    .withDataForCol(1, ar("A", "B", null, "A",  "B", "A",  "B", "A"))
                    .withDataForCol(2, ar("0", "1",  "2", "3", null, "0",  "1", "3"))
                    .build();
            
            int unique2Combinations = 5;
            int interactions2_idx = InteractionSupport.addFeatureInteraction(fr, new String[] {"cat1", "cat2"});
            printOutFrameAsTable(fr);
            assertEquals(4, fr.numCols());
            assertArrayEquals(new String[] {"cat1", "cat2", "cat3", "cat1:cat2"}, fr.names());
            assertEquals("cat1:cat2", fr.names()[interactions2_idx]);
            Vec interactions2 = fr.vec(interactions2_idx);
            assertTrue(interactions2.isCategorical());
            assertEquals(unique2Combinations, interactions2.domain().length);
            assertArrayEquals(new String[] {"0", "2", "5", "7", "10"}, interactions2.domain());

            int unique3Combinations = (int)fr.numRows() - 1;  // combination "a", "A, "3" duplicated on purpose
            int interactions3_idx = InteractionSupport.addFeatureInteraction(fr, new String[] {"cat1", "cat2", "cat3"});
            printOutFrameAsTable(fr);
            assertEquals(5, fr.numCols());
            assertArrayEquals(new String[] {"cat1", "cat2", "cat3", "cat1:cat2", "cat1:cat2:cat3"}, fr.names());
            assertEquals("cat1:cat2:cat3", fr.names()[interactions3_idx]);
            Vec interactions3 = fr.vec(interactions3_idx);
            assertTrue(interactions3.isCategorical());
            assertEquals(unique3Combinations, interactions3.domain().length);
            assertArrayEquals(new String[] {"0", "2", "17", "19", "34", "36", "53"}, interactions3.domain());
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void test_addFeatureInteraction_with_known_domain_is_consistent() {
        try {
            Scope.enter();
            Frame fr = parse_test_file("./smalldata/testng/airlines_train.csv");
            Scope.track(fr);
            String[] interacting = new String[] {"Origin", "fYear", "fMonth"};
            int interactionColIdx = addFeatureInteraction(fr, interacting);
            Vec interactionTraining = fr.vec(interactionColIdx);  //named training, because that's how interactions are computed during training
            fr.remove(interactionColIdx);

            String[] interactionDomain = interactionTraining.domain();
            interactionColIdx = addFeatureInteraction(fr, interacting, interactionDomain);
            Vec interactionScoring = fr.vec(interactionColIdx); //named scoring, because that's how interactions are computed during scoring
            assertArrayEquals(interactionDomain, interactionScoring.domain());
            
            assertVecEquals(interactionTraining, interactionScoring, 0);
        } finally {
            Scope.exit();
        }
    }


}
