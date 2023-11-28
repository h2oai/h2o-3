package hex.tree;

import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;

import hex.glm.GLMModel;
import hex.schemas.TreeV3;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import hex.tree.isofor.IsolationForest;
import hex.tree.isofor.IsolationForestModel;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.*;
import java.util.regex.Pattern;

import static hex.genmodel.utils.DistributionFamily.AUTO;
import static org.junit.Assert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class TreeHandlerTest {

    @Test
    public void testSharedTreeSubgraphConversion() {
        Frame tfr = null;
        GBMModel model = null;

        Scope.enter();
        try {
            tfr = parseTestFile("./smalldata/airlines/allyears2k_headers.zip");
            DKV.put(tfr);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = tfr._key;
            parms._response_column = "Dest";
            parms._ntrees = 1;
            parms._seed = 0;

            model = new GBM(parms).trainModel().get();
            final SharedTreeModel.SharedTreeOutput sharedTreeOutput = model._output;
            assertEquals(parms._ntrees, sharedTreeOutput._ntrees);
            assertEquals(parms._ntrees, sharedTreeOutput._treeKeys.length);
            assertEquals(parms._ntrees, sharedTreeOutput._treeKeysAux.length);

            final int treeIndex = 0; //Not a tree number, numbered from 0
            final int treeClass = 0;
            final CompressedTree auxCompressedTree = sharedTreeOutput._treeKeysAux[treeIndex][treeClass].get();
            final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeOutput._treeKeys[treeIndex][treeClass].get()
                    .toSharedTreeSubgraph(auxCompressedTree, sharedTreeOutput._names, sharedTreeOutput._domains);

            assertNotNull(sharedTreeSubgraph);

            final TreeHandler.TreeProperties treeProperties = TreeHandler.convertSharedTreeSubgraph(sharedTreeSubgraph, TreeHandler.PlainLanguageRules.FALSE);
            assertNotNull(treeProperties);
            assertEquals(sharedTreeSubgraph.nodesArray.size(), treeProperties._descriptions.length);
            assertEquals(sharedTreeSubgraph.nodesArray.size(), treeProperties._thresholds.length);
            assertEquals(sharedTreeSubgraph.nodesArray.size(), treeProperties._features.length);
            assertEquals(sharedTreeSubgraph.nodesArray.size(), treeProperties._nas.length);
            final Pattern rootNodeSplitColPattern = Pattern.compile(".* and splits on column '.+'.*");
            assertTrue(rootNodeSplitColPattern.matcher(treeProperties._descriptions[0]).matches());

            final int[] leftChildren = treeProperties._leftChildren;
            final int[] rightChildren = treeProperties._rightChildren;
            assertEquals(leftChildren.length, rightChildren.length);

            final SharedTreeNode rootNode = sharedTreeSubgraph.rootNode;
            final Deque<SharedTreeNode> discoverednodes = new ArrayDeque<>();
            discoverednodes.push(rootNode);
            int nonRootNodesFound = 0;
            for (int i = 0; i < leftChildren.length; i++) {
                final SharedTreeNode sharedTreeNode = discoverednodes.pollLast();
                final SharedTreeNode leftChild = sharedTreeNode.getLeftChild();
                final SharedTreeNode rightChild = sharedTreeNode.getRightChild();

                if (leftChildren[i] != -1) {
                    assertEquals(leftChildren[i], leftChild.getNodeNumber());
                    discoverednodes.push(sharedTreeNode.getLeftChild());
                    nonRootNodesFound++;
                }
                if (rightChildren[i] != -1) {
                    assertEquals(rightChildren[i], rightChild.getNodeNumber());
                    discoverednodes.push(sharedTreeNode.getRightChild());
                    nonRootNodesFound++;
                }
            }

            assertEquals(sharedTreeSubgraph.nodesArray.size(), nonRootNodesFound + 1); // +1 for the root node that is not represented explicitely

        } finally {
            Scope.exit();
            if (tfr != null) tfr.remove();
            if (model != null) model.remove();
        }
    }

    @Test
    public void testSharedTreeSubgraphConversion_inclusiveLevelsIris() {

        Frame tfr = null;
        GBMModel model = null;

        Scope.enter();
        try {
            tfr = parseTestFile("./smalldata/iris/iris2.csv");
            DKV.put(tfr);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = tfr._key;
            parms._response_column = "response";
            parms._ntrees = 1;
            parms._seed = 0;

            model = new GBM(parms).trainModel().get();
            final SharedTreeModel.SharedTreeOutput sharedTreeOutput = model._output;
            assertEquals(parms._ntrees, sharedTreeOutput._ntrees);
            assertEquals(parms._ntrees, sharedTreeOutput._treeKeys.length);
            assertEquals(parms._ntrees, sharedTreeOutput._treeKeysAux.length);

            final int treeNumber = 0;
            final int treeClass = 0;
            final CompressedTree auxCompressedTree = sharedTreeOutput._treeKeysAux[treeNumber][treeClass].get();
            final SharedTreeSubgraph sharedTreeSubgraph = sharedTreeOutput._treeKeys[treeNumber][treeClass].get()
                    .toSharedTreeSubgraph(auxCompressedTree, sharedTreeOutput._names, sharedTreeOutput._domains);

            assertNotNull(sharedTreeSubgraph);

            final TreeHandler.TreeProperties treeProperties = TreeHandler.convertSharedTreeSubgraph(sharedTreeSubgraph, TreeHandler.PlainLanguageRules.FALSE);
            assertNotNull(treeProperties);

            final String[] nodeDescriptions = treeProperties._descriptions;
            assertEquals(sharedTreeSubgraph.nodesArray.size(), nodeDescriptions.length);

            for (String nodeDescription : nodeDescriptions) {
                assertFalse(nodeDescription.isEmpty());
            }

        } finally {
            if (tfr != null) tfr.remove();
            if (model != null) model.remove();
        }

    }

    @Test
    public void testSharedTreeSubgraphConversion_argumentValidationMultinomial() {

        Frame tfr = null;
        GBMModel model = null;
        GLMModel nonTreeBasedModel = null;

        Scope.enter();
        try {
            tfr = parseTestFile("./smalldata/iris/iris2.csv");
            DKV.put(tfr);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = tfr._key;
            parms._response_column = "response";
            parms._ntrees = 1;
            parms._seed = 0;

            model = new GBM(parms).trainModel().get();

            final TreeHandler treeHandler = new TreeHandler();
            final TreeV3 args = new TreeV3();
            args.model = new KeyV3.ModelKeyV3(Key.make());

            //Non-existing key

            boolean exceptionThrown = false;
            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("Given model does not exist"));
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);
            exceptionThrown = false;

            nonTreeBasedModel = new GLMModel(Key.make(), new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial),
                    null, null, 1, 1,1);
            DKV.put(nonTreeBasedModel);
            args.model = new KeyV3.ModelKeyV3(nonTreeBasedModel._key);
            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("Given model is not tree-based."));
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);
            exceptionThrown = false;

            // Invalid tree index
            args.tree_number = 1;
            args.tree_class = tfr.vec(parms._response_column).domain()[0];
            args.model = new KeyV3.ModelKeyV3(model._key);
            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e) {
                assertEquals("Invalid tree index: 1. Tree index must be in range [0, 0].", e.getMessage());
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);
            exceptionThrown = false;

            // Invalid tree class
            args.tree_number = 0;
            args.tree_class = "NonExistingCategoricalLevel";

            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("There is no such tree class. Given categorical level does not exist in response column: NonExistingCategoricalLevel"));
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);
            exceptionThrown = false;

            //  Tree number < 0
            args.tree_number = -1;

            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e){
                assertTrue(e.getMessage().contains("Invalid tree number: " + args.tree_number + ". Tree number must be >= 0."));
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);
        } finally {
            Scope.exit();
            if (tfr != null) tfr.remove();
            if (model != null) model.remove();
            if(nonTreeBasedModel != null) nonTreeBasedModel.remove();
        }
    }

    @Test
    public void testSharedTreeSubgraphConversion_argumentValidationRegression() {


        Frame tfr = null;
        GBMModel regressionModel = null;

        Scope.enter();
        try {
            tfr = parseTestFile("./smalldata/iris/iris2.csv");
            DKV.put(tfr);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = tfr._key;
            parms._ntrees = 1;
            parms._seed = 0;
            parms._response_column = "Sepal.Length";

            final TreeV3 args = new TreeV3();
            regressionModel = new GBM(parms).trainModel().get();
            args.model = new KeyV3.ModelKeyV3(regressionModel._key);
            args.tree_class = "NonExistingClass";


            final TreeHandler treeHandler = new TreeHandler();
            boolean exceptionThrown = false;
            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("There are no tree classes for Regression."));
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);


        } finally {
            if (tfr != null) tfr.remove();
            if (regressionModel != null) regressionModel.remove();
            Scope.exit();
        }
    }

    @Test
    public void testSharedTreeSubgraphConversion_argumentValidationBinomial() {


        Frame tfr = null;
        GBMModel model = null;

        Scope.enter();
        try {
            tfr = parseTestFile("./smalldata/testng/airlines_train.csv");
            DKV.put(tfr);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = tfr._key;
            parms._ntrees = 1;
            parms._seed = 0;
            parms._response_column = "IsDepDelayed";

            // Test incorrect tree request
            final TreeV3 args = new TreeV3();
            model = new GBM(parms).trainModel().get();
            args.model = new KeyV3.ModelKeyV3(model._key);
            args.tree_class = "YES";
            args.plain_language_rules = TreeHandler.PlainLanguageRules.FALSE;

            // If the tree class name is specified, it must be the tree class built exactly
            final TreeHandler treeHandler = new TreeHandler();
            boolean exceptionThrown = false;
            try {
                treeHandler.getTree(3, args);
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage().contains("For binomial, only one tree class has been built per each iteration: NO"));
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);

            args.tree_class = "NO";
            final TreeV3 correctlySpecifiedClassTree = treeHandler.getTree(3, args);
            assertNotNull(correctlySpecifiedClassTree);

            args.tree_class = "";
            final TreeV3 noClassTree = treeHandler.getTree(3, args);
            assertNotNull(noClassTree);

        } finally {
            if (tfr != null) tfr.remove();
            if (model != null) model.remove();
            Scope.exit();
        }
    }

    @Test
    public void testNaHandling_airlines_train() {


        Frame tfr = null;
        GBMModel model = null;

        Scope.enter();
        try {
            tfr = parseTestFile("./smalldata/testng/airlines_train.csv");
            DKV.put(tfr);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = tfr._key;
            parms._ntrees = 1;
            parms._seed = 0;
            parms._response_column = "IsDepDelayed";

            // Test incorrect tree request
            model = new GBM(parms).trainModel().get();
            final SharedTreeSubgraph sharedTreeSubgraph = model.getSharedTreeSubgraph(0, 0);
            final TreeHandler.TreeProperties treeProperties = TreeHandler.convertSharedTreeSubgraph(sharedTreeSubgraph, TreeHandler.PlainLanguageRules.FALSE);
            assertNotNull(treeProperties);

            final SharedTreeNode rootNode = sharedTreeSubgraph.rootNode;

            final String[] noExcludedSplits = new String[]{};
            final int naSplits = checkNaPath(rootNode, noExcludedSplits);

            //Count number of nonNull NA splits generate by TreeHandler.convertSharedTreeSubgraph and compare with number
            // of NA splits detected while walking the tree. Must be the same number.
            int nonNullNaSplits = 0;

            for (String naSplitDescription: treeProperties._nas){
                if(naSplitDescription != null) nonNullNaSplits++;
            }

            assertEquals(naSplits, nonNullNaSplits);


        } finally {
            if (tfr != null) tfr.remove();
            if (model != null) model.remove();
            Scope.exit();
        }
    }

    @Test
    public void testNaHandling_cars() {

        Frame tfr = null;
        GBMModel model = null;

        Scope.enter();
        try {
            tfr = parseTestFile("./smalldata/junit/cars_nice_header.csv");
            DKV.put(tfr);
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = tfr._key;
            parms._ignored_columns = new String[]{"name", "economy", "displacement", "weight", "acceleration", "year"}; // Cylinders-only
            parms._response_column = "power"; //Regression
            parms._ntrees = 1;
            parms._seed = 0;

            model = new GBM(parms).trainModel().get();
            final SharedTreeModel.SharedTreeOutput sharedTreeOutput = model._output;
            assertEquals(parms._ntrees, sharedTreeOutput._ntrees);
            assertEquals(parms._ntrees, sharedTreeOutput._treeKeys.length);
            assertEquals(parms._ntrees, sharedTreeOutput._treeKeysAux.length);

            final SharedTreeSubgraph sharedTreeSubgraph = model.getSharedTreeSubgraph(0, 0);
            assertNotNull(sharedTreeSubgraph);
            final TreeHandler.TreeProperties treeProperties = TreeHandler.convertSharedTreeSubgraph(sharedTreeSubgraph, TreeHandler.PlainLanguageRules.FALSE);
            assertNotNull(treeProperties);

            final SharedTreeNode rootNode = sharedTreeSubgraph.rootNode;
            final String[] noExcludedSplits = new String[]{};
            final int naSplits = checkNaPath(rootNode, noExcludedSplits);

            //Count number of nonNull NA splits generate by TreeHandler.convertSharedTreeSubgraph and compare with number
            // of NA splits detected while walking the tree. Must be the same number.
            int nonNullNaSplits = 0;

            for (String naSplitDescription: treeProperties._nas){
                if(naSplitDescription != null) nonNullNaSplits++;
            }

            assertEquals(naSplits, nonNullNaSplits);

        } finally {
            if (tfr != null) tfr.remove();
            if (model != null) model.remove();
        }
    }

    private int checkNaPath(SharedTreeNode parentNode, String[] previousNaSplits) {
        final SharedTreeNode leftChild = parentNode.getLeftChild();
        final SharedTreeNode rightChild = parentNode.getRightChild();

        if (leftChild == null && rightChild == null) return 0;


        final boolean isRightInclusive = rightChild != null ? rightChild.isInclusiveNa() : false;
        final boolean isLeftInclusive = leftChild != null ? leftChild.isInclusiveNa() : false;

        if (!(isLeftInclusive ^ isRightInclusive) && isLeftInclusive) {
            fail("Parent node " + parentNode.getNodeNumber() + " includes NAs for both children");
        }

        final boolean splitsOnExcluded = ArrayUtils.contains(previousNaSplits, parentNode.getColName());
        if (splitsOnExcluded
                && (isRightInclusive || isLeftInclusive)) {
            // If NAs for given column went any other way before, there should be no NA direction for the column
            fail("Parent node " + parentNode.getNodeNumber() + " includes NAs for column " + parentNode.getColName());
        } else if (!splitsOnExcluded && !(isLeftInclusive ^ isRightInclusive)) {
            // If NAs for given column may appear on this node, it should point a way to one of its children
            fail("Parent node " + parentNode.getNodeNumber() + " should set NA direction to one of its children");
        }


        final String[] leftPreviousSplits = isRightInclusive ? ArrayUtils.append(previousNaSplits, parentNode.getColName()) : previousNaSplits;
        final String[] rightPreviousSplits = isLeftInclusive ? ArrayUtils.append(previousNaSplits, parentNode.getColName()) : previousNaSplits;

        int naSplits = 0;
        if (isRightInclusive ^ isLeftInclusive) {
            naSplits++;
        }

        if (leftChild != null) naSplits += checkNaPath(leftChild, leftPreviousSplits);
        if (rightChild != null) naSplits += checkNaPath(rightChild, rightPreviousSplits);

        return naSplits;
    }

    @Test
    public void testEmptyInheritedCategoricalLevels() {
        try {
            Scope.enter();
            final Frame trainingFrame = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(trainingFrame);
            IsolationForestModel.IsolationForestParameters parms = new IsolationForestModel.IsolationForestParameters();
            parms._train = trainingFrame._key;
            parms._distribution = AUTO;
            parms._response_column = "IsDepDelayed";
            parms._ntrees = 10;
            parms._max_depth = 10;
            parms._seed = 0XFEED;

            IsolationForest job = new IsolationForest(parms);
            IsolationForestModel model = job.trainModel().get();
            Scope.track_generic(model);

            final TreeHandler treeHandler = new TreeHandler();
            final TreeV3 arguments = new TreeV3();
            arguments.model = new KeyV3.ModelKeyV3(model._key);
            for (int i = 0; i < parms._ntrees; i++) {
                arguments.tree_number = i;
                arguments.plain_language_rules = TreeHandler.PlainLanguageRules.AUTO;
                final TreeV3 tree = treeHandler.getTree(3, arguments);
                assertNotNull(tree);
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testSlowTree() {
        try {
            Scope.enter();
            int[] responseData = ArrayUtils.seq(0, 2000);
            ArrayUtils.shuffleArray(responseData, new Random(42));
            String[] featureData = new String[responseData.length];
            for (int i = 0; i < featureData.length; i++) {
                featureData[i] = "A" + i;
            }

            Frame train = new TestFrameBuilder()
                    .withColNames("Feature", "Response")
                    .withVecTypes(Vec.T_CAT, Vec.T_NUM)
                    .withDataForCol(0, featureData)
                    .withDataForCol(1, responseData)
                    .build();

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = train._key;
            parms._distribution = AUTO;
            parms._response_column = "Response";
            parms._ntrees = 1;
            parms._max_depth = 30;
            parms._seed = 0XFEED;
            parms._min_split_improvement = 1e-6;
            parms._min_rows = 1;

            GBM job = new GBM(parms);
            GBMModel model = job.trainModel().get();
            Scope.track_generic(model);

            final TreeHandler treeHandler = new TreeHandler();

            // First try the default (tree is big - it should disable the rules)
            final TreeV3 arguments = new TreeV3();
            arguments.model = new KeyV3.ModelKeyV3(model._key);
            arguments.tree_number = 0;
            arguments.plain_language_rules = TreeHandler.PlainLanguageRules.AUTO;

            long startTimeAuto = System.currentTimeMillis();
            final TreeV3 treeAuto = treeHandler.getTree(3, arguments);
            assertNotNull(treeAuto);
            long durationAuto = System.currentTimeMillis() - startTimeAuto;

            Log.info("Tree rendering AUTO rules took " + durationAuto + "ms.");

            // Now try with rules explicitly disabled
            long startTimeDisabled = System.currentTimeMillis();
            final TreeV3 treeDisabled = treeHandler.getTree(3, arguments);
            assertNotNull(treeDisabled);
            long durationDisabled = System.currentTimeMillis() - startTimeDisabled;

            Log.info("Tree rendering WITHOUT rules took " + durationDisabled + "ms.");
            
            // Now try with language rules enabled
            arguments.plain_language_rules = TreeHandler.PlainLanguageRules.TRUE;
            long startTimeEnabled = System.currentTimeMillis();
            final TreeV3 treeEnabled = treeHandler.getTree(3, arguments);
            assertNotNull(treeEnabled);
            long durationEnabled = System.currentTimeMillis() - startTimeEnabled;

            Log.info("Tree rendering WITH rules took " + durationEnabled + "ms.");

            // If this assertion fails - we likely made it faster - that is great! but please do check
            int slowdownFactor = 10; // this is very conservative lower bound, actual measured slowdown is about 100x!!!
            assertThat("Wow, did we optimize tree rendering with language rules? It is now closer to rendering without rules!",
                    durationEnabled, greaterThan(durationDisabled * slowdownFactor));

            // duration disabled and "auto" should be similar in this case, 10x factor of tolerance because we are measuring in ms
            assertEquals("Duration is not the same, if it consistent failure, then we have a real bug.", durationDisabled, durationAuto, (double) 10 * durationDisabled);
        } finally {
            Scope.exit();
        }
    }
}
