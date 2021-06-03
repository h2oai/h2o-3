package hex.tree.gbm;


import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static water.TestUtil.parseTestFile;
import static water.TestUtil.stall_till_cloudsize;

@RunWith(MockitoJUnitRunner.class)
public class FriedmanPopescusHTest {

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
        node16.setWeight((float)20/150);
        node16.setGain(1);
        node16.setPredValue(0.733049424490485f);

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
        SharedTreeSubgraph sharedTreeSubgraph0k0Tree = createStage0k0Tree(sharedTreeGraph);
        SharedTreeSubgraph sharedTreeSubgraph0k1Tree = createStage0k1Tree(sharedTreeGraph);
        SharedTreeSubgraph sharedTreeSubgraph0k2Tree = createStage0k2Tree(sharedTreeGraph);
        SharedTreeSubgraph sharedTreeSubgraph1k0Tree = createStage1k0Tree(sharedTreeGraph);
        SharedTreeSubgraph sharedTreeSubgraph1k1Tree = createStage1k1Tree(sharedTreeGraph);
        SharedTreeSubgraph sharedTreeSubgraph1k2Tree = createStage1k2Tree(sharedTreeGraph);
        SharedTreeSubgraph sharedTreeSubgraph2k0Tree = createStage2k0Tree(sharedTreeGraph);
        SharedTreeSubgraph sharedTreeSubgraph2k1Tree = createStage2k1Tree(sharedTreeGraph);
        SharedTreeSubgraph sharedTreeSubgraph2k2Tree = createStage2k2Tree(sharedTreeGraph);

        return sharedTreeGraph;
    }

    
    
    @Test
    public void testPartialDependenceTree() throws IOException {
        String currentPath = new java.io.File(".").getCanonicalPath();
        Frame frame = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/grid.csv");
        Frame res = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/result012.csv");

        SharedTreeGraph tree = createSharedTreeGraphForTest();
        
        double result[] = FriedmanPopescusH.partialDependenceTree(tree.subgraphArray.get(0), new Integer[] {0,1,2}, 0.1, frame);
        assertEquals(result.length, res.numRows());
        for (int i = 0; i < res.numRows(); i++) {
            assertEquals(res.vec(0).at(i), result[i], 1e-4);
        }
        
        res = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/result02.csv");
        Frame frame1 = new Frame();
        frame1.add(new String[] {"feature0", "feature2"}, new Vec[] {frame.vec(0), frame.vec(2)});
        result = FriedmanPopescusH.partialDependenceTree(tree.subgraphArray.get(0), new Integer[] {0,2}, 0.1, frame1);
        assertEquals(result.length, res.numRows());
        for (int i = 0; i < res.numRows(); i++) {
            assertEquals(res.vec(0).at(i), result[i], 1e-4);
        }

        res = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/uncentd_f_vals_01.csv");
        Frame frame2 = new Frame();
        frame2.add(new String[] {"feature0", "feature1"}, new Vec[] {frame.vec(0), frame.vec(1)});
        result = FriedmanPopescusH.partialDependenceTree(tree.subgraphArray.get(0), new Integer[] {0,1}, 0.1, frame2);
        assertEquals(result.length, res.numRows());
        for (int i = 0; i < res.numRows(); i++) {
            assertEquals(res.vec(0).at(i), result[i], 1e-4);
        }
    }
    
    @Test 
    public void testUniqueRowsWithCounts() {
        String[] strings0 = new String[] { "a", "a", "b", "a", "c", "b" };
        String[] strings1 = new String[] { "z", "z", "y", "z", "w", "y" };
        long[] nums0 = new long[] { 0, 0, 1, 2, 1, 1 };
        long[] nums1 = new long[] { 2, 2, 3, 2, 4, 3 };

        Frame frame = new TestFrameBuilder()
                .withName("frameName")
                .withColNames("Str_0", "Str_1", "Num_0", "Num_1")
                .withVecTypes(Vec.T_STR, Vec.T_STR, Vec.T_NUM, Vec.T_NUM)
                .withDataForCol(0, strings0)
                .withDataForCol(1, strings1)
                .withDataForCol(2, nums0)
                .withDataForCol(3, nums1)
                .withChunkLayout(1, 1, 2, 1)
                .build();
        DKV.put(frame);

        Frame result = FriedmanPopescusH.uniqueRowsWithCounts(frame);
        assertEquals(result.numRows(), frame.numRows() - 1);
        DKV.remove(frame._key);
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
    * 
    * */
    @Test
    public void testRegression() throws IOException {
        String currentPath = new java.io.File(".").getCanonicalPath();
        Frame frame = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/grid.csv");
        DKV.put(frame);

        SharedTreeGraph tree = createSharedTreeGraphForTest();

        when(mockModel2.getSharedTreeSubgraph(0,0)).thenReturn(tree.subgraphArray.get(0));

        GBMModel.GBMParameters params = new GBMModel.GBMParameters();
        params._learn_rate = 0.1;
        params._ntrees = 1;
        mockModel2._parms = params;

        GBMModel.GBMOutput output = new GBMModel.GBMOutput(new GBM(params));
        output._nclasses = 1;
        mockModel2._output = output;

        checkFValues(mockModel2, new Integer[] {0,1}, frame, new String[]{"feature0", "feature1"}, currentPath + "/src/test/java/hex/tree/gbm/f_vals_01.csv", 4);
        checkFValues(mockModel2, new Integer[] {1,2}, frame, new String[]{"feature1", "feature2"}, currentPath + "/src/test/java/hex/tree/gbm/f_vals_inds_12.csv", 4);
        checkFValues(mockModel2, new Integer[] {0,1,2}, frame, frame.names(), currentPath + "/src/test/java/hex/tree/gbm/Fvals012result.csv", 5);
        checkFValues(mockModel2, new Integer[] {1}, frame, new String[]{"feature1"}, currentPath + "/src/test/java/hex/tree/gbm/f_vals_inds_1.csv", 3);

        double h = FriedmanPopescusH.h(frame, new String[] {"feature0","feature1","feature2"}, mockModel2);
        assertEquals(h, 0.08603547, 1e-7);
        
        DKV.remove(frame._key);
    }
    
    void checkFValues(GBMModel model, Integer[] modelIds, Frame filteredFrame, String[] cols, String pathToResult, int expectedNumCols) {
        Frame res = FriedmanPopescusH.computeFValues(model, modelIds, filteredFrame, cols);
        assertEquals(res.numCols(),expectedNumCols);
        Frame pyres = parseTestFile(pathToResult);
        assertEquals(res.numRows(), pyres.numRows());
        for (int i=0; i < res.numRows(); i++) {
            assertEquals(res.vec(0).at(i), pyres.vec(0).at(i), 1e-4);
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
    
    @Test
    public void testClassification() throws IOException {
        String currentPath = new java.io.File(".").getCanonicalPath();
        Frame frame = parseTestFile( "smalldata/iris/iris_wheader.csv");
        DKV.put(frame);
        SharedTreeGraph tree = createSharedTreeGraphForTestMLT();
        
        Frame filtered = FriedmanPopescusH.filterFrame(frame, new String[] {"sepal_len"});
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

        GBMModel.GBMOutput output = new GBMModel.GBMOutput(new GBM(params));
        output._nclasses = 3;
        mockModel._output = output;

        checkFValues(mockModel, new Integer[] {0}, frame, new String[]{"sepal_len"}, currentPath + "/src/test/java/hex/tree/gbm/fvals_inds_0_multinomial.csv", 3);
        checkFValues(mockModel, new Integer[] {1}, frame, new String[]{"sepal_wid"}, currentPath + "/src/test/java/hex/tree/gbm/fvals_inds_1_multinomial.csv", 3);
        checkFValues(mockModel, new Integer[] {2}, frame, new String[]{"petal_len"}, currentPath + "/src/test/java/hex/tree/gbm/fvals_inds_2_multinomial.csv", 3);
        checkFValues(mockModel, new Integer[] {3}, frame, new String[]{"petal_wid"}, currentPath + "/src/test/java/hex/tree/gbm/fvals_inds_3_multinomial.csv", 3);
        checkFValues(mockModel, new Integer[] {0, 1}, frame, new String[]{"sepal_len", "sepal_wid"}, currentPath + "/src/test/java/hex/tree/gbm/fvals_inds_01_multinomial.csv", 4);
        checkFValues(mockModel, new Integer[] {0, 2}, frame, new String[]{"sepal_len", "petal_len"}, currentPath + "/src/test/java/hex/tree/gbm/fvals_inds_02_multinomial.csv", 4);
        checkFValues(mockModel, new Integer[] {1, 2}, frame, new String[]{"sepal_wid", "petal_len"}, currentPath + "/src/test/java/hex/tree/gbm/fvals_inds_12_multinomial.csv", 4);
        checkFValues(mockModel, new Integer[]  {0, 1, 2, 3}, frame, new String[]{"sepal_len","sepal_wid","petal_len","petal_wid"}, currentPath + "/src/test/java/hex/tree/gbm/fvals_inds_0123_multinomial.csv", 6);
        
        double h = FriedmanPopescusH.h(frame, new String[] {"sepal_len","sepal_wid","petal_len","petal_wid"}, mockModel);
        assertEquals(1.7358501914626407e-16, h , 1e-4);
        DKV.remove(frame._key);
    }
    
    @Test
    public void testCombinations() {
        int[] intArray = {0,1,2,3,4,5,6};
        List<int[]> combinations = FriedmanPopescusH.combinations(intArray, 3);
        assertEquals(combinations.size(), 35);
    }
}
