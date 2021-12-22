package hex.genmodel.algos.tree;

import org.junit.Test;

import static org.junit.Assert.*;

public class ContributionsPredictorTest {

    @Test
    public void testPubDev8195_workspaceAdjustsSize() {
        final int smallSize = 2;
        WorkspaceSizeReturningPredictor pSmall = new WorkspaceSizeReturningPredictor(smallSize);
        DummyPredictor dpSmall = new DummyPredictor(2, new String[] {"f1", "f2"}, pSmall);
        assertEquals(smallSize, (int) dpSmall.calculateContributions(null)[0]);

        final int largerSize = smallSize + 40;
        WorkspaceSizeReturningPredictor pLarge = new WorkspaceSizeReturningPredictor(largerSize);
        DummyPredictor dpLarge = new DummyPredictor(2, new String[] {"f1", "f2"}, pLarge);
        assertEquals(largerSize, (int) dpLarge.calculateContributions(null)[0]);
    }

    private static class DummyPredictor extends ContributionsPredictor<double[]> {
        public DummyPredictor(int ncontribs, String[] featureContributionNames, TreeSHAPPredictor<double[]> treeSHAPPredictor) {
            super(ncontribs, featureContributionNames, treeSHAPPredictor);
        }

        @Override
        protected double[] toInputRow(double[] input) {
            return input;
        }
    }

    private static class WorkspaceSizeReturningPredictor implements TreeSHAPPredictor<double[]> {
        private final int _givenWorkspaceSize;

        public WorkspaceSizeReturningPredictor(int givenWorkspaceSize) {
            _givenWorkspaceSize = givenWorkspaceSize;
        }

        @Override
        public float[] calculateContributions(double[] feat, float[] out_contribs) {
            fail("Method calculateContributions without workspace argument shouldn't be called.");
            return null;
        }

        @Override
        public float[] calculateContributions(double[] feat, float[] out_contribs, int condition, int condition_feature, Workspace workspace) {
            out_contribs[0] = workspace.getSize();
            return out_contribs;
        }

        @Override
        public Workspace makeWorkspace() {
            return new Workspace() {
                @Override
                public int getSize() {
                    return _givenWorkspaceSize;
                }
            };
        }

        @Override
        public int getWorkspaceSize() {
            return _givenWorkspaceSize;
        }
    }
    
}
