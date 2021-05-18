package hex.tree.gbm;


import hex.genmodel.algos.tree.SharedTreeGraph;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static water.TestUtil.parseTestFile;
import static water.TestUtil.stall_till_cloudsize;

public class FriedmanPopescusHTest {

    @BeforeClass
    public static void stall() { stall_till_cloudsize(1); }

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
    
    @Test
    public void testComputeFvalsInner() throws IOException {
        String currentPath = new java.io.File(".").getCanonicalPath();
        Frame frame = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/grid.csv");
        DKV.put(frame);
        SharedTreeGraph tree = createSharedTreeGraphForTest();

        Frame res = FriedmanPopescusH.computeFValuesForTest(tree.subgraphArray.get(0), new Integer[] {0,1,2}, frame, frame.names(), 0.1);
        assertEquals(res.numCols(),1);
        Frame pyres = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/Fvals012result.csv");
        assertEquals(res.numRows(), pyres.numRows());
        for (int i=0; i < res.numRows(); i++) {
            assertEquals(res.vec(0).at(i), pyres.vec(0).at(i), 1e-4);
        }
        DKV.remove(frame._key);
    }

    @Test
    public void testComputeFvalsInner01() throws IOException {
        String currentPath = new java.io.File(".").getCanonicalPath();
        Frame frame = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/grid.csv");
        DKV.put(frame);
        SharedTreeGraph tree = createSharedTreeGraphForTest();

        Frame res = FriedmanPopescusH.computeFValuesForTest(tree.subgraphArray.get(0), new Integer[] {0,1}, frame, new String[]{"feature0", "feature1"}, 0.1);
        assertEquals(res.numCols(),1);
        Frame pyres = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/f_vals_01.csv");
        assertEquals(res.numRows(), pyres.numRows());
        for (int i=0; i < res.numRows(); i++) {
            assertEquals(res.vec(0).at(i), pyres.vec(0).at(i), 1e-4);
        }
        DKV.remove(frame._key);
    }

    @Test
    public void testComputeFvalsInner12() throws IOException {
        String currentPath = new java.io.File(".").getCanonicalPath();
        Frame frame = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/grid.csv");
        DKV.put(frame);
        SharedTreeGraph tree = createSharedTreeGraphForTest();

        Frame res = FriedmanPopescusH.computeFValuesForTest(tree.subgraphArray.get(0), new Integer[] {1,2}, frame,new String[]{"feature1", "feature2"}, 0.1);
        assertEquals(res.numCols(),1);
        Frame pyres = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/f_vals_inds_12.csv");;
        assertEquals(res.numRows(), pyres.numRows());
        for (int i=0; i < res.numRows(); i++) {
            assertEquals(res.vec(0).at(i), pyres.vec(0).at(i), 1e-4);
        }
        DKV.remove(frame._key);
    }


    @Test
    public void testComputeFvalsInner1() throws IOException {
        String currentPath = new java.io.File(".").getCanonicalPath();
        Frame frame = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/grid.csv");
        DKV.put(frame);
        SharedTreeGraph tree = createSharedTreeGraphForTest();

        Frame res = FriedmanPopescusH.computeFValuesForTest(tree.subgraphArray.get(0), new Integer[] {1}, frame,new String[]{"feature1"}, 0.1);
        assertEquals(res.numCols(),1);
        Frame pyres = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/f_vals_inds_1.csv");
        assertEquals(res.numRows(), pyres.numRows());
        for (int i=0; i < res.numRows(); i++) {
            assertEquals(res.vec(0).at(i), pyres.vec(0).at(i), 1e-4);
        }
        DKV.remove(frame._key);
    }
    
    @Test
    public void testMeanCalculation() throws IOException {
        String currentPath = new java.io.File(".").getCanonicalPath();
        Frame count = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/counts01.csv");
        Frame fvals = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/fvals_uncentered01.csv");
        float[][] counts = FriedmanPopescusH.FrameTo2DArr(new Frame(count.vec(0)), true);
        float[][] fValues = FriedmanPopescusH.FrameTo2DArr(fvals, false);
        float[][] res =  FriedmanPopescusH.matrixMultiply(counts, fValues);
        FriedmanPopescusH.matrixScalarDivision(res, 90);
        
        count = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/counts12.csv");
        fvals = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/fvals_uncentered12.csv");
        counts = FriedmanPopescusH.FrameTo2DArr(new Frame(count.vec(0)), true);
        fValues = FriedmanPopescusH.FrameTo2DArr(fvals, false);
        res =  FriedmanPopescusH.matrixMultiply(counts, fValues);
        FriedmanPopescusH.matrixScalarDivision(res, 90);
    }
    
    
    @Test public void testComputeHvalInner() throws IOException {
        String currentPath = new java.io.File(".").getCanonicalPath();
        Frame frame = parseTestFile(currentPath + "/src/test/java/hex/tree/gbm/grid.csv");
        DKV.put(frame);
        SharedTreeGraph tree = createSharedTreeGraphForTest();
        double h = FriedmanPopescusH.h_test(frame, new String[] {"feature0","feature1","feature2"}, tree.subgraphArray.get(0));
         assertEquals(h, 0.08603547, 1e-7);
    }

    @Test
    public void testCombinations() {
        int[] intArray = {0,1,2,3,4,5,6};
        List<int[]> combinations = FriedmanPopescusH.combinations(intArray, 3);
        assertEquals(combinations.size(), 35);
    }
    
    
    

    
}

/*
* tree and data for this test can be generated like this:
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
