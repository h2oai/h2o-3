package hex;

import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import org.junit.Test;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class FeatureInteractionsTest {

    public SharedTreeGraph createSharedTreeGraph() {
        SharedTreeGraph sharedTreeGraph = new SharedTreeGraph();
        SharedTreeSubgraph sharedTreeSubgraph = sharedTreeGraph.makeSubgraph("A");
        SharedTreeNode node_0 = sharedTreeSubgraph.makeRootNode();
        node_0.setInclusiveNa(true);
        node_0.setWeight(10);
        node_0.setGain(1);
        node_0.setColName("AA");
        node_0.setSplitValue(0);
        SharedTreeNode node_1L = sharedTreeSubgraph.makeLeftChildNode(node_0);
        node_1L.setWeight(5);
        node_1L.setGain(1);
        node_1L.setColName("BB");
        node_1L.setSplitValue(0);
        SharedTreeNode node_1R = sharedTreeSubgraph.makeRightChildNode(node_0);
        node_1R.setWeight(5);
        node_1R.setGain(1);
        node_1R.setColName("CC");
        node_1R.setSplitValue(0);
        SharedTreeNode node_2LL = sharedTreeSubgraph.makeLeftChildNode(node_1L);
        node_2LL.setWeight(2);
        node_2LL.setGain(1);
        node_2LL.setPredValue(1000.000f);
        SharedTreeNode node_2LR = sharedTreeSubgraph.makeRightChildNode(node_1L);
        node_2LR.setWeight(3);
        node_2LR.setGain(1);
        node_2LR.setPredValue(1100.000f);
        SharedTreeNode node_2RL = sharedTreeSubgraph.makeLeftChildNode(node_1R);
        node_2RL.setWeight(2);
        node_2RL.setGain(1);
        node_2RL.setPredValue(1110.0000f);
        SharedTreeNode node_2RR = sharedTreeSubgraph.makeRightChildNode(node_1R);
        node_2RR.setWeight(3);
        node_2RR.setSquaredError(1);
        node_2RR.setGain(1);
        node_2RR.setPredValue(1111.000f);

        sharedTreeSubgraph = sharedTreeGraph.makeSubgraph("B");
        node_0 = sharedTreeSubgraph.makeRootNode();
        node_0.setInclusiveNa(true);
        node_0.setWeight(10);
        node_0.setGain(1);
        node_0.setColName("AA");
        node_0.setSplitValue(1);
        node_1L = sharedTreeSubgraph.makeLeftChildNode(node_0);
        node_1L.setWeight(5);
        node_1L.setGain(1);
        node_1L.setColName("BB");
        node_1L.setSplitValue(1);
        node_1R = sharedTreeSubgraph.makeRightChildNode(node_0);
        node_1R.setWeight(5);
        node_1R.setGain(1);
        node_1R.setColName("CC");
        node_1R.setSplitValue(1);
        node_2LL = sharedTreeSubgraph.makeLeftChildNode(node_1L);
        node_2LL.setWeight(2);
        node_2LL.setGain(1);
        node_2LL.setPredValue(1000.000f);
        node_2LR = sharedTreeSubgraph.makeRightChildNode(node_1L);
        node_2LR.setWeight(3);
        node_2LR.setGain(1);
        node_2LR.setPredValue(1100.000f);
        node_2RL = sharedTreeSubgraph.makeLeftChildNode(node_1R);
        node_2RL.setWeight(2);
        node_2RL.setGain(1);
        node_2RL.setPredValue(1110.000f);
        node_2RR = sharedTreeSubgraph.makeRightChildNode(node_1R);
        node_2RR.setWeight(3);
        node_2RR.setGain(1);
        node_2RR.setPredValue(1111.000f);
        
        return sharedTreeGraph;
    }

    @Test
    public void testCollectFeatureInteractions() {
        FeatureInteractions featureInteractions1 = new FeatureInteractions();
        List<SharedTreeNode> interactionPath1 = new ArrayList<>();
        Set<String> memo1 = new HashSet<>();
        SharedTreeGraph sharedTreeGraph = createSharedTreeGraph();

        FeatureInteractions.collectFeatureInteractions(sharedTreeGraph.subgraphArray.get(0).rootNode,
                interactionPath1, 0, 0 ,1,0,0, featureInteractions1, memo1,
                10,10,-1,0, false);

        assertEquals(featureInteractions1.entrySet().size(), 5);

        double delta = 0.0001;
        FeatureInteraction featureInteraction = featureInteractions1.get("AA");
        assertEquals(featureInteraction.depth, 0);
        assertEquals(featureInteraction.gain, 1.0, delta);
        assertEquals(featureInteraction.cover, 10.0, delta);
        assertEquals(featureInteraction.fScore, 1.0, delta);
        assertEquals(featureInteraction.fScoreWeighted, 1.0, delta);
        assertEquals(featureInteraction.averageFScoreWeighted, 1.0, delta);
        assertEquals(featureInteraction.averageGain, 1.0, delta);
        assertEquals(featureInteraction.expectedGain, 1.0, delta);
        assertEquals(featureInteraction.treeIndex, 0.0, delta);
        assertEquals(featureInteraction.averageTreeDepth, 0.0, delta);
        assertEquals(featureInteraction.hasLeafStatistics, false);
        assertEquals(featureInteraction.splitValueHistogram.entrySet().size(), 1);
        

        featureInteraction = featureInteractions1.get("AA|CC");
        assertEquals(featureInteraction.depth, 1);
        assertEquals(featureInteraction.gain, 2.0, delta);
        assertEquals(featureInteraction.cover, 6.0, delta);
        assertEquals(featureInteraction.fScore, 1.0, delta);
        assertEquals(featureInteraction.fScoreWeighted, 0.5, delta);
        assertEquals(featureInteraction.averageFScoreWeighted, 0.5, delta);
        assertEquals(featureInteraction.averageGain, 2.0, delta);
        assertEquals(featureInteraction.expectedGain, 1.0, delta);
        assertEquals(featureInteraction.treeIndex, 0.0, delta);
        assertEquals(featureInteraction.averageTreeDepth, 1.0, delta);
        assertEquals(featureInteraction.hasLeafStatistics, true);
        assertEquals(featureInteraction.sumLeafValuesLeft, 1110.0, delta);
        assertEquals(featureInteraction.sumLeafValuesRight, 1111.0, delta);
    }

    @Test
    public void testMergeWith() {
        FeatureInteractions featureInteractions1 = new FeatureInteractions(), featureInteractions2 = new FeatureInteractions();
        List<SharedTreeNode> interactionPath1 = new ArrayList<>(), interactionPath2 = new ArrayList<>();
        Set<String> memo1 = new HashSet<>(), memo2 = new HashSet<>();
        SharedTreeGraph sharedTreeGraph = createSharedTreeGraph();
        double delta = 0.0001;
        
        FeatureInteractions.collectFeatureInteractions(sharedTreeGraph.subgraphArray.get(0).rootNode,
                interactionPath1, 0, 0 ,1,0,0, featureInteractions1, memo1, 
                10,10,-1,0, false);
        FeatureInteractions.collectFeatureInteractions(sharedTreeGraph.subgraphArray.get(1).rootNode,
                interactionPath2, 0, 0 ,1,0,0, featureInteractions2, memo2,
                10,10,-1,1, false);
        
        featureInteractions1.mergeWith(featureInteractions2);

        assertEquals(featureInteractions1.entrySet().size(), 5);
        
        FeatureInteraction featureInteraction = featureInteractions1.get("AA");
        assertEquals(featureInteraction.depth, 0);
        assertEquals(featureInteraction.gain, 2.0, delta);
        assertEquals(featureInteraction.cover, 20.0, delta);
        assertEquals(featureInteraction.fScore, 2.0, delta);
        assertEquals(featureInteraction.fScoreWeighted, 2.0, delta);
        assertEquals(featureInteraction.averageFScoreWeighted, 1.0, delta);
        assertEquals(featureInteraction.averageGain, 1.0, delta);
        assertEquals(featureInteraction.expectedGain, 2.0, delta);
        assertEquals(featureInteraction.treeIndex, 1.0, delta);
        assertEquals(featureInteraction.averageTreeDepth, 0.0, delta);
        assertEquals(featureInteraction.hasLeafStatistics, false);
        assertEquals(featureInteraction.splitValueHistogram.entrySet().size(), 2);

        featureInteraction = featureInteractions1.get("AA|CC");
        assertEquals(featureInteraction.depth, 1);
        assertEquals(featureInteraction.gain, 4.0, delta);
        assertEquals(featureInteraction.cover, 12.0, delta);
        assertEquals(featureInteraction.fScore, 2.0, delta);
        assertEquals(featureInteraction.fScoreWeighted, 1.0, delta);
        assertEquals(featureInteraction.averageFScoreWeighted, 0.5, delta);
        assertEquals(featureInteraction.averageGain, 2.0, delta);
        assertEquals(featureInteraction.expectedGain, 2.0, delta);
        assertEquals(featureInteraction.treeIndex, 1.0, delta);
        assertEquals(featureInteraction.averageTreeDepth, 1.0, delta);
        assertEquals(featureInteraction.hasLeafStatistics, true);
        assertEquals(featureInteraction.sumLeafValuesLeft, 2220.0, delta);
        assertEquals(featureInteraction.sumLeafValuesRight, 2222.0, delta);
        
    }

    @Test
    public void testGetFeatureInteractionsTable() {
        FeatureInteractions featureInteractions1 = new FeatureInteractions();
        List<SharedTreeNode> interactionPath1 = new ArrayList<>();
        Set<String> memo1 = new HashSet<>();
        SharedTreeGraph sharedTreeGraph = createSharedTreeGraph();
        

        FeatureInteractions.collectFeatureInteractions(sharedTreeGraph.subgraphArray.get(0).rootNode,
                interactionPath1, 0, 0 ,1,0,0, featureInteractions1, memo1,
                10,10,-1,0, true);

        TwoDimTable[] featureInteractionTable = featureInteractions1.getAsTable();

        assertEquals(featureInteractionTable.length, 2);
        assertEquals(featureInteractionTable[0].getTableHeader(), "Interaction Depth 0");
        assertEquals(featureInteractionTable[0].getRowDim(), 3);
        assertEquals(featureInteractionTable[0].getColDim(), 16);
        List<String> vars = new ArrayList<>();
        vars.add((String) featureInteractionTable[0].get(0,0));
        vars.add((String) featureInteractionTable[0].get(1,0));
        vars.add((String) featureInteractionTable[0].get(2,0));
        List<String> expected = new ArrayList<>();
        expected.add("AA");
        expected.add("BB");
        expected.add("CC");
        assertTrue(vars.containsAll(expected));
        
        assertEquals(featureInteractionTable[1].getTableHeader(), "Interaction Depth 1");
        assertEquals(featureInteractionTable[1].getRowDim(), 2);
        assertEquals(featureInteractionTable[1].getColDim(), 16);
        vars = new ArrayList<>();
        vars.add((String) featureInteractionTable[1].get(0,0));
        vars.add((String) featureInteractionTable[1].get(1,0));
        expected = new ArrayList<>();
        expected.add("AA|BB");
        expected.add("AA|CC");
        assertTrue(vars.containsAll(expected));
    }

    @Test
    public void testNoInteractions() {
        FeatureInteractions featureInteractions = new FeatureInteractions();
        TwoDimTable[] table = featureInteractions.getAsTable();
        assert table == null;
    }
    
}
