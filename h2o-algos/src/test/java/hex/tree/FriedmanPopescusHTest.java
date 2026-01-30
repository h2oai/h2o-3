package hex.tree;


import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import water.Key;
import water.junit.rules.ScopeTracker;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FriedmanPopescusHTest extends TestUtil {

    @Rule
    public ScopeTracker scope = new ScopeTracker();

    @BeforeClass
    public static void stall() { stall_till_cloudsize(1); }
    
    String[] NAMES_IRIS = new String[] {"sepal_len", "sepal_wid", "petal_len", "petal_wid"};

    @Mock
    private GBMModel mockModel, mockModel2;

    public SharedTreeGraph createSharedTreeGraphForTest() {
        SharedTreeGraph sharedTreeGraph = new SharedTreeGraph();
        SharedTreeSubgraph sharedTreeSubgraph = sharedTreeGraph.makeSubgraph("A");
        SharedTreeNode node_0 = sharedTreeSubgraph.makeRootNode();
        // 0
        node_0.setInclusiveNa(true);
        node_0.setWeight(1);
        node_0.setGain(1);
        node_0.setColName("feature2");
        node_0.setCol(2, "feature2");
        node_0.setSplitValue(0.54549f);
        // 1
        SharedTreeNode node_1L = sharedTreeSubgraph.makeLeftChildNode(node_0);
        node_1L.setWeight((float)49/90);
        node_1L.setGain(1);
        node_1L.setColName("feature2");
        node_1L.setCol(2, "feature2");
        node_1L.setSplitValue(0.19288f);
        // 8
        SharedTreeNode node_1R = sharedTreeSubgraph.makeRightChildNode(node_0);
        node_1R.setWeight((float)41/90);
        node_1R.setGain(1);
        node_1R.setColName("feature1");
        node_1R.setCol(1, "feature1");
        node_1R.setSplitValue(0.87505f);
        //2
        SharedTreeNode node_2LL = sharedTreeSubgraph.makeLeftChildNode(node_1L);
        node_2LL.setWeight((float)14/90);
        node_2LL.setGain(1);
        node_2LL.setColName("feature0");
        node_2LL.setCol(0, "feature0");
        node_2LL.setSplitValue(0.38097f);
        // 5
        SharedTreeNode node_2LR = sharedTreeSubgraph.makeRightChildNode(node_1L);
        node_2LR.setWeight((float)35/90);
        node_2LR.setGain(1);
        node_2LR.setColName("feature1");
        node_2LR.setCol(1, "feature1");
        node_2LR.setSplitValue(0.86227f);
        // 3
        SharedTreeNode node_3LLL = sharedTreeSubgraph.makeLeftChildNode(node_2LL);
        node_3LLL.setPredValue(-0.67412452f);
        node_3LLL.setWeight((float)8/90);
        // 4
        SharedTreeNode node_3LLR = sharedTreeSubgraph.makeRightChildNode(node_2LL);
        node_3LLR.setPredValue(-0.38505089f);
        node_3LLR.setWeight((float)6/90);
        //6
        SharedTreeNode node_3LRL = sharedTreeSubgraph.makeLeftChildNode(node_2LR);
        node_3LRL.setPredValue(-0.21888788f);
        node_3LRL.setWeight((float)29/90);
        //7
        SharedTreeNode node_3LRR = sharedTreeSubgraph.makeRightChildNode(node_2LR);
        node_3LRR.setPredValue(0.2213862f);
        node_3LRR.setWeight((float)6/90);
        //9
        SharedTreeNode node_2RL = sharedTreeSubgraph.makeLeftChildNode(node_1R); 
        node_2RL.setWeight((float)34/90);
        node_2RL.setGain(1);
        node_2RL.setColName("feature1");
        node_2RL.setCol(1, "feature1");
        node_2RL.setSplitValue(0.19438f);
        //12
        SharedTreeNode node_2RR = sharedTreeSubgraph.makeRightChildNode(node_1R);
        node_2RR.setWeight((float)7/90);
        node_2RR.setGain(1);
        node_2RR.setColName("feature0");
        node_2RR.setCol(0, "feature0");
        node_2RR.setSplitValue(0.93068f);
        //10
        SharedTreeNode node_3RLL = sharedTreeSubgraph.makeLeftChildNode(node_2RL);
        node_3RLL.setWeight((float)14/90);
        node_3RLL.setPredValue(0.04768911f);
        //11
        SharedTreeNode node_3RLR = sharedTreeSubgraph.makeRightChildNode(node_2RL);
        node_3RLR.setWeight((float)20/90);
        node_3RLR.setPredValue(0.32979722f);
        //13
        SharedTreeNode node_3RRL = sharedTreeSubgraph.makeLeftChildNode(node_2RR);
        node_3RRL.setWeight((float)6/90);
        node_3RRL.setPredValue(0.68742419f);
        //14
        SharedTreeNode node_3RRR = sharedTreeSubgraph.makeRightChildNode(node_2RR);
        node_3RRR.setWeight((float)1/90);
        node_3RRR.setPredValue(1.33459568f);

        return sharedTreeGraph;
    }

    private SharedTreeSubgraph createStage0k0Tree(SharedTreeGraph graph) {
        SharedTreeSubgraph sharedTreeSubgraph = graph.makeSubgraph("stage0k0");
        // 0
        SharedTreeNode node0 = sharedTreeSubgraph.makeRootNode();
        node0.setInclusiveNa(true);
        node0.setWeight(1.0f);
        node0.setGain(1);
        node0.setCol(3, NAMES_IRIS[3]);
        node0.setSplitValue(0.800000011920929f);
        // 1
        SharedTreeNode node1 = sharedTreeSubgraph.makeLeftChildNode(node0);
        node1.setWeight((float)50/150);
        node1.setGain(1);
        node1.setPredValue(2.0f);
        // 2
        SharedTreeNode node2 = sharedTreeSubgraph.makeRightChildNode(node0);
        node2.setCol(3, NAMES_IRIS[3]);
        node2.setSplitValue(1.550000011920929f);
        node2.setWeight((float)100/150);
        node2.setGain(1);
        node2.setInclusiveNa(true);
        // 3
        SharedTreeNode node3 = sharedTreeSubgraph.makeLeftChildNode(node2);
        node3.setPredValue(-0.9999999999999997f);
        node3.setWeight((float)48/150);
        node3.setGain(1);
        // 4
        SharedTreeNode node4 = sharedTreeSubgraph.makeRightChildNode(node2);
        node4.setCol(2, NAMES_IRIS[2]);
        node4.setSplitValue(6.5f);
        node4.setGain(1);
        node4.setWeight((float)52/150);
        node4.setInclusiveNa(true);
        // 5
        SharedTreeNode node5 = sharedTreeSubgraph.makeLeftChildNode(node4);
        node5.setPredValue(-0.9999999999999997f);
        node5.setWeight((float)48/150);
        node5.setGain(1);
        // 6
        SharedTreeNode node6 = sharedTreeSubgraph.makeRightChildNode(node4);
        node6.setCol(2, NAMES_IRIS[2]);
        node6.setSplitValue(6.799999952316284f);
        node6.setGain(1);
        node6.setWeight((float)4/150);
        node6.setInclusiveNa(true);
        // 7
        SharedTreeNode node7 = sharedTreeSubgraph.makeLeftChildNode(node6);
        node7.setPredValue(-0.9999999999999999f);
        node7.setWeight((float)3/150);
        node7.setGain(1);
        // 8
        SharedTreeNode node8 = sharedTreeSubgraph.makeRightChildNode(node6);
        node8.setPredValue(-0.9999999999999999f);
        node8.setWeight((float)1/150);
        node8.setGain(1);
        
        return sharedTreeSubgraph;
    }

    private SharedTreeSubgraph createStage0k1Tree(SharedTreeGraph graph) {
        SharedTreeSubgraph sharedTreeSubgraph = graph.makeSubgraph("stage0k1");
        
        // 0
        SharedTreeNode node0 = sharedTreeSubgraph.makeRootNode();
        node0.setInclusiveNa(true);
        node0.setWeight(1);
        node0.setGain(1);
        node0.setCol(3, NAMES_IRIS[3]);
        node0.setSplitValue(0.800000011920929f);
        // 1
        SharedTreeNode node1 = sharedTreeSubgraph.makeLeftChildNode(node0);
        node1.setPredValue(-0.9999999999999999f);
        node1.setWeight((float)50/150);
        node1.setGain(1);
        // 2
        SharedTreeNode node2 = sharedTreeSubgraph.makeRightChildNode(node0);
        node2.setInclusiveNa(true);
        node2.setWeight((float)100/150);
        node2.setGain(1);
        node2.setCol(3, NAMES_IRIS[3]);
        node2.setSplitValue(1.75f);
        // 3
        SharedTreeNode node3 = sharedTreeSubgraph.makeLeftChildNode(node2);
        node3.setInclusiveNa(true);
        node3.setWeight((float)54/150);
        node3.setGain(1);
        node3.setCol(2, NAMES_IRIS[2]);
        node3.setSplitValue(4.950000047683716f);
        // 4
        SharedTreeNode node4 = sharedTreeSubgraph.makeLeftChildNode(node3);
        node4.setInclusiveNa(true);
        node4.setWeight((float)48/150);
        node4.setGain(1);
        node4.setCol(3, NAMES_IRIS[3]);
        node4.setSplitValue(1.6500000357627869f);
        // 5
        SharedTreeNode node5 = sharedTreeSubgraph.makeLeftChildNode(node4);
        node5.setGain(1);
        node5.setWeight((float)47/150);
        node5.setPredValue(2.0000000000000018f);
        // 6
        SharedTreeNode node6 = sharedTreeSubgraph.makeRightChildNode(node4);
        node6.setGain(1);
        node6.setWeight((float)1/150);
        node6.setPredValue(-0.9999999999999999f);
        // 7
        SharedTreeNode node7 = sharedTreeSubgraph.makeRightChildNode(node3);
        node7.setInclusiveNa(false);
        node7.setWeight((float)6/150);
        node7.setGain(1);
        node7.setCol(3, NAMES_IRIS[3]);
        node7.setSplitValue(1.550000011920929F);
        // 8
        SharedTreeNode node8 = sharedTreeSubgraph.makeLeftChildNode(node7);
        node8.setGain(1);
        node8.setWeight((float)3/150);
        node8.setPredValue(-0.9999999999999999f);
        // 9
        SharedTreeNode node9 = sharedTreeSubgraph.makeRightChildNode(node7);
        node9.setGain(1);
        node9.setWeight((float)3/150);
        node9.setPredValue( 1.0000000000000002f);
        // 10
        SharedTreeNode node10 = sharedTreeSubgraph.makeRightChildNode(node2);
        node10.setInclusiveNa(false);
        node10.setWeight((float)46/150);
        node10.setGain(1);
        node10.setCol(2, NAMES_IRIS[2]);
        node10.setSplitValue(4.8500001430511475f);
        // 11
        SharedTreeNode node11 = sharedTreeSubgraph.makeLeftChildNode(node10);
        node11.setInclusiveNa(false);
        node11.setWeight((float)3/150);
        node11.setGain(1);
        node11.setCol(1, NAMES_IRIS[1]);
        node11.setSplitValue(3.100000023841858f);
        // 12
        SharedTreeNode node12 = sharedTreeSubgraph.makeLeftChildNode(node11);
        node12.setGain(1);
        node12.setWeight((float)2/150);
        node12.setPredValue(-0.9999999999999999f);
        // 13
        SharedTreeNode node13 = sharedTreeSubgraph.makeRightChildNode(node11);
        node13.setGain(1);
        node13.setWeight((float)1/150);
        node13.setPredValue(2.0000000000000004f);
        // 14
        SharedTreeNode node14 = sharedTreeSubgraph.makeRightChildNode(node10);
        node14.setGain(1);
        node14.setWeight((float)43/150);
        node14.setPredValue(-1.0000000000000002f);

        return sharedTreeSubgraph;
    }

    private SharedTreeSubgraph createStage0k2Tree(SharedTreeGraph graph) {
        SharedTreeSubgraph sharedTreeSubgraph = graph.makeSubgraph("stage0k2");

        // 0
        SharedTreeNode node0 = sharedTreeSubgraph.makeRootNode();
        node0.setInclusiveNa(true);
        node0.setWeight(1);
        node0.setGain(1);
        node0.setCol(3, NAMES_IRIS[3]);
        node0.setSplitValue(1.75f);
        // 1
        SharedTreeNode node1 = sharedTreeSubgraph.makeLeftChildNode(node0);
        node1.setInclusiveNa(true);
        node1.setGain(1);
        node1.setWeight((float)104/150);
        node1.setSplitValue(4.950000047683716f);
        node1.setCol(2, NAMES_IRIS[2]);
        // 2
        SharedTreeNode node2 = sharedTreeSubgraph.makeLeftChildNode(node1);
        node2.setInclusiveNa(true);
        node2.setGain(1);
        node2.setWeight((float)98/150);
        node2.setSplitValue(1.6500000357627869f);
        node2.setCol(3, NAMES_IRIS[3]);
        // 3
        SharedTreeNode node3 = sharedTreeSubgraph.makeLeftChildNode(node2);
        node3.setInclusiveNa(true);
        node3.setGain(1);
        node3.setWeight((float)97/150);
        node3.setSplitValue(3.049999952316284f);
        node3.setCol(1, NAMES_IRIS[1]);
        // 4
        SharedTreeNode node4 = sharedTreeSubgraph.makeLeftChildNode(node3);
        node4.setGain(1);
        node4.setWeight((float)48/150);
        node4.setPredValue(-0.9999999999999997f);
        // 5
        SharedTreeNode node5 = sharedTreeSubgraph.makeRightChildNode(node3);
        node5.setGain(1);
        node5.setWeight((float)49/150);
        node5.setPredValue(-0.9999999999999999f);
        // 6
        SharedTreeNode node6 = sharedTreeSubgraph.makeRightChildNode(node2);
        node6.setGain(1);
        node6.setWeight((float)1/150);
        node6.setPredValue(2.0000000000000004f);
        // 7 
        SharedTreeNode node7 = sharedTreeSubgraph.makeRightChildNode(node1);
        node7.setInclusiveNa(false);
        node7.setGain(1);
        node7.setWeight((float)6/150);
        node7.setSplitValue(1.550000011920929f);
        node7.setCol(3, NAMES_IRIS[3]);
        // 8
        SharedTreeNode node8 = sharedTreeSubgraph.makeLeftChildNode(node7);
        node8.setInclusiveNa(false);
        node8.setGain(1);
        node8.setWeight((float)3/150);
        node8.setSplitValue(6.200000047683716f);
        node8.setCol(0, NAMES_IRIS[0]);
        // 9
        SharedTreeNode node9 = sharedTreeSubgraph.makeLeftChildNode(node8);
        node9.setGain(1);
        node9.setWeight((float)2/150);
        node9.setPredValue(2.0000000000000004f);
        // 10
        SharedTreeNode node10 = sharedTreeSubgraph.makeRightChildNode(node8);
        node10.setGain(1);
        node10.setWeight((float)1/150);
        node10.setPredValue(2.0000000000000004f);
        // 11
        SharedTreeNode node11 = sharedTreeSubgraph.makeRightChildNode(node7);
        node11.setGain(1);
        node11.setWeight((float)3/150);
        node11.setInclusiveNa(false);
        node11.setSplitValue(6.949999809265137f);
        node11.setCol(0, NAMES_IRIS[0]);
        // 12
        SharedTreeNode node12 = sharedTreeSubgraph.makeLeftChildNode(node11);
        node12.setGain(1);
        node12.setWeight((float)2/150);
        node12.setPredValue(-0.9999999999999999f);
        // 13
        SharedTreeNode node13 = sharedTreeSubgraph.makeRightChildNode(node11);
        node13.setGain(1);
        node13.setWeight((float)1/150);
        node13.setPredValue(2.0000000000000004f);
        // 14
        SharedTreeNode node14 = sharedTreeSubgraph.makeRightChildNode(node0);
        node14.setGain(1);
        node14.setWeight((float)46/150);
        node14.setInclusiveNa(false);
        node14.setSplitValue(4.8500001430511475f);
        node14.setCol(2, NAMES_IRIS[2]);
        // 15
        SharedTreeNode node15 = sharedTreeSubgraph.makeLeftChildNode(node14);
        node15.setGain(1);
        node15.setWeight((float)3/150);
        node15.setInclusiveNa(false);
        node15.setSplitValue(5.950000047683716f);
        node15.setCol(0, NAMES_IRIS[0]);
        // 16
        SharedTreeNode node16 = sharedTreeSubgraph.makeLeftChildNode(node15);
        node16.setGain(1);
        node16.setWeight((float)1/150);
        node16.setPredValue(-0.9999999999999999f);
        // 17
        SharedTreeNode node17 = sharedTreeSubgraph.makeRightChildNode(node15);
        node17.setGain(1);
        node17.setWeight((float)2/150);
        node17.setPredValue(2.0000000000000004f);
        // 18
        SharedTreeNode node18 = sharedTreeSubgraph.makeRightChildNode(node14);
        node18.setGain(1);
        node18.setWeight((float)43/150);
        node18.setPredValue(2.000000000000001f);

        return sharedTreeSubgraph;
    }

    private SharedTreeSubgraph createStage1k0Tree(SharedTreeGraph graph) {
        SharedTreeSubgraph sharedTreeSubgraph = graph.makeSubgraph("stage1k0");

        // 0
        SharedTreeNode node0 = sharedTreeSubgraph.makeRootNode();
        node0.setInclusiveNa(true);
        node0.setWeight(1);
        node0.setGain(1);
        node0.setCol(2, NAMES_IRIS[2]);
        node0.setSplitValue(2.449999988079071f);
        // 1
        SharedTreeNode node1 = sharedTreeSubgraph.makeLeftChildNode(node0);
        node1.setInclusiveNa(true);
        node1.setWeight((float)50/150);
        node1.setGain(1);
        node1.setCol(0, NAMES_IRIS[0]);
        node1.setSplitValue(4.75f);
        // 2
        SharedTreeNode node2 = sharedTreeSubgraph.makeLeftChildNode(node1);
        node2.setWeight((float)11/150);
        node2.setGain(1);
        node2.setPredValue(0.733049424490485f);
        // 3
        SharedTreeNode node3 = sharedTreeSubgraph.makeRightChildNode(node1);
        node3.setInclusiveNa(false);
        node3.setWeight((float)39/150);
        node3.setGain(1);
        node3.setCol(1, NAMES_IRIS[1]);
        node3.setSplitValue(3.350000023841858f);
        // 4
        SharedTreeNode node4 = sharedTreeSubgraph.makeLeftChildNode(node3);
        node4.setWeight((float)11/150);
        node4.setGain(1);
        node4.setPredValue(0.733049424490485f);
        // 5
        SharedTreeNode node5 = sharedTreeSubgraph.makeRightChildNode(node3);
        node5.setWeight((float)28/150);
        node5.setInclusiveNa(false);
        node5.setGain(1);
        node5.setCol(1, NAMES_IRIS[1]);
        node5.setSplitValue(3.649999976158142f);
        // 6
        SharedTreeNode node6 = sharedTreeSubgraph.makeLeftChildNode(node5);
        node6.setWeight((float)15/150);
        node6.setGain(1);
        node6.setPredValue(0.7330494244904849f);
        // 7
        SharedTreeNode node7 = sharedTreeSubgraph.makeRightChildNode(node5);
        node7.setWeight((float)13/150);
        node7.setGain(1);
        node7.setPredValue(0.7330494244904849f);
        // 8
        SharedTreeNode node8 = sharedTreeSubgraph.makeRightChildNode(node0);
        node8.setWeight((float)100/150);
        node8.setInclusiveNa(false);
        node8.setGain(1);
        node8.setCol(2, NAMES_IRIS[2]);
        node8.setSplitValue(4.950000047683716f);
        // 9
        SharedTreeNode node9 = sharedTreeSubgraph.makeLeftChildNode(node8);
        node9.setWeight((float)54/150);
        node9.setInclusiveNa(false);
        node9.setGain(1);
        node9.setCol(0, NAMES_IRIS[0]);
        node9.setSplitValue(5.950000047683716f);
        // 10
        SharedTreeNode node10 = sharedTreeSubgraph.makeLeftChildNode(node9);
        node10.setWeight((float)28/150);
        node10.setInclusiveNa(false);
        node10.setGain(1);
        node10.setCol(0, NAMES_IRIS[0]);
        node10.setSplitValue(5.75f);
        // 11
        SharedTreeNode node11 = sharedTreeSubgraph.makeLeftChildNode(node10);
        node11.setWeight((float)23/150);
        node11.setGain(1);
        node11.setPredValue(-0.6982839154517115f);
        // 12
        SharedTreeNode node12 = sharedTreeSubgraph.makeRightChildNode(node10);
        node12.setWeight((float)5/150);
        node12.setGain(1);
        node12.setPredValue(-0.6982839154517111f);
        // 13
        SharedTreeNode node13 = sharedTreeSubgraph.makeRightChildNode(node9);
        node13.setWeight((float)26/150);
        node13.setGain(1);
        node13.setInclusiveNa(false);
        node13.setCol(1, NAMES_IRIS[1]);
        node13.setSplitValue(3.149999976158142f);
        // 14
        SharedTreeNode node14 = sharedTreeSubgraph.makeLeftChildNode(node13);
        node14.setWeight((float)22/150);
        node14.setGain(1);
        node14.setPredValue(-0.6982839154517112f);
        // 15
        SharedTreeNode node15 = sharedTreeSubgraph.makeRightChildNode(node13);
        node15.setWeight((float)4/150);
        node15.setGain(1);
        node15.setPredValue(-0.6982839154517112f);
        // 16
        SharedTreeNode node16 = sharedTreeSubgraph.makeRightChildNode(node8);
        node16.setWeight((float)46/150);
        node16.setGain(1);
        node16.setInclusiveNa(false);
        node16.setCol(3, NAMES_IRIS[3]);
        node16.setSplitValue(1.75f);
        // 17
        SharedTreeNode node17 = sharedTreeSubgraph.makeLeftChildNode(node16);
        node17.setWeight((float)6/150);
        node17.setGain(1);
        node17.setInclusiveNa(false);
        node17.setCol(3, NAMES_IRIS[3]);
        node17.setSplitValue(1.6500000357627869f);
        // 18
        SharedTreeNode node18 = sharedTreeSubgraph.makeLeftChildNode(node17);
        node18.setWeight((float)5/150);
        node18.setGain(1);
        node18.setPredValue(-0.7149208857359403f);
        // 19
        SharedTreeNode node19 = sharedTreeSubgraph.makeRightChildNode(node17);
        node19.setWeight((float)1/150);
        node19.setGain(1);
        node19.setPredValue(-0.7461352813480784f);
        // 20
        SharedTreeNode node20 = sharedTreeSubgraph.makeRightChildNode(node16);
        node20.setWeight((float)40/150);
        node20.setGain(1);
        node20.setInclusiveNa(false);
        node20.setCol(0, NAMES_IRIS[0]);
        node20.setSplitValue(6.75f);
        // 21
        SharedTreeNode node21 = sharedTreeSubgraph.makeLeftChildNode(node20);
        node21.setWeight((float)24/150);
        node21.setGain(1);
        node21.setPredValue(-0.698283915451711f);
        // 22
        SharedTreeNode node22 = sharedTreeSubgraph.makeRightChildNode(node20);
        node22.setWeight((float)16/150);
        node22.setGain(1);
        node22.setPredValue(-0.6982839154517111f);
        
        return sharedTreeSubgraph;
    }

    private SharedTreeSubgraph createStage1k1Tree(SharedTreeGraph graph) {
        SharedTreeSubgraph sharedTreeSubgraph = graph.makeSubgraph("stage1k1");

        // 0
        SharedTreeNode node0 = sharedTreeSubgraph.makeRootNode();
        node0.setInclusiveNa(true);
        node0.setWeight(1);
        node0.setGain(1);
        node0.setCol(3, NAMES_IRIS[3]);
        node0.setSplitValue(0.800000011920929f);
        // 1
        SharedTreeNode node1 = sharedTreeSubgraph.makeLeftChildNode(node0);
        node1.setWeight((float)50/150);
        node1.setGain(1);
        node1.setPredValue(-0.698283915451711f);
        // 2
        SharedTreeNode node2 = sharedTreeSubgraph.makeRightChildNode(node0);
        node2.setInclusiveNa(false);
        node2.setWeight((float)100/150);
        node2.setGain(1);
        node2.setCol(3, NAMES_IRIS[3]);
        node2.setSplitValue(1.75f);
        // 3
        SharedTreeNode node3 = sharedTreeSubgraph.makeLeftChildNode(node2);
        node3.setInclusiveNa(false);
        node3.setWeight((float)54/150);
        node3.setGain(1);
        node3.setCol(0, NAMES_IRIS[0]);
        node3.setSplitValue(7.099999904632568f);
        // 4
        SharedTreeNode node4 = sharedTreeSubgraph.makeLeftChildNode(node3);
        node4.setInclusiveNa(false);
        node4.setWeight((float)53/150);
        node4.setGain(1);
        node4.setCol(2, NAMES_IRIS[2]);
        node4.setSplitValue(5.349999904632568f);
        // 5
        SharedTreeNode node5 = sharedTreeSubgraph.makeLeftChildNode(node4);
        node5.setWeight((float)52/150);
        node5.setGain(1);
        node5.setPredValue(0.6990574969530611f);
        // 6
        SharedTreeNode node6 = sharedTreeSubgraph.makeRightChildNode(node4);
        node6.setWeight((float)1/150);
        node6.setGain(1);
        node6.setPredValue(-0.6982839154517111f);
        // 7
        SharedTreeNode node7 = sharedTreeSubgraph.makeRightChildNode(node3);
        node7.setWeight((float)1/150);
        node7.setGain(1);
        node7.setPredValue(-0.9002882916332074f);
        // 8 
        SharedTreeNode node8 = sharedTreeSubgraph.makeRightChildNode(node2);
        node8.setWeight((float)46/150);
        node8.setGain(1);
        node8.setInclusiveNa(false);
        node8.setSplitValue(4.8500001430511475f);
        node8.setCol(2, NAMES_IRIS[2]);
        // 9
        SharedTreeNode node9 = sharedTreeSubgraph.makeLeftChildNode(node8);
        node9.setWeight((float)3/150);
        node9.setGain(1);
        node9.setInclusiveNa(false);
        node9.setSplitValue(5.950000047683716f);
        node9.setCol(0, NAMES_IRIS[0]);
        // 10
        SharedTreeNode node10 = sharedTreeSubgraph.makeLeftChildNode(node9);
        node10.setWeight((float)1/150);
        node10.setGain(1);
        node10.setPredValue(0.7330494244904852f);
        // 11
        SharedTreeNode node11 = sharedTreeSubgraph.makeRightChildNode(node9);
        node11.setWeight((float)2/150);
        node11.setGain(1);
        node11.setPredValue(-0.6982839154517111f);
        // 12
        SharedTreeNode node12 = sharedTreeSubgraph.makeRightChildNode(node8);
        node12.setWeight((float)43/150);
        node12.setGain(1);
        node12.setInclusiveNa(false);
        node12.setSplitValue(6.599999904632568f);
        node12.setCol(0, NAMES_IRIS[0]);
        // 13
        SharedTreeNode node13 = sharedTreeSubgraph.makeLeftChildNode(node12);
        node13.setWeight((float)22/150);
        node13.setGain(1);
        node13.setPredValue(-0.698283915451711f);
        // 14
        SharedTreeNode node14 = sharedTreeSubgraph.makeRightChildNode(node12);
        node14.setWeight((float)21/150);
        node14.setGain(1);
        node14.setPredValue(-0.6982839154517111f);

        return sharedTreeSubgraph;
    }

    private SharedTreeSubgraph createStage1k2Tree(SharedTreeGraph graph) {
        SharedTreeSubgraph sharedTreeSubgraph = graph.makeSubgraph("stage1k2");

        // 0
        SharedTreeNode node0 = sharedTreeSubgraph.makeRootNode();
        node0.setInclusiveNa(true);
        node0.setWeight(1);
        node0.setGain(1);
        node0.setCol(2, NAMES_IRIS[2]);
        node0.setSplitValue(4.75f);
        // 1
        SharedTreeNode node1 = sharedTreeSubgraph.makeLeftChildNode(node0);
        node1.setInclusiveNa(true);
        node1.setWeight((float)95/150);
        node1.setGain(1);
        node1.setCol(3, NAMES_IRIS[3]);
        node1.setSplitValue(1.6500000357627869f);
        // 2
        SharedTreeNode node2 = sharedTreeSubgraph.makeLeftChildNode(node1);
        node2.setWeight((float)94/150);
        node2.setGain(1);
        node2.setPredValue(-0.6982839154517116f);
        // 3
        SharedTreeNode node3 = sharedTreeSubgraph.makeRightChildNode(node1);
        node3.setWeight((float)1/150);
        node3.setGain(1);
        node3.setPredValue(0.7330494244904853f);
        // 4
        SharedTreeNode node4 = sharedTreeSubgraph.makeRightChildNode(node0);
        node4.setWeight((float)55/150);
        node4.setInclusiveNa(false);
        node4.setGain(1);
        node4.setCol(2, NAMES_IRIS[2]);
        node4.setSplitValue(5.1499998569488525f);
        // 5
        SharedTreeNode node5 = sharedTreeSubgraph.makeLeftChildNode(node4);
        node5.setWeight((float)21/150);
        node5.setInclusiveNa(true);
        node5.setGain(1);
        node5.setCol(3, NAMES_IRIS[3]);
        node5.setSplitValue(1.75f);
        // 6
        SharedTreeNode node6 = sharedTreeSubgraph.makeLeftChildNode(node5);
        node6.setWeight((float)7/150);
        node6.setInclusiveNa(true);
        node6.setGain(1);
        node6.setCol(3, NAMES_IRIS[3]);
        node6.setSplitValue(1.550000011920929f);
        // 7
        SharedTreeNode node7 = sharedTreeSubgraph.makeLeftChildNode(node6);
        node7.setWeight((float)5/150);
        node7.setGain(1);
        node7.setPredValue(0.10253355558077014f);
        // 8
        SharedTreeNode node8 = sharedTreeSubgraph.makeRightChildNode(node6);
        node8.setWeight((float)2/150);
        node8.setGain(1);
        node8.setPredValue(-0.7461352813480784f);
        // 9
        SharedTreeNode node9 = sharedTreeSubgraph.makeRightChildNode(node5);
        node9.setWeight((float)14/150);
        node9.setGain(1);
        node9.setInclusiveNa(true);
        node9.setCol(1, NAMES_IRIS[1]);
        node9.setSplitValue(3.149999976158142f);
        // 10
        SharedTreeNode node10 = sharedTreeSubgraph.makeLeftChildNode(node9);
        node10.setWeight((float)12/150);
        node10.setGain(1);
        node10.setPredValue(0.733049424490485f);
        // 11
        SharedTreeNode node11 = sharedTreeSubgraph.makeRightChildNode(node9);
        node11.setWeight((float)2/150);
        node11.setGain(1);
        node11.setPredValue(0.24036085407194846f);
        // 12
        SharedTreeNode node12 = sharedTreeSubgraph.makeRightChildNode(node4);
        node12.setWeight((float)34/150);
        node12.setGain(1);
        node12.setInclusiveNa(true);
        node12.setCol(3, NAMES_IRIS[3]);
        node12.setSplitValue(1.699999988079071f);
        // 13
        SharedTreeNode node13 = sharedTreeSubgraph.makeLeftChildNode(node12);
        node13.setWeight((float)2/150);
        node13.setGain(1);
        node13.setInclusiveNa(true);
        node13.setCol(1, NAMES_IRIS[1]);
        node13.setSplitValue(2.799999952316284f);
        // 14
        SharedTreeNode node14 = sharedTreeSubgraph.makeLeftChildNode(node13);
        node14.setWeight((float)1/150);
        node14.setGain(1);
        node14.setPredValue(0.7330494244904853f);
        // 15
        SharedTreeNode node15 = sharedTreeSubgraph.makeRightChildNode(node13);
        node15.setWeight((float)1/150);
        node15.setGain(1);
        node15.setPredValue(0.9451110063595374f);
        // 16
        SharedTreeNode node16 = sharedTreeSubgraph.makeRightChildNode(node12);
        node16.setWeight((float)32/150);
        node16.setGain(1);
        node16.setInclusiveNa(true);
        node16.setCol(0, NAMES_IRIS[0]);
        node16.setSplitValue(2.799999952316284f);
        // 17
        SharedTreeNode node17 = sharedTreeSubgraph.makeLeftChildNode(node16);
        node17.setWeight((float)12/150);
        node17.setGain(1);
        node17.setPredValue(0.733049424490485f);
        // 18
        SharedTreeNode node18 = sharedTreeSubgraph.makeRightChildNode(node16);
        node18.setWeight((float)20/150);
        node18.setGain(1);
        node18.setPredValue(0.733049424490485f);

        return sharedTreeSubgraph;
    }

    private SharedTreeSubgraph createStage2k0Tree(SharedTreeGraph graph) {
        SharedTreeSubgraph sharedTreeSubgraph = graph.makeSubgraph("stage2k0");

        // 0
        SharedTreeNode node0 = sharedTreeSubgraph.makeRootNode();
        node0.setInclusiveNa(true);
        node0.setWeight(1);
        node0.setGain(1);
        node0.setCol(2, NAMES_IRIS[2]);
        node0.setSplitValue(2.449999988079071f);
        // 1
        SharedTreeNode node1 = sharedTreeSubgraph.makeLeftChildNode(node0);
        node1.setWeight((float)50/150);
        node1.setGain(1);
        node1.setPredValue(0.6825314855918335f);
        // 2
        SharedTreeNode node2 = sharedTreeSubgraph.makeRightChildNode(node0);
        node2.setWeight((float)100/150);
        node2.setGain(1);
        node2.setInclusiveNa(true);
        node2.setCol(2, NAMES_IRIS[2]);
        node2.setSplitValue(5.1499998569488525f);
        // 3
        SharedTreeNode node3 = sharedTreeSubgraph.makeLeftChildNode(node2);
        node3.setWeight((float)66/150);
        node3.setGain(1);
        node3.setInclusiveNa(true);
        node3.setCol(2, NAMES_IRIS[2]);
        node3.setSplitValue(4.950000047683716f);
        // 4
        SharedTreeNode node4 = sharedTreeSubgraph.makeLeftChildNode(node3);
        node4.setWeight((float)54/150);
        node4.setGain(1);
        node4.setInclusiveNa(true);
        node4.setCol(3, NAMES_IRIS[3]);
        node4.setSplitValue(1.6500000357627869f);
        // 5
        SharedTreeNode node5 = sharedTreeSubgraph.makeLeftChildNode(node4);
        node5.setWeight((float)47/150);
        node5.setGain(1);
        node5.setPredValue(-0.6747660459630088f);
        // 6
        SharedTreeNode node6 = sharedTreeSubgraph.makeRightChildNode(node4);
        node6.setGain(1);
        node6.setWeight((float)7/150);
        node6.setPredValue(-0.6744482073055489f);
        // 7
        SharedTreeNode node7 = sharedTreeSubgraph.makeRightChildNode(node3);
        node7.setWeight((float)12/150);
        node7.setGain(1);
        node7.setInclusiveNa(true);
        node7.setCol(3, NAMES_IRIS[3]);
        node7.setSplitValue(1.75f);
        // 8
        SharedTreeNode node8 = sharedTreeSubgraph.makeLeftChildNode(node7);
        node8.setGain(1);
        node8.setWeight((float)4/150);
        node8.setPredValue(-0.6846351528304812f);
        // 9
        SharedTreeNode node9 = sharedTreeSubgraph.makeRightChildNode(node7);
        node9.setGain(1);
        node9.setWeight((float)8/150);
        node9.setPredValue(-0.6754172338067784f);
        // 10
        SharedTreeNode node10 = sharedTreeSubgraph.makeRightChildNode(node2);
        node10.setWeight((float)34/150);
        node10.setGain(1);
        node10.setInclusiveNa(true);
        node10.setCol(3, NAMES_IRIS[3]);
        node10.setSplitValue(1.699999988079071f);
        // 11
        SharedTreeNode node11 = sharedTreeSubgraph.makeLeftChildNode(node10);
        node11.setWeight((float)2/150);
        node11.setGain(1);
        node11.setInclusiveNa(true);
        node11.setCol(1, NAMES_IRIS[1]);
        node11.setSplitValue(2.799999952316284f);
        // 12
        SharedTreeNode node12 = sharedTreeSubgraph.makeLeftChildNode(node11);
        node12.setWeight((float)1/150);
        node12.setGain(1);
        node12.setPredValue(-0.6743764607417055f);
        // 13
        SharedTreeNode node13 = sharedTreeSubgraph.makeRightChildNode(node11);
        node13.setWeight((float)1/150);
        node13.setGain(1);
        node13.setPredValue( -0.6726308550350857f);
        // 14
        SharedTreeNode node14 = sharedTreeSubgraph.makeRightChildNode(node10);
        node14.setWeight((float)32/150);
        node14.setGain(1);
        node14.setInclusiveNa(true);
        node14.setCol(0, NAMES_IRIS[0]);
        node14.setSplitValue(-0.011622041810576758f);
        // 15
        SharedTreeNode node15 = sharedTreeSubgraph.makeLeftChildNode(node14);
        node15.setGain(1);
        node15.setWeight((float)21/150);
        node15.setPredValue(-0.6745058012907443f);
        // 16
        SharedTreeNode node16 = sharedTreeSubgraph.makeRightChildNode(node14);
        node16.setGain(1);
        node16.setWeight((float)11/150);
        node16.setPredValue(-0.6745058012907444f);

        return sharedTreeSubgraph;
    }

    private SharedTreeSubgraph createStage2k1Tree(SharedTreeGraph graph) {
        SharedTreeSubgraph sharedTreeSubgraph = graph.makeSubgraph("stage2k1");

        // 0
        SharedTreeNode node0 = sharedTreeSubgraph.makeRootNode();
        node0.setInclusiveNa(true);
        node0.setWeight(1);
        node0.setGain(1);
        node0.setCol(2, NAMES_IRIS[2]);
        node0.setSplitValue(4.950000047683716f);
        // 1
        SharedTreeNode node1 = sharedTreeSubgraph.makeLeftChildNode(node0);
        node1.setInclusiveNa(true);
        node1.setWeight((float)104/150);
        node1.setGain(1);
        node1.setCol(2, NAMES_IRIS[2]);
        node1.setSplitValue(-0.0003459393976288921f);
        // 2
        SharedTreeNode node2 = sharedTreeSubgraph.makeLeftChildNode(node1);
        node2.setInclusiveNa(true);
        node2.setWeight((float)50/150);
        node2.setGain(1);
        node2.setCol(1, NAMES_IRIS[1]);
        node2.setSplitValue(3.850000023841858f);
        // 3
        SharedTreeNode node3 = sharedTreeSubgraph.makeLeftChildNode(node2);
        node3.setInclusiveNa(true);
        node3.setWeight((float)44/150);
        node3.setGain(1);
        node3.setCol(0, NAMES_IRIS[0]);
        node3.setSplitValue(4.950000047683716f);
        // 4
        SharedTreeNode node4 = sharedTreeSubgraph.makeLeftChildNode(node3);
        node4.setWeight((float)20/150);
        node4.setGain(1);
        node4.setPredValue(-0.6745058012907443f);
        // 5
        SharedTreeNode node5 = sharedTreeSubgraph.makeRightChildNode(node3);
        node5.setWeight((float)24/150);
        node5.setGain(1);
        node5.setPredValue(-0.6745058012907442f);
        // 6
        SharedTreeNode node6 = sharedTreeSubgraph.makeRightChildNode(node2);
        node6.setGain(1);
        node6.setWeight((float)6/150);
        node6.setPredValue(-0.6745058012907442f);
        // 7
        SharedTreeNode node7 = sharedTreeSubgraph.makeRightChildNode(node1);
        node7.setInclusiveNa(true);
        node7.setWeight((float)54/150);
        node7.setGain(1);
        node7.setCol(3, NAMES_IRIS[3]);
        node7.setSplitValue(1.6500000357627869f);
        // 8
        SharedTreeNode node8 = sharedTreeSubgraph.makeLeftChildNode(node7);
        node8.setInclusiveNa(false);
        node8.setWeight((float)47/150);
        node8.setGain(1);
        node8.setCol(2, NAMES_IRIS[2]);
        node8.setSplitValue(4.75f);
        // 9
        SharedTreeNode node9 = sharedTreeSubgraph.makeLeftChildNode(node8);
        node9.setGain(1);
        node9.setWeight((float)44/150);
        node9.setPredValue(0.6830800316183638f);
        // 10
        SharedTreeNode node10 = sharedTreeSubgraph.makeRightChildNode(node8);
        node10.setGain(1);
        node10.setWeight((float)3/150);
        node10.setPredValue(0.6931525935264681f);
        // 11
        SharedTreeNode node11 = sharedTreeSubgraph.makeRightChildNode(node7);
        node11.setInclusiveNa(true);
        node11.setWeight((float)7/150);
        node11.setGain(1);
        node11.setCol(1, NAMES_IRIS[1]);
        node11.setSplitValue(3.100000023841858f);
        // 12
        SharedTreeNode node12 = sharedTreeSubgraph.makeLeftChildNode(node11);
        node12.setGain(1);
        node12.setWeight((float)6/150);
        node12.setPredValue(-0.6847693060435969f);
        // 13
        SharedTreeNode node13 = sharedTreeSubgraph.makeRightChildNode(node11);
        node13.setGain(1);
        node13.setWeight((float)1/150);
        node13.setPredValue(0.6948783951734169f);
        // 14
        SharedTreeNode node14 = sharedTreeSubgraph.makeRightChildNode(node0);
        node14.setInclusiveNa(true);
        node14.setWeight((float)46/150);
        node14.setGain(1);
        node14.setCol(3, NAMES_IRIS[3]);
        node14.setSplitValue(1.550000011920929f);
        // 15
        SharedTreeNode node15 = sharedTreeSubgraph.makeLeftChildNode(node14);
        node15.setInclusiveNa(true);
        node15.setWeight((float)3/150);
        node15.setGain(1);
        node15.setCol(2, NAMES_IRIS[2]);
        node15.setSplitValue(5.349999904632568f);
        // 16
        SharedTreeNode node16 = sharedTreeSubgraph.makeLeftChildNode(node15);
        node16.setGain(1);
        node16.setWeight((float)2/150);
        node16.setPredValue(-0.7256390101215537f);
        // 17
        SharedTreeNode node17 = sharedTreeSubgraph.makeRightChildNode(node15);
        node17.setGain(1);
        node17.setWeight((float)1/150);
        node17.setPredValue(-0.6745073224628325f);
        // 18
        SharedTreeNode node18 = sharedTreeSubgraph.makeRightChildNode(node14);
        node18.setInclusiveNa(true);
        node18.setWeight((float)43/150);
        node18.setGain(1);
        node18.setCol(3, NAMES_IRIS[3]);
        node18.setSplitValue(1.75f);
        // 19
        SharedTreeNode node19 = sharedTreeSubgraph.makeLeftChildNode(node18);
        node19.setInclusiveNa(true);
        node19.setWeight((float)3/150);
        node19.setGain(1);
        node19.setCol(2, NAMES_IRIS[2]);
        node19.setSplitValue(5.450000047683716f);
        // 20
        SharedTreeNode node20 = sharedTreeSubgraph.makeLeftChildNode(node19);
        node20.setGain(1);
        node20.setWeight((float)2/150);
        node20.setPredValue(0.7095376133806136f);
        // 21
        SharedTreeNode node21 = sharedTreeSubgraph.makeRightChildNode(node19);
        node21.setGain(1);
        node21.setWeight((float)1/150);
        node21.setPredValue(-0.7050440855119997f);
        // 22
        SharedTreeNode node22 = sharedTreeSubgraph.makeRightChildNode(node18);
        node22.setInclusiveNa(true);
        node22.setWeight((float)40/150);
        node22.setGain(1);
        node22.setCol(2, NAMES_IRIS[2]);
        node22.setSplitValue(5.1499998569488525f);
        // 23
        SharedTreeNode node23 = sharedTreeSubgraph.makeLeftChildNode(node22);
        node23.setGain(1);
        node23.setWeight((float)8/150);
        node23.setPredValue(-0.6754172338067784f);
        // 24
        SharedTreeNode node24 = sharedTreeSubgraph.makeRightChildNode(node22);
        node24.setGain(1);
        node24.setWeight((float)32/150);
        node24.setPredValue(-0.6745058012907442f);
        
        return sharedTreeSubgraph;
    }

    private SharedTreeSubgraph createStage2k2Tree(SharedTreeGraph graph) {
        SharedTreeSubgraph sharedTreeSubgraph = graph.makeSubgraph("stage2k2");

        // 0
        SharedTreeNode node0 = sharedTreeSubgraph.makeRootNode();
        node0.setInclusiveNa(true);
        node0.setWeight(1);
        node0.setGain(1);
        node0.setCol(2, NAMES_IRIS[2]);
        node0.setSplitValue(4.950000047683716f);
        // 1
        SharedTreeNode node1 = sharedTreeSubgraph.makeLeftChildNode(node0);
        node1.setInclusiveNa(true);
        node1.setWeight((float)104/150);
        node1.setGain(1);
        node1.setCol(3, NAMES_IRIS[3]);
        node1.setSplitValue(1.6500000357627869f);
        // 2
        SharedTreeNode node2 = sharedTreeSubgraph.makeLeftChildNode(node1);
        node2.setInclusiveNa(true);
        node2.setWeight((float)97/150);
        node2.setGain(1);
        node2.setCol(2, NAMES_IRIS[2]);
        node2.setSplitValue(4.75f);
        // 3
        SharedTreeNode node3 = sharedTreeSubgraph.makeLeftChildNode(node2);
        node3.setInclusiveNa(true);
        node3.setWeight((float)94/150);
        node3.setGain(1);
        node3.setCol(2, NAMES_IRIS[2]);
        node3.setSplitValue(2.449999988079071f);
        // 4
        SharedTreeNode node4 = sharedTreeSubgraph.makeLeftChildNode(node3);
        node4.setWeight((float)50/150);
        node4.setGain(1);
        node4.setPredValue(-0.6745058012907443f);
        // 5
        SharedTreeNode node5 = sharedTreeSubgraph.makeRightChildNode(node3);
        node5.setWeight((float)44/150);
        node5.setGain(1);
        node5.setPredValue(-0.6747735531773246f);
        // 6
        SharedTreeNode node6 = sharedTreeSubgraph.makeRightChildNode(node2);
        node6.setWeight((float)3/150);
        node6.setGain(1);
        node6.setInclusiveNa(true);
        node6.setCol(0, NAMES_IRIS[0]);
        node6.setSplitValue(6.8500001430511475f);
        // 7
        SharedTreeNode node7 = sharedTreeSubgraph.makeLeftChildNode(node6);
        node7.setWeight((float)2/150);
        node7.setGain(1);
        node7.setPredValue(-0.6847236294110374f);
        // 8
        SharedTreeNode node8 = sharedTreeSubgraph.makeRightChildNode(node6);
        node8.setWeight((float)1/150);
        node8.setGain(1);
        node8.setPredValue(-0.6847236294110374f);
        // 9
        SharedTreeNode node9 = sharedTreeSubgraph.makeRightChildNode(node1);
        node9.setInclusiveNa(true);
        node9.setGain(1);
        node9.setWeight((float)7/150);
        node9.setCol(1, NAMES_IRIS[1]);
        node9.setSplitValue(3.10f);
        // 10
        SharedTreeNode node10 = sharedTreeSubgraph.makeLeftChildNode(node9);
        node10.setInclusiveNa(true);
        node10.setGain(1);
        node10.setWeight((float)6/150);
        node10.setCol(2, NAMES_IRIS[2]);
        node10.setSplitValue(4.65f);
        // 11
        SharedTreeNode node11 = sharedTreeSubgraph.makeLeftChildNode(node10);
        node11.setWeight((float)1/150);
        node11.setGain(1);
        node11.setPredValue(0.7066811761656342f);
        // 12
        SharedTreeNode node12 = sharedTreeSubgraph.makeRightChildNode(node10);
        node12.setWeight((float)5/150);
        node12.setGain(1);
        node12.setPredValue(0.6825314855918333f);
        // 13
        SharedTreeNode node13 = sharedTreeSubgraph.makeRightChildNode(node9);
        node13.setWeight((float)1/150);
        node13.setGain(1);
        node13.setPredValue(-0.6867075272427257f);
        // 14
        SharedTreeNode node14 = sharedTreeSubgraph.makeRightChildNode(node0);
        node14.setWeight((float)46/150);
        node14.setInclusiveNa(true);
        node14.setGain(1);
        node14.setCol(3, NAMES_IRIS[3]);
        node14.setSplitValue(1.550000011920929f);
        // 15
        SharedTreeNode node15 = sharedTreeSubgraph.makeLeftChildNode(node14);
        node15.setWeight((float)3/150);
        node15.setInclusiveNa(true);
        node15.setGain(1);
        node15.setCol(3, NAMES_IRIS[3]);
        node15.setSplitValue(1.449999988079071f);
        // 16
        SharedTreeNode node16 = sharedTreeSubgraph.makeLeftChildNode(node15);
        node16.setGain(1);
        node16.setWeight((float)1/150);
        node16.setPredValue(0.6824006060695021f);
        // 17
        SharedTreeNode node17 = sharedTreeSubgraph.makeRightChildNode(node15);
        node17.setGain(1);
        node17.setWeight((float)2/150);
        node17.setPredValue(0.7415912335371097f);
        // 18
        SharedTreeNode node18 = sharedTreeSubgraph.makeRightChildNode(node14);
        node18.setWeight((float)43/150);
        node18.setInclusiveNa(true);
        node18.setGain(1);
        node18.setCol(3, NAMES_IRIS[3]);
        node18.setSplitValue(1.75f);
        // 19
        SharedTreeNode node19 = sharedTreeSubgraph.makeLeftChildNode(node18);
        node19.setWeight((float)3/150);
        node19.setInclusiveNa(true);
        node19.setGain(1);
        node19.setCol(0, NAMES_IRIS[0]);
        node19.setSplitValue(6.949999809265137f);
        // 20
        SharedTreeNode node20 = sharedTreeSubgraph.makeLeftChildNode(node19);
        node20.setGain(1);
        node20.setWeight((float)2/150);
        node20.setPredValue(-0.6872649449325733f);
        // 21
        SharedTreeNode node21 = sharedTreeSubgraph.makeRightChildNode(node19);
        node21.setGain(1);
        node21.setWeight((float)1/150);
        node21.setPredValue(0.7117181459858791f);
        // 22
        SharedTreeNode node22 = sharedTreeSubgraph.makeRightChildNode(node18);
        node22.setWeight((float)40/150);
        node22.setInclusiveNa(true);
        node22.setGain(1);
        node22.setCol(2, NAMES_IRIS[2]);
        node22.setSplitValue(5.1499998569488525f);
        // 23
        SharedTreeNode node23 = sharedTreeSubgraph.makeLeftChildNode(node22);
        node23.setGain(1);
        node23.setWeight((float)8/150);
        node23.setPredValue(0.684400573561297f);
        // 24
        SharedTreeNode node24 = sharedTreeSubgraph.makeRightChildNode(node22);
        node24.setGain(1);
        node24.setWeight((float)32/150);
        node24.setPredValue(0.6825314855918333f);
        
        
        return sharedTreeSubgraph;
    }

    public SharedTreeGraph createSharedTreeGraphForTestMLT() {
        SharedTreeGraph sharedTreeGraph = new SharedTreeGraph();
        createStage0k0Tree(sharedTreeGraph);
        createStage0k1Tree(sharedTreeGraph);
        createStage0k2Tree(sharedTreeGraph);
        createStage1k0Tree(sharedTreeGraph);
        createStage1k1Tree(sharedTreeGraph);
        createStage1k2Tree(sharedTreeGraph);
        createStage2k0Tree(sharedTreeGraph);
        createStage2k1Tree(sharedTreeGraph);
        createStage2k2Tree(sharedTreeGraph);

        return sharedTreeGraph;
    }

    
    @Test
    public void testPartialDependenceTree() {
        Frame frame = parseTestFile( "smalldata/h_test_input.csv");
        scope.track(frame);

        double res[] = new double[] {-2.188878791803408608e-02, -6.741245150466763925e-02, 4.768910591425746387e-03, 3.297972219392273502e-02, -2.188878791803408608e-02,
                3.297972219392273502e-02, -2.188878791803408608e-02, 4.768910591425746387e-03, -6.741245150466763925e-02, -6.741245150466763925e-02, -2.188878791803408608e-02,
                -6.741245150466763925e-02, 3.297972219392273502e-02, 3.297972219392273502e-02, -2.188878791803408608e-02, 3.297972219392273502e-02, 4.768910591425746387e-03,
                -2.188878791803408608e-02, -6.741245150466763925e-02, -6.741245150466763925e-02, 2.213861966073388601e-02, -2.188878791803408608e-02, -2.188878791803408608e-02,
                3.297972219392273502e-02, -2.188878791803408608e-02, 4.768910591425746387e-03, -2.188878791803408608e-02, -2.188878791803408608e-02, -2.188878791803408608e-02,
                -6.741245150466763925e-02, -6.741245150466763925e-02, -2.188878791803408608e-02, -2.188878791803408608e-02, -2.188878791803408608e-02, 6.874241915520466761e-02,
                3.297972219392273502e-02, -2.188878791803408608e-02, 4.768910591425746387e-03, 3.297972219392273502e-02, 3.297972219392273502e-02, -2.188878791803408608e-02,
                3.297972219392273502e-02, -3.850508864319568403e-02, 6.874241915520466761e-02, 2.213861966073388601e-02, -2.188878791803408608e-02, -3.850508864319568403e-02,
                4.768910591425746387e-03, -3.850508864319568403e-02, 3.297972219392273502e-02, -3.850508864319568403e-02, -2.188878791803408608e-02, -2.188878791803408608e-02,
                4.768910591425746387e-03, -2.188878791803408608e-02, -2.188878791803408608e-02, -2.188878791803408608e-02, -3.850508864319568403e-02, -2.188878791803408608e-02,
                3.297972219392273502e-02, 3.297972219392273502e-02, 4.768910591425746387e-03, 2.213861966073388601e-02, 2.213861966073388601e-02, 3.297972219392273502e-02,
                6.874241915520466761e-02, 4.768910591425746387e-03, -2.188878791803408608e-02, 3.297972219392273502e-02, 3.297972219392273502e-02, -2.188878791803408608e-02,
                3.297972219392273502e-02, 3.297972219392273502e-02, -2.188878791803408608e-02, 6.874241915520466761e-02, 3.297972219392273502e-02, 2.213861966073388601e-02,
                4.768910591425746387e-03, 6.874241915520466761e-02, -3.850508864319568403e-02, 4.768910591425746387e-03, 4.768910591425746387e-03, 6.874241915520466761e-02,
                -2.188878791803408608e-02, 4.768910591425746387e-03, 4.768910591425746387e-03, 3.297972219392273502e-02, 2.213861966073388601e-02, 1.334595684654562298e-01,
                -2.188878791803408608e-02};

        SharedTreeGraph tree = createSharedTreeGraphForTest();

        Vec result = FriedmanPopescusH.partialDependenceTree(tree.subgraphArray.get(0), new int[] {0,1,2}, 0.1, frame);
        scope.track(result);
        assertEquals(result.length(), res.length);
        for (int i = 0; i < res.length; i++) {
            assertEquals(res[i], result.at(i), 1e-8);
        }
        
        res = new double[] {-1.434123233310243516e-02, -6.741245150466763925e-02, 2.945258844499629158e-02, 2.945258844499629158e-02, -1.434123233310243516e-02,
                2.945258844499629158e-02, -1.434123233310243516e-02, 2.945258844499629158e-02, -6.741245150466763925e-02, -6.741245150466763925e-02, -1.434123233310243516e-02,
                -6.741245150466763925e-02, 2.945258844499629158e-02, 2.945258844499629158e-02, -1.434123233310243516e-02, 2.945258844499629158e-02, 2.945258844499629158e-02,
                -1.434123233310243516e-02, -6.741245150466763925e-02, -6.741245150466763925e-02, -1.434123233310243516e-02, -1.434123233310243516e-02, -1.434123233310243516e-02, 
                2.945258844499629158e-02, -1.434123233310243516e-02, 2.945258844499629158e-02, -1.434123233310243516e-02, -1.434123233310243516e-02, -1.434123233310243516e-02,
                -6.741245150466763925e-02, -6.741245150466763925e-02, -1.434123233310243516e-02, -1.434123233310243516e-02, -1.434123233310243516e-02, 2.945258844499629158e-02,
                2.945258844499629158e-02, -1.434123233310243516e-02, 2.945258844499629158e-02, 2.945258844499629158e-02, 2.945258844499629158e-02, -1.434123233310243516e-02,
                2.945258844499629158e-02, -3.850508864319568403e-02, 2.945258844499629158e-02, -1.434123233310243516e-02, -1.434123233310243516e-02, -3.850508864319568403e-02,
                2.945258844499629158e-02, -3.850508864319568403e-02, 2.945258844499629158e-02, -3.850508864319568403e-02, -1.434123233310243516e-02, -1.434123233310243516e-02,
                2.945258844499629158e-02, -1.434123233310243516e-02, -1.434123233310243516e-02, -1.434123233310243516e-02, -3.850508864319568403e-02, -1.434123233310243516e-02,
                2.945258844499629158e-02, 2.945258844499629158e-02, 2.945258844499629158e-02, -1.434123233310243516e-02, -1.434123233310243516e-02, 2.945258844499629158e-02,
                2.945258844499629158e-02, 2.945258844499629158e-02, -1.434123233310243516e-02, 2.945258844499629158e-02, 2.945258844499629158e-02, -1.434123233310243516e-02,
                2.945258844499629158e-02, 2.945258844499629158e-02, -1.434123233310243516e-02, 2.945258844499629158e-02, 2.945258844499629158e-02, -1.434123233310243516e-02,
                2.945258844499629158e-02, 2.945258844499629158e-02, -3.850508864319568403e-02, 2.945258844499629158e-02, 2.945258844499629158e-02, 2.945258844499629158e-02,
                -1.434123233310243516e-02, 4.050185783942948647e-02, 4.050185783942948647e-02, 4.050185783942948647e-02, -1.434123233310243516e-02, 4.050185783942948647e-02,
                -1.434123233310243516e-02};
        
        Frame frame1 = new Frame();
        frame1.add(new String[] {"feature0", "feature2"}, new Vec[] {frame.vec(0), frame.vec(2)});
        result = FriedmanPopescusH.partialDependenceTree(tree.subgraphArray.get(0), new int[] {0,2}, 0.1, frame1);
        scope.track(result);
        assertEquals(result.length(), res.length);
        for (int i = 0; i < res.length; i++) {
            assertEquals(res[i], result.at(i), 1e-8);
        }

        res = new double[] {-3.974592091618971840e-03, 2.943907280470811627e-02, -1.682618404386760150e-02, -3.974592091618971840e-03, -3.974592091618971840e-03, -3.974592091618971840e-03,
                -3.974592091618971840e-03, -1.682618404386760150e-02, -3.974592091618971840e-03, -3.974592091618971840e-03, -3.974592091618971840e-03, -3.974592091618971840e-03,
                -3.974592091618971840e-03, -3.974592091618971840e-03, -3.974592091618971840e-03, 1.314717752234634968e-02, -1.682618404386760150e-02, -3.974592091618971840e-03,
                -3.974592091618971840e-03, 2.943907280470811627e-02, 2.943907280470811627e-02, -3.974592091618971840e-03, -3.974592091618971840e-03, -3.974592091618971840e-03,
                -3.974592091618971840e-03, -1.682618404386760150e-02, -3.974592091618971840e-03, -3.974592091618971840e-03, -3.974592091618971840e-03, -1.682618404386760150e-02,
                -3.974592091618971840e-03, -3.974592091618971840e-03, -3.974592091618971840e-03, -3.974592091618971840e-03, 2.943907280470811627e-02, -3.974592091618971840e-03,
                -3.974592091618971840e-03, -1.682618404386760150e-02, 5.221087979433323631e-04, 5.221087979433323631e-04, -1.232948315430529643e-02, 5.221087979433323631e-04,
                5.221087979433323631e-04, 3.393577369427042134e-02, 3.393577369427042134e-02, 5.221087979433323631e-04, 5.221087979433323631e-04, -1.232948315430529643e-02,
                5.221087979433323631e-04, 5.221087979433323631e-04, 5.221087979433323631e-04, 5.221087979433323631e-04, 5.221087979433323631e-04, -1.232948315430529643e-02,
                5.221087979433323631e-04, 5.221087979433323631e-04, -1.232948315430529643e-02, 5.221087979433323631e-04, -1.232948315430529643e-02, 5.221087979433323631e-04,
                5.221087979433323631e-04, -1.232948315430529643e-02, 3.393577369427042134e-02, 3.393577369427042134e-02, 5.221087979433323631e-04, 3.393577369427042134e-02,
                -1.232948315430529643e-02, 5.221087979433323631e-04, 5.221087979433323631e-04, 5.221087979433323631e-04, 5.221087979433323631e-04, 5.221087979433323631e-04,
                5.221087979433323631e-04, 5.221087979433323631e-04, 3.393577369427042134e-02, 5.221087979433323631e-04, 1.764387841190865475e-02, -1.232948315430529643e-02,
                3.393577369427042134e-02, 5.221087979433323631e-04, -1.232948315430529643e-02, -1.232948315430529643e-02, 3.393577369427042134e-02, -1.232948315430529643e-02,
                -1.232948315430529643e-02, -1.232948315430529643e-02, 5.221087979433323631e-04, 1.764387841190865475e-02, 6.341803060227391153e-02, -1.232948315430529643e-02};
        
        Frame frame2 = new Frame();
        frame2.add(new String[] {"feature0", "feature1"}, new Vec[] {frame.vec(0), frame.vec(1)});
        result = FriedmanPopescusH.partialDependenceTree(tree.subgraphArray.get(0), new int[] {0,1}, 0.1, frame2);
        scope.track(result);
        assertEquals(result.length(), res.length);
        for (int i = 0; i < res.length; i++) {
            assertEquals(res[i], result.at(i), 1e-8);
        }
    }
    
    @Test 
    public void testUniqueRowsWithCounts() {
        Frame inputFrame = new TestFrameBuilder()
                .withName("inputFrame")
                .withColNames("Str_0", "Str_1", "Num_0", "Num_1")
                .withVecTypes(Vec.T_STR, Vec.T_STR, Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ar("a", "a", "b", "a", "c", "b"))
                .withDataForCol(1, ar("z", "z", "y", "z", "w", "y"))
                .withDataForCol(2, ard(0, 0, 1, 2, 1, 1))
                .withDataForCol(3, ard(2, 2, 3, 2, 4, 3))
                .withChunkLayout(1, 1, 3, 1)
                .build();

        Frame reference = new TestFrameBuilder()
                .withName("referenceFrame")
                .withColNames("Str_0", "Str_1", "Num_0", "Num_1", "nrows")
                .withVecTypes(Vec.T_STR, Vec.T_STR, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, ar("a", "a", "b", "c"))
                .withDataForCol(1, ar("z", "z", "y", "w"))
                .withDataForCol(2, ard(0, 2, 1, 1))
                .withDataForCol(3, ard(2, 2, 3, 4))
                .withDataForCol(4, ard(2, 1, 2, 1))
                .build();

        Frame result = FriedmanPopescusH.uniqueRowsWithCounts(inputFrame);

        System.out.println("inputFrame = " + inputFrame.toTwoDimTable(0, 10, false));
        System.out.println("reference = " + reference.toTwoDimTable(0, 10, false));
        System.out.println("result = " + result.toTwoDimTable(0, 10, false));

        scope.track(result);
        assertEquals(result.numRows(), inputFrame.numRows() - 2); // 2 Rows are equal and should be removed in results
        assertFrameEquals(reference, result, 0);
    }
    
    /*
    * tested against this:
    * 
    * 
    *     DATA_COUNT = 100
    RANDOM_SEED = 137
    TRAIN_FRACTION = 0.9
    
    # Fabricate a data set of DATA_COUNT samples of three continuous predictor variables and one 
    # continuous target variable:
    #
    # 1.  x0, x1, and x2 are independent random variables uniformly distributed on the unit interval.
    # 2.  y is x0∗x1 + x2 + e, where e is a random variable normally distributed with mean 0 and variance 0.01.
    #
    # So the relationship between the predictor variables and the target variable features an interaction between
    # two of the former, namely x0 and x1. 
    
    np.random.seed(RANDOM_SEED)
    xs = pd.DataFrame(np.random.uniform(size = (DATA_COUNT, 3)))
    xs.columns = ['x0', 'x1', 'x2']
    
    y = pd.DataFrame(xs.x0*xs.x1 + xs.x2 + pd.Series(0.1*np.random.randn(DATA_COUNT)))
    y.columns = ['y']
    
    # Train a gradient-boosting regressor on the first TRAIN_FRACTION of the data set, and test it on the rest.
    # In this toy analysis, hyperparameter tuning is omitted. However, note that the default value of the 
    # hyperparameter max_depth is 3, so the regressor could represent interactions among up to three predictor variables.
    
    train_ilocs = range(int(TRAIN_FRACTION*DATA_COUNT))
    test_ilocs = range(int(TRAIN_FRACTION*DATA_COUNT), DATA_COUNT)
    
    learn_rate=0.1
    ntrees=1
    max_depth=3
    min_rows=1

    gbr_1 = GradientBoostingRegressor(random_state = RANDOM_SEED, learning_rate=learn_rate, n_estimators=ntrees, max_depth=max_depth,
                                      min_samples_leaf=min_rows )
    gbr_1.fit(xs.iloc[train_ilocs], y.y.iloc[train_ilocs])
    gbr_1.score(xs.iloc[test_ilocs], y.y.iloc[test_ilocs])

    # Compute the H statistic of all three predictor variables. We expect the value to be relatively small.
    h(gbr_1, xs.iloc[train_ilocs])
    h_val = h(gbr_1, xs.iloc[train_ilocs], ['x0', 'x1'])
    h_val = h(gbr_1, xs.iloc[train_ilocs], ['x0', 'x2'])
    h_val = h(gbr_1, xs.iloc[train_ilocs], ['x2', 'x0'])
    h_val = h(gbr_1, xs.iloc[train_ilocs], ['x2', 'x1'])
    * 
    * */
    @Test
    public void testRegression() {
        Frame frame = parseTestFile( "smalldata/h_test_input.csv");
        scope.track(frame);

        SharedTreeGraph tree = createSharedTreeGraphForTest();

        when(mockModel2.getSharedTreeSubgraph(0,0)).thenReturn(tree.subgraphArray.get(0));

        GBMModel.GBMParameters params = new GBMModel.GBMParameters();
        params._learn_rate = 0.1;
        params._ntrees = 1;
        mockModel2._parms = params;

        SharedTreeSubgraph[][] sharedTreeSubgraphs = new SharedTreeSubgraph[1][1];
        sharedTreeSubgraphs[0][0] = mockModel2.getSharedTreeSubgraph(0,0);

        double[] expectedResult = new double[] {-5.466909073493543547e-03, 2.794675582283354456e-02, -1.831850102574217320e-02, -5.466909073493543547e-03, -5.466909073493543547e-03,
                -5.466909073493543547e-03, -5.466909073493543547e-03, -1.831850102574217320e-02, -5.466909073493543547e-03, -5.466909073493543547e-03, -5.466909073493543547e-03,
                -5.466909073493543547e-03, -5.466909073493543547e-03, -5.466909073493543547e-03, -5.466909073493543547e-03, 1.165486054047177797e-02, -1.831850102574217320e-02,
                -5.466909073493543547e-03, -5.466909073493543547e-03, 2.794675582283354456e-02, 2.794675582283354456e-02, -5.466909073493543547e-03, -5.466909073493543547e-03,
                -5.466909073493543547e-03, -5.466909073493543547e-03, -1.831850102574217320e-02, -5.466909073493543547e-03, -5.466909073493543547e-03,-5.466909073493543547e-03,
                -1.831850102574217320e-02, -5.466909073493543547e-03,-5.466909073493543547e-03, -5.466909073493543547e-03, -5.466909073493543547e-03, 2.794675582283354456e-02,
                -5.466909073493543547e-03, -5.466909073493543547e-03, -1.831850102574217320e-02, -9.702081839312389107e-04, -9.702081839312389107e-04, -1.382180013617986813e-02,
                -9.702081839312389107e-04, -9.702081839312389107e-04, 3.244345671239585310e-02, 3.244345671239585310e-02, -9.702081839312389107e-04, -9.702081839312389107e-04,
                -1.382180013617986813e-02, -9.702081839312389107e-04, -9.702081839312389107e-04, -9.702081839312389107e-04, -9.702081839312389107e-04, -9.702081839312389107e-04,
                -1.382180013617986813e-02, -9.702081839312389107e-04, -9.702081839312389107e-04, -1.382180013617986813e-02, -9.702081839312389107e-04, -1.382180013617986813e-02,
                -9.702081839312389107e-04, -9.702081839312389107e-04, -1.382180013617986813e-02, 3.244345671239585310e-02, 3.244345671239585310e-02, -9.702081839312389107e-04,
                3.244345671239585310e-02, -1.382180013617986813e-02, -9.702081839312389107e-04, -9.702081839312389107e-04, -9.702081839312389107e-04, -9.702081839312389107e-04,
                -9.702081839312389107e-04, -9.702081839312389107e-04, -9.702081839312389107e-04, 3.244345671239585310e-02, -9.702081839312389107e-04, 1.615156143003408304e-02,
                -1.382180013617986813e-02, 3.244345671239585310e-02, -9.702081839312389107e-04, -1.382180013617986813e-02, -1.382180013617986813e-02, 3.244345671239585310e-02,
                -1.382180013617986813e-02, -1.382180013617986813e-02, -1.382180013617986813e-02, -9.702081839312389107e-04, 1.615156143003408304e-02, 6.192571362039934330e-02,
                -1.382180013617986813e-02};
        checkFValues(new int[] {0,1}, frame, new String[]{"feature0", "feature1"}, 4, sharedTreeSubgraphs, params._learn_rate, expectedResult);
        
        expectedResult = new double[] {4.768910591425756795e-03, 4.768910591425756795e-03, 4.768910591425756795e-03, 4.768910591425756795e-03, 4.768910591425756795e-03,
                -2.188878791803407567e-02, 4.768910591425756795e-03, 4.768910591425756795e-03, -2.188878791803407567e-02, 4.768910591425756795e-03, 4.768910591425756795e-03,
                4.768910591425756795e-03, -2.188878791803407567e-02, -2.188878791803407567e-02, -5.502358170689392036e-02, 4.768910591425756795e-03, 4.768910591425756795e-03,
                -2.188878791803407567e-02, 4.768910591425756795e-03, 4.768910591425756795e-03, 3.297972219392274890e-02, -2.188878791803407567e-02, -5.502358170689392036e-02,
                -2.188878791803407567e-02, -5.502358170689392036e-02, -5.502358170689392036e-02, -2.188878791803407567e-02, 3.297972219392274890e-02, -5.502358170689392036e-02,
                3.297972219392274890e-02, -2.188878791803407567e-02, -5.502358170689392036e-02, 3.297972219392274890e-02, -2.188878791803407567e-02, -5.502358170689392036e-02,
                3.297972219392274890e-02, 3.297972219392274890e-02, -2.188878791803407567e-02, -2.188878791803407567e-02, 3.297972219392274890e-02, 3.297972219392274890e-02,
                -2.188878791803407567e-02, -2.188878791803407567e-02, -2.188878791803407567e-02, -5.502358170689392036e-02, -2.188878791803407567e-02, -2.188878791803407567e-02,
                -2.188878791803407567e-02, -2.188878791803407567e-02, 3.297972219392274890e-02, -2.188878791803407567e-02, -2.188878791803407567e-02, 3.297972219392274890e-02,
                -5.502358170689392036e-02, -2.188878791803407567e-02, -5.502358170689392036e-02, 3.297972219392274890e-02, 3.297972219392274890e-02, -2.188878791803407567e-02,
                -5.502358170689392036e-02, 3.297972219392274890e-02, 3.297972219392274890e-02, 3.297972219392274890e-02, -2.188878791803407567e-02, 3.297972219392274890e-02,
                -5.502358170689392036e-02, 3.297972219392274890e-02, 3.297972219392274890e-02, 3.297972219392274890e-02, -2.188878791803407567e-02, -2.188878791803407567e-02,
                -2.188878791803407567e-02, -2.188878791803407567e-02, -2.188878791803407567e-02, 2.213861966073389642e-02, 3.297972219392274890e-02, 2.213861966073389642e-02,
                -5.502358170689392036e-02, 7.798772619952633323e-02, 2.213861966073389642e-02, -5.502358170689392036e-02, 7.798772619952633323e-02, 2.213861966073389642e-02,
                2.213861966073389642e-02, 2.213861966073389642e-02, 7.798772619952633323e-02, 7.798772619952633323e-02, 7.798772619952633323e-02, 7.798772619952633323e-02,
                7.798772619952633323e-02,

        };
        checkFValues(new int[] {1,2}, frame, new String[]{"feature1", "feature2"}, 4, sharedTreeSubgraphs, params._learn_rate, expectedResult);
        
        expectedResult = new double[] {-2.188878791803407567e-02, -6.741245150466762537e-02, 4.768910591425757663e-03, 3.297972219392274890e-02, -2.188878791803407567e-02,
                3.297972219392274890e-02, -2.188878791803407567e-02, 4.768910591425757663e-03, -6.741245150466762537e-02, -6.741245150466762537e-02, -2.188878791803407567e-02,
                -6.741245150466762537e-02, 3.297972219392274890e-02, 3.297972219392274890e-02, -2.188878791803407567e-02, 3.297972219392274890e-02, 4.768910591425757663e-03,
                -2.188878791803407567e-02, -6.741245150466762537e-02, -6.741245150466762537e-02, 2.213861966073389642e-02, -2.188878791803407567e-02, -2.188878791803407567e-02, 
                3.297972219392274890e-02, -2.188878791803407567e-02, 4.768910591425757663e-03, -2.188878791803407567e-02, -2.188878791803407567e-02, -2.188878791803407567e-02,
                -6.741245150466762537e-02, -6.741245150466762537e-02, -2.188878791803407567e-02, -2.188878791803407567e-02, -2.188878791803407567e-02, 6.874241915520468149e-02,
                3.297972219392274890e-02, -2.188878791803407567e-02, 4.768910591425757663e-03, 3.297972219392274890e-02, 3.297972219392274890e-02, -2.188878791803407567e-02,
                3.297972219392274890e-02, -3.850508864319567015e-02, 6.874241915520468149e-02, 2.213861966073389642e-02, -2.188878791803407567e-02, -3.850508864319567015e-02,
                4.768910591425757663e-03, -3.850508864319567015e-02, 3.297972219392274890e-02, -3.850508864319567015e-02, -2.188878791803407567e-02, -2.188878791803407567e-02,
                4.768910591425757663e-03, -2.188878791803407567e-02, -2.188878791803407567e-02, -2.188878791803407567e-02, -3.850508864319567015e-02, -2.188878791803407567e-02,
                3.297972219392274890e-02, 3.297972219392274890e-02, 4.768910591425757663e-03, 2.213861966073389642e-02, 2.213861966073389642e-02, 3.297972219392274890e-02,
                6.874241915520468149e-02, 4.768910591425757663e-03, -2.188878791803407567e-02, 3.297972219392274890e-02, 3.297972219392274890e-02, -2.188878791803407567e-02,
                3.297972219392274890e-02, 3.297972219392274890e-02, -2.188878791803407567e-02, 6.874241915520468149e-02, 3.297972219392274890e-02, 2.213861966073389642e-02,
                4.768910591425757663e-03, 6.874241915520468149e-02, -3.850508864319567015e-02, 4.768910591425757663e-03, 4.768910591425757663e-03, 6.874241915520468149e-02,
                -2.188878791803407567e-02, 4.768910591425757663e-03, 4.768910591425757663e-03, 3.297972219392274890e-02, 2.213861966073389642e-02, 1.334595684654562298e-01,
                -2.188878791803407567e-02,
        };
        checkFValues(new int[] {0,1,2}, frame, frame.names(), 5,  sharedTreeSubgraphs, params._learn_rate, expectedResult);
        
        expectedResult = new double[] {-1.600119057756334978e-02, -1.600119057756334978e-02, -1.600119057756334978e-02, -1.600119057756334978e-02, -1.600119057756334978e-02,
                -1.600119057756334978e-02, -1.600119057756334978e-02, -1.600119057756334978e-02, -1.600119057756334978e-02, -1.600119057756334978e-02, -1.600119057756334978e-02,
                -1.600119057756334978e-02, -1.600119057756334978e-02, -1.600119057756334978e-02, -1.600119057756334978e-02, -1.600119057756334978e-02, -1.600119057756334978e-02,
                -1.600119057756334978e-02, -1.600119057756334978e-02, -1.600119057756334978e-02, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03,
                -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03,
                -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03,
                -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03,
                -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03,
                -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03,
                -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03,
                -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03,
                -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03,
                -3.149598625314720991e-03, -3.149598625314720991e-03, -3.149598625314720991e-03, 1.397217098865059792e-02, 1.397217098865059792e-02, 1.397217098865059792e-02,
                3.447581725787000895e-02, 3.447581725787000895e-02, 3.447581725787000895e-02, 3.447581725787000895e-02, 3.447581725787000895e-02, 3.447581725787000895e-02, 
                3.447581725787000895e-02, 3.447581725787000895e-02, 3.447581725787000895e-02, 3.447581725787000895e-02, 3.447581725787000895e-02, 3.447581725787000895e-02, 
                3.447581725787000895e-02};
        checkFValues(new int[] {1}, frame, new String[]{"feature1"}, 3,  sharedTreeSubgraphs, params._learn_rate, expectedResult);
        
        double h = FriedmanPopescusH.h(frame, new String[] {"feature0","feature1","feature2"}, params._learn_rate, sharedTreeSubgraphs);
        assertEquals(h, 0.08603547308125703, 1e-8);
         h = FriedmanPopescusH.h(frame, new String[] {"feature0","feature1"}, params._learn_rate, sharedTreeSubgraphs);
        assertEquals(h, 0.1821050597335194, 1e-7);
         h = FriedmanPopescusH.h(frame, new String[] {"feature0","feature2"}, params._learn_rate, sharedTreeSubgraphs);
        assertEquals(h, 0.1695429601435328, 1e-8);
         h = FriedmanPopescusH.h(frame, new String[] {"feature2","feature0"}, params._learn_rate, sharedTreeSubgraphs);
        assertEquals(h, 0.16954296014353273, 1e-8);
         h = FriedmanPopescusH.h(frame, new String[] {"feature2","feature1"}, params._learn_rate, sharedTreeSubgraphs);
        assertEquals(h, 0.2508738347652033, 1e-8);
    }
    
    void checkFValues(int[] modelIds, Frame filteredFrame, String[] cols, int expectedNumCols, SharedTreeSubgraph[][] sharedTreeSubgraphs, double learn_rate, double[] result) {
        Frame res = FriedmanPopescusH.computeFValues(modelIds, filteredFrame, cols, learn_rate, sharedTreeSubgraphs);
        scope.track(res);
        assertEquals(res.numCols(),expectedNumCols);
        assertEquals(res.numRows(), result.length);
        for (int i = 0; i < res.numRows(); i++) {
            assertEquals(res.vec(0).at(i), result[i], 1e-4);
        }
    }
    
    // tested against this:
    //
    //    df = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    //    X_train = df.as_data_frame()[['sepal_len', 'sepal_wid', 'petal_len', 'petal_wid']]
    //    y_train = df.as_data_frame()[['class']]
    //
    //    clf = GradientBoostingClassifier(n_estimators=3, learning_rate=1.0, max_depth=4, random_state=0).fit(X_train, y_train)
    //  #  clf.score(X_test, y_test)
    //
    //    h_val = h(clf, X_train)
    //    h_val = h(clf, X_train, ['sepal_len', 'sepal_wid', 'petal_len'])
    //    h_val = h(clf, X_train, ['sepal_len', 'sepal_wid'])
    //    h_val = h(clf, X_train, ['sepal_wid', 'petal_len'])
    //    h_val = h(clf, X_train, ['petal_len', 'sepal_wid'])
    
    @Test
    public void testClassification() {
        Frame frame = parseTestFile( "smalldata/iris/iris_wheader.csv");
        scope.track(frame);
        SharedTreeGraph tree = createSharedTreeGraphForTestMLT();
        
        Frame filtered = FriedmanPopescusH.filterFrame(frame, new String[] {"sepal_len"});
        scope.track(filtered);
        filtered._key = Key.make();
        
        when(mockModel.getSharedTreeSubgraph(0,0)).thenReturn(tree.subgraphArray.get(0));
        when(mockModel.getSharedTreeSubgraph(0,1)).thenReturn(tree.subgraphArray.get(1));
        when(mockModel.getSharedTreeSubgraph(0,2)).thenReturn(tree.subgraphArray.get(2));
        when(mockModel.getSharedTreeSubgraph(1,0)).thenReturn(tree.subgraphArray.get(3));
        when(mockModel.getSharedTreeSubgraph(1,1)).thenReturn(tree.subgraphArray.get(4));
        when(mockModel.getSharedTreeSubgraph(1,2)).thenReturn(tree.subgraphArray.get(5));
        when(mockModel.getSharedTreeSubgraph(2,0)).thenReturn(tree.subgraphArray.get(6));
        when(mockModel.getSharedTreeSubgraph(2,1)).thenReturn(tree.subgraphArray.get(7));
        when(mockModel.getSharedTreeSubgraph(2,2)).thenReturn(tree.subgraphArray.get(8));
        
        GBMModel.GBMParameters params = new GBMModel.GBMParameters();
        params._learn_rate = 1.0;
        params._ntrees = 3;
        mockModel._parms = params;

        SharedTreeSubgraph[][] sharedTreeSubgraphs = new SharedTreeSubgraph[params._ntrees][3];
        for (int i = 0; i <  params._ntrees; i++) {
            for (int j = 0; j < 3; j++) {
                sharedTreeSubgraphs[i][j] = mockModel.getSharedTreeSubgraph(i, j);
            }
        }
        
        double[] expectedResult = new double[] {-1.110223024625156540e-16, -1.110223024625156540e-16, -1.110223024625156540e-16, -1.110223024625156540e-16, -1.110223024625156540e-16,
                -1.110223024625156540e-16, -1.110223024625156540e-16, -1.110223024625156540e-16, -1.110223024625156540e-16, -1.110223024625156540e-16, -1.110223024625156540e-16,
                -1.110223024625156540e-16, -1.110223024625156540e-16, -1.110223024625156540e-16, -1.110223024625156540e-16, 0.000000000000000000e+00, 0.000000000000000000e+00,
                0.000000000000000000e+00, 0.000000000000000000e+00, 0.000000000000000000e+00, 0.000000000000000000e+00, 0.000000000000000000e+00, 0.000000000000000000e+00,
                0.000000000000000000e+00, 0.000000000000000000e+00, 0.000000000000000000e+00, 0.000000000000000000e+00, 0.000000000000000000e+00, 0.000000000000000000e+00,
                0.000000000000000000e+00, 0.000000000000000000e+00, 0.000000000000000000e+00, 0.000000000000000000e+00, 0.000000000000000000e+00, 0.000000000000000000e+00};
        checkFValues(new int[] {0}, frame, new String[]{"sepal_len"}, 3, sharedTreeSubgraphs, params._learn_rate, expectedResult);

        expectedResult = new double[] {-1.598199002489364418e-05, -1.598199002489364418e-05, -1.598199002489364418e-05, -1.598199002489364418e-05, -1.598199002489364418e-05,
                -1.598199002489364418e-05, -1.598199002489364418e-05, -1.598199002489364418e-05, 7.292752730092555424e-06, 7.292752730092555424e-06, 7.292752730092555424e-06,
                7.292752730092555424e-06, 7.292752730092555424e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06,
                7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06};
        checkFValues(new int[] {1}, frame, new String[]{"sepal_wid"}, 3, sharedTreeSubgraphs, params._learn_rate, expectedResult);
        
        expectedResult = new double[] {1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00,
                1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00, -9.284181247164067230e-01, -9.284181247164067230e-01, -9.284181247164067230e-01,
                -9.284181247164067230e-01, -9.284181247164067230e-01, -9.284181247164067230e-01, -9.284181247164067230e-01, -9.284181247164067230e-01, -9.284181247164067230e-01,
                -9.284181247164067230e-01, -9.284181247164067230e-01, -9.284181247164067230e-01, -9.284181247164067230e-01, -9.284181247164067230e-01, -9.284181247164067230e-01,
                -9.284181247164067230e-01, -9.284181247164067230e-01, -9.350317669621379668e-01, -9.350317669621379668e-01, -9.309887451596334795e-01, -9.309887451596334795e-01,
                -9.309887451596334795e-01, -9.309887451596334795e-01, -9.309887451596334795e-01, -9.309887451596334795e-01, -9.309887451596334795e-01, -9.309887451596334795e-01,
                -9.309887451596334795e-01, -9.309887451596334795e-01, -9.309887451596334795e-01, -9.309887451596334795e-01, -9.309887451596334795e-01, -9.309887451596334795e-01,
                -9.309887451596334795e-01};
        checkFValues(new int[] {2}, frame, new String[]{"petal_len"}, 3, sharedTreeSubgraphs, params._learn_rate, expectedResult);

        expectedResult = new double[] {1.998372945075835405e+00, 1.998372945075835405e+00, 1.998372945075835405e+00, 1.998372945075835405e+00, 1.998372945075835405e+00, 1.998372945075835405e+00,
                -1.001627054924164151e+00, -1.001627054924164151e+00, -1.001627054924164151e+00, -1.001627054924164151e+00, -1.001627054924164151e+00, -1.001627054924164151e+00,
                -1.001627054924164151e+00, -1.011312200166400332e+00, -9.959003477696179996e-01, -9.959003477696179996e-01, -9.959003477696179996e-01, -9.959003477696179996e-01,
                -9.959003477696179996e-01, -9.959003477696179996e-01, -9.959003477696179996e-01, -9.959003477696179996e-01};
        checkFValues(new int[] {3}, frame, new String[]{"petal_wid"}, 3, sharedTreeSubgraphs, params._learn_rate, expectedResult);
        
        expectedResult = new double[] {7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, -1.598199002500466648e-05, 7.292752729981533122e-06,
                7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06,
                7.292752729981533122e-06, -1.598199002500466648e-05, -1.598199002500466648e-05, 7.292752729981533122e-06, 7.292752729981533122e-06, -1.598199002500466648e-05,
                -1.598199002500466648e-05, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06,
                7.292752729981533122e-06, -1.598199002500466648e-05, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06,
                7.292752729981533122e-06, -1.598199002500466648e-05, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06,
                7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, -1.598199002500466648e-05, -1.598199002500466648e-05,
                -1.598199002500466648e-05, -1.598199002500466648e-05, 7.292752729981533122e-06, 7.292752729981533122e-06, -1.598199002500466648e-05, -1.598199002500466648e-05,
                -1.598199002500466648e-05, 7.292752729981533122e-06, 7.292752729981533122e-06, -1.598199002500466648e-05, -1.598199002500466648e-05, -1.598199002500466648e-05,
                7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, -1.598199002478262187e-05, -1.598199002478262187e-05, 
                -1.598199002478262187e-05, 7.292752730092555424e-06, 7.292752730092555424e-06, 7.292752730092555424e-06, -1.598199002478262187e-05, -1.598199002478262187e-05,
                7.292752730092555424e-06, 7.292752730092555424e-06, 7.292752730092555424e-06, -1.598199002478262187e-05, -1.598199002478262187e-05, 7.292752730092555424e-06,
                7.292752730092555424e-06, -1.598199002478262187e-05, -1.598199002478262187e-05, 7.292752730092555424e-06, 7.292752730092555424e-06, -1.598199002478262187e-05,
                -1.598199002478262187e-05, -1.598199002478262187e-05, -1.598199002478262187e-05, 7.292752730092555424e-06, 7.292752730092555424e-06, 7.292752730092555424e-06,
                -1.598199002478262187e-05, -1.598199002478262187e-05, 7.292752730092555424e-06, 7.292752730092555424e-06, 7.292752730092555424e-06, -1.598199002478262187e-05,
                7.292752730092555424e-06, 7.292752730092555424e-06, 7.292752730092555424e-06, 7.292752730092555424e-06, -1.598199002478262187e-05, 7.292752730092555424e-06,
                7.292752730092555424e-06, 7.292752730092555424e-06, -1.598199002489364418e-05, 7.292752730092555424e-06, 7.292752730092555424e-06, 7.292752730092555424e-06,
                7.292752730092555424e-06, 7.292752730092555424e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06, 7.292752729981533122e-06,
                7.292752729981533122e-06, -1.598199002500466648e-05, 7.292752729981533122e-06, -1.598199002500466648e-05, -1.598199002500466648e-05, 7.292752729981533122e-06, 
                7.292752729981533122e-06, 7.292752729981533122e-06};
        checkFValues(new int[] {0, 1}, frame, new String[]{"sepal_len", "sepal_wid"}, 4,  sharedTreeSubgraphs, params._learn_rate, expectedResult);
        
        expectedResult = new double[] {1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00,
                1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00,
                1.860171545473183308e+00, 1.860171545473183308e+00, -9.284181247164070561e-01, -9.284181247164070561e-01, 1.860171545473183308e+00, 1.860171545473183308e+00,
                1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00, -9.284181247164070561e-01, -9.284181247164070561e-01, 1.860171545473183308e+00,
                1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00, -9.284181247164070561e-01, 1.860171545473183308e+00,
                1.860171545473183308e+00, -9.284181247164070561e-01, 1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00, 1.860171545473183308e+00,
                -9.284181247164070561e-01, 1.860171545473183308e+00, 1.860171545473183308e+00, -9.284181247164070561e-01, -9.284181247164070561e-01, -9.284181247164070561e-01,
                -9.284181247164070561e-01, -9.284181247164070561e-01, -9.284181247164070561e-01, -9.284181247164070561e-01, -9.284181247164070561e-01, -9.284181247164070561e-01,
                -9.284181247164070561e-01, 1.860171545473183308e+00, 1.860171545473183308e+00, -9.284181247164070561e-01, -9.284181247164070561e-01, -9.284181247164070561e-01,
                -9.284181247164070561e-01, -9.350317669621378558e-01, 1.860171545473183308e+00, -9.284181247164066120e-01, -9.284181247164066120e-01, -9.284181247164066120e-01,
                -9.350317669621378558e-01, -9.284181247164066120e-01, -9.284181247164066120e-01, -9.350317669621378558e-01, -9.284181247164066120e-01, -9.284181247164066120e-01,
                -9.284181247164066120e-01, -9.350317669621378558e-01, -9.350317669621378558e-01, -9.284181247164066120e-01, -9.284181247164066120e-01, -9.284181247164066120e-01, 
                -9.284181247164066120e-01, -9.309887451596333685e-01, -9.284181247164066120e-01, -9.284181247164066120e-01, -9.284181247164066120e-01, -9.309887451596333685e-01,
                -9.284181247164066120e-01, -9.284181247164066120e-01, -9.284181247164066120e-01, -9.350317669621378558e-01, -9.350317669621378558e-01, -9.309887451596333685e-01,
                -9.309887451596333685e-01, -9.284181247164066120e-01, -9.284181247164066120e-01, -9.309887451596333685e-01, -9.309887451596333685e-01, -9.309887451596333685e-01,
                -9.284181247164066120e-01, -9.350317669621378558e-01, -9.309887451596333685e-01, -9.309887451596333685e-01, -9.309887451596333685e-01, -9.284181247164066120e-01,
                -9.284181247164066120e-01, -9.284181247164066120e-01, -9.284181247164066120e-01, -9.350317669621378558e-01, -9.309887451596333685e-01, -9.309887451596333685e-01,
                -9.309887451596333685e-01, -9.309887451596333685e-01, -9.284181247164066120e-01, -9.309887451596335906e-01, -9.309887451596335906e-01, -9.284181247164066120e-01,
                -9.350317669621380778e-01, -9.309887451596335906e-01, -9.309887451596335906e-01, -9.284181247164066120e-01, -9.309887451596335906e-01, -9.309887451596335906e-01,
                -9.309887451596335906e-01, -9.309887451596335906e-01, -9.309887451596335906e-01, -9.309887451596335906e-01, -9.309887451596335906e-01, -9.309887451596335906e-01,
                -9.309887451596335906e-01, -9.309887451596335906e-01, -9.309887451596335906e-01};
        checkFValues(new int[] {0, 2}, frame, new String[]{"sepal_len", "petal_len"}, 4, sharedTreeSubgraphs, params._learn_rate, expectedResult);
        
        expectedResult = new double[] {-9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.350379279234550811e-01, 1.860165384511866193e+00,
                -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01,
                -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.350379279234550811e-01,
                -9.310462474652629883e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.310462474652629883e-01, -9.310462474652629883e-01,
                -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.350379279234550811e-01, -9.310462474652629883e-01,
                -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01,
                -9.284242856777238373e-01, -9.350379279234550811e-01, -9.310462474652629883e-01, -9.310462474652629883e-01, -9.310462474652629883e-01, 1.860165384511866193e+00,
                -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01,
                -9.309435647766381994e-01, -9.309435647766381994e-01, 1.860165384511866193e+00, 1.860165384511866193e+00, 1.860165384511866193e+00, 1.860165384511866193e+00,
                -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01, -9.284242856777238373e-01,
                -9.284242856777238373e-01, -9.350379279234550811e-01, -9.350379279234550811e-01, -9.309435647766381994e-01, -9.309435647766381994e-01, -9.309435647766381994e-01,
                -9.309435647766381994e-01, -9.309435647766381994e-01, -9.309435647766381994e-01, 1.860165384511866193e+00, 1.860165384511866193e+00, -9.284242856777238373e-01,
                -9.284242856777238373e-01, -9.284242856777238373e-01, -9.350379279234550811e-01, -9.309435647766381994e-01, -9.309435647766381994e-01, -9.309435647766381994e-01,
                1.860165384511866193e+00, 1.860165384511866193e+00, 1.860165384511866193e+00, 1.860165384511866193e+00, -9.284242856777238373e-01, -9.284242856777238373e-01,
                -9.284242856777238373e-01, -9.350379279234550811e-01, -9.309435647766381994e-01, -9.309435647766381994e-01, -9.309435647766381994e-01,-9.309435647766381994e-01,
                1.860165384511866193e+00, 1.860165384511866193e+00, -9.284242856777238373e-01, -9.309435647766381994e-01, -9.309435647766381994e-01, 1.860165384511866193e+00,
                1.860165384511866193e+00, 1.860165384511866193e+00, 1.860165384511866193e+00, 1.860165384511866193e+00, -9.284242856777238373e-01, -9.309435647766381994e-01,
                -9.309435647766381994e-01, 1.860165384511866193e+00, 1.860165384511866193e+00, 1.860165384511866193e+00, 1.860165384511866193e+00, 1.860165384511866193e+00,
                1.860165384511866193e+00, -9.309435647766381994e-01, 1.860165384511866193e+00, 1.860165384511866193e+00, 1.860165384511866193e+00, 1.860165384511866193e+00,
                1.860165384511866193e+00, -9.309435647766381994e-01, -9.309435647766381994e-01, 1.860165384511866193e+00, 1.860165384511866193e+00, 1.860165384511866193e+00,
                1.860165384511866193e+00, 1.860165384511866193e+00, 1.860165384511866193e+00};
        checkFValues(new int[] {1, 2}, frame, new String[]{"sepal_wid", "petal_len"}, 4, sharedTreeSubgraphs, params._learn_rate, expectedResult);
        
        expectedResult = new double[] {3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00,
                3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00,
                3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, -1.928459326023855613e+00,
                -1.928141487366395790e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, -1.928459326023855613e+00, -1.928459326023855613e+00, 3.860171545473182864e+00,
                3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00,
                3.860171545473182864e+00, -1.928459326023855613e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00,
                3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, -1.928459326023855613e+00, 3.860171545473182864e+00, 
                3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, -1.928459326023855613e+00, 3.860171545473182864e+00, 3.860171545473182864e+00,
                3.860171545473182864e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, -1.928459326023855613e+00, -1.928459326023855613e+00, -1.928459326023855613e+00,
                -1.928459326023855613e+00, -1.928459326023855613e+00, 3.860171545473182864e+00, 3.860171545473182864e+00, -1.928459326023855613e+00, -1.928459326023855613e+00,
                -1.928141487366395790e+00, -1.928459326023855613e+00, -1.928459326023855613e+00, -1.928459326023855613e+00, -1.929110513867624555e+00, -1.928459326023855613e+00,
                -1.928459326023855613e+00, -1.928459326023855613e+00, -1.928459326023855613e+00, -1.928459326023855613e+00, 3.860171545473182864e+00, 3.860171545473182864e+00,
                -1.928459326023855169e+00, -1.928459326023855169e+00, -1.928459326023855169e+00, -1.929110513867624555e+00, -1.929110513867624555e+00, 3.860171545473182864e+00,
                -1.928459326023855169e+00, -1.929110513867624555e+00, -1.928141487366394902e+00, -1.928459326023855169e+00, -1.954965403175556560e+00, -1.954965403175556560e+00,
                -1.928459326023855169e+00, -1.928141487366394902e+00, -1.928459326023855169e+00, -1.944706711086781059e+00, -1.928459326023855169e+00, -1.928459326023855169e+00,
                -1.928459326023855169e+00, -1.928459326023855169e+00, -1.928141487366394902e+00, -1.928459326023855169e+00, -1.928141487366394902e+00, -1.928459326023855169e+00,
                -1.928199081351590127e+00, -1.928459326023855169e+00, -1.928459326023855169e+00, -1.929110513867624555e+00, -1.928141487366394902e+00, -1.954965403175556560e+00,
                -1.928199081351590127e+00, -1.928459326023855169e+00, -1.928199081351590127e+00, -1.928199081351590127e+00, -1.928199081351590127e+00, -1.928199081351590127e+00,
                -1.928199081351590127e+00, -1.928459326023855169e+00, -1.928199081351590127e+00, -1.928459326023855169e+00, -1.928199081351590127e+00, -1.928459326023855169e+00,
                -1.928199081351590127e+00, -1.928199081351590127e+00, -1.928199081351590127e+00, -1.929110513867624555e+00, -1.928459326023855169e+00, -1.928459326023855169e+00,
                -1.928199081351590127e+00, -1.986179798787694573e+00, -1.928199081351590127e+00, -1.928459326023855169e+00, -1.928459326023855169e+00, -1.928199081351590127e+00,
                -1.928199081351590127e+00, -1.928199081351590127e+00, -1.928459326023855169e+00, -1.928199081351590571e+00, -1.928199081351590571e+00, -1.928459326023855169e+00,
                -1.929110513867624555e+00, -1.928199081351590571e+00, -1.928199081351590571e+00, -1.928459326023855169e+00, -1.928199081351590571e+00, -1.942961105380160980e+00,
                -1.928199081351590571e+00, -1.928199081351590571e+00, -1.928199081351590571e+00, -1.928199081351590571e+00, -1.928199081351590571e+00, -1.928199081351590571e+00,
                -1.928199081351590571e+00, -1.928199081351590571e+00, -1.928199081351590571e+00, -1.928199081351590571e+00};
        checkFValues(new int[] {0, 1, 2, 3}, frame, new String[]{"sepal_len","sepal_wid","petal_len","petal_wid"}, 6, sharedTreeSubgraphs, params._learn_rate, expectedResult);
        
        double h = FriedmanPopescusH.h(frame, new String[] {"sepal_len","sepal_wid","petal_len","petal_wid"}, params._learn_rate, sharedTreeSubgraphs);
        assertEquals(1.7358501914626407e-16, h , 1e-15);
        h = FriedmanPopescusH.h(frame, new String[] {"sepal_len","sepal_wid","petal_len"}, params._learn_rate, sharedTreeSubgraphs);
        assertEquals(2.6762600878064094e-16, h , 1e-15);
        h = FriedmanPopescusH.h(frame, new String[] {"sepal_len","sepal_wid"}, params._learn_rate, sharedTreeSubgraphs);
        assertEquals(6.8214295179305406e-12, h , 1e-11);
        h = FriedmanPopescusH.h(frame, new String[] {"sepal_wid","petal_len"}, params._learn_rate, sharedTreeSubgraphs);
        assertEquals(1.6283638520340696e-05, h , 1e-4);
        h = FriedmanPopescusH.h(frame, new String[] {"petal_len","sepal_wid"}, params._learn_rate, sharedTreeSubgraphs);
        assertEquals(1.62836385203624e-05, h , 1e-4);
    }
    
    @Test
    public void testCombinations() {
        int[] intArray = {0,1,2,3,4,5,6};
        List<int[]> combinations = FriedmanPopescusH.combinations(intArray, 3);
        assertEquals(combinations.size(), 35);
    }
}
