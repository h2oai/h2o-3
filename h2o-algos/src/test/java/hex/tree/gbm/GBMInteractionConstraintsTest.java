package hex.tree.gbm;

import hex.Model;
import hex.genmodel.CategoricalEncoding;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.tree.GlobalInteractionConstraints;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import java.util.*;


@CloudSize(1)
@RunWith(H2ORunner.class)
public class GBMInteractionConstraintsTest extends TestUtil {
    
    @Test
    public void testInteraction() {
        String[][] interactions = new String[2][];
        interactions[0] = new String[]{"AGE", "RACE", "PSA", "GLEASON"};
        interactions[1] = new String[]{"DPROS", "DCAPS", "VOL"};
        testInteractionConstraintsSimple(interactions);
    }

    @Test
    public void testInteraction1() {
        String[][] interactions = new String[2][];
        interactions[0] = new String[]{"AGE", "RACE", "PSA", "GLEASON"};
        interactions[1] = new String[]{"DPROS", "DCAPS", "VOL", "GLEASON"};
        testInteractionConstraintsSimple(interactions);
    }

    @Test
    public void testInteraction2() {
        String[][] interactions = new String[2][];
        interactions[0] = new String[]{"AGE", "RACE", "PSA", "GLEASON"};
        interactions[1] = new String[]{"DPROS"};
        testInteractionConstraintsSimple(interactions);
    }

    @Test
    public void testInteraction3() {
        String[][] interactions = new String[3][];
        interactions[0] = new String[]{"AGE", "RACE", "PSA", "GLEASON"};
        interactions[1] = new String[]{"DPROS"};
        interactions[2] = new String[]{"DPROS", "VOL"};
        testInteractionConstraintsSimple(interactions);
    }

    @Test
    public void testInteraction4() {
        String[][] interactions = new String[3][];
        interactions[0] = new String[]{"AGE", "RACE", "PSA", "GLEASON"};
        interactions[1] = new String[]{"DPROS"};
        interactions[2] = new String[]{"DPROS", "VOL"};
        testInteractionConstraintsSimple(interactions, 10, 20);
    }

    @Test
    public void testInteraction5() {
        String[][] interactions = new String[3][];
        interactions[0] = new String[]{"RACE"};
        interactions[1] = new String[]{"DPROS"};
        interactions[2] = new String[]{"VOL"};
        testInteractionConstraintsSimple(interactions);
    }

    @Test
    public void testInteraction6() {
        String[][] interactions = new String[2][];
        interactions[0] = new String[]{"AGE", "PSA"};
        interactions[1] = new String[]{"GLEASON"};
        testInteractionConstraintsSimple(interactions);
    }

    @Test
    public void testInteractionUnknownColError() {
        try {
            String[][] interactions = new String[2][];
            interactions[0] = new String[]{"RACE"};
            interactions[1] = new String[]{"42"};
            testInteractionConstraintsSimple(interactions);
        } catch (H2OModelBuilderIllegalArgumentException ex){
            assert ex.getMessage().contains("ERRR on field: _interaction_constraints: Invalid interaction constraint - there is no column '42' in the training frame.");
        }
    }

    @Test
    public void testInteractionResponseColError() {
        try {
            String[][] interactions = new String[2][];
            interactions[0] = new String[]{"RACE"};
            interactions[1] = new String[]{"CAPSULE"};
            testInteractionConstraintsSimple(interactions);
        } catch (H2OModelBuilderIllegalArgumentException ex){
            assert ex.getMessage().contains("ERRR on field: '_interaction_constraints': Column with the name 'CAPSULE' is used as response column and cannot be used in interaction.");
        }
    }

    @Test
    public void testInteractionIgnoredColError() {
        try {
            String[][] interactions = new String[2][];
            interactions[0] = new String[]{"RACE"};
            interactions[1] = new String[]{"ID"};
            testInteractionConstraintsSimple(interactions);
       } catch (H2OModelBuilderIllegalArgumentException ex){
            assert ex.getMessage().contains("ERRR on field: _interaction_constraints: Column with the name 'ID' is set in ignored columns and cannot be used in interaction.");
        }
    }
    
    public void testInteractionConstraintsSimple(String[][] interactionConstraints) {
        testInteractionConstraintsSimple(interactionConstraints, 3, 10);
    }
    
    public void testInteractionConstraintsSimple(String[][] interactionConstraints, int maxDepth, int ntrees) {
        Frame fr = null;
        GBMModel model = null;
        Scope.enter();
        try {
            final String response = "CAPSULE";
            final String testFile = "smalldata/logreg/prostate.csv";
            fr = parseTestFile(testFile)
                    .toCategoricalCol(response);
            Scope.track(fr);
            DKV.put(fr);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._response_column = response;
            parms._ignored_columns = new String[]{"ID"};
            parms._train = fr._key;
            parms._ntrees = ntrees;
            parms._max_depth = maxDepth;
            parms._seed = 1234L;
            parms._interaction_constraints = interactionConstraints;

            model = new GBM(parms).trainModel().get();

            String[] names = Arrays.copyOfRange(fr.names(), 2, fr.names().length);
            checkTrees(model, names);
        } finally {
            if (model != null) model.delete();
            if (fr  != null) fr.remove();
            Scope.exit();
        }
    }
    
    public void testInteractionConstraintsOneHotEncoding(Model.Parameters.CategoricalEncodingScheme encoding) {
        Frame fr = null;
        GBMModel model = null;
        Scope.enter();
        try {
            final String response = "CAPSULE";
            final String testFile = "smalldata/logreg/prostate.csv";
            fr = parseTestFile(testFile)
                    .toCategoricalCol("RACE")
                    .toCategoricalCol(response);
            Scope.track(fr);
            DKV.put(fr);

            String[][] interactions = new String[2][];
            interactions[0] = new String[]{"AGE", "RACE", "PSA", "GLEASON"};
            interactions[1] = new String[]{"DPROS", "DCAPS", "VOL"};

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._response_column = response;
            parms._ignored_columns = new String[]{"ID"};
            parms._train = fr._key;
            parms._ntrees = 10;
            parms._max_depth = 3;
            parms._seed = 1234L;
            parms._interaction_constraints = interactions;
            parms._categorical_encoding = encoding;

            model = new GBM(parms).trainModel().get();
            
            String[] names = Arrays.copyOfRange(fr.names(), 2, fr.names().length);
            checkTrees(model, names);
        } finally {
            if (model != null) model.delete();
            if (fr  != null) fr.remove();
            Scope.exit();
        }
    }
    
    @Test
    public void testInteractionConstraintsOneHotEncoding(){
        testInteractionConstraintsOneHotEncoding(Model.Parameters.CategoricalEncodingScheme.AUTO);
    }

    @Test
    public void testInteractionConstraintsOneHotEncodingWrong(){
        try {
            testInteractionConstraintsOneHotEncoding(Model.Parameters.CategoricalEncodingScheme.OneHotExplicit);
        } catch (H2OModelBuilderIllegalArgumentException ex){
                assert ex.getMessage().contains("Interaction constraints can be used when the categorical encoding is set to ``AUTO`` (``one_hot_internal`` or ``OneHotInternal``) only.");
        }
    }
    
    private void collectBranchIndices(SharedTreeNode node, List<Integer> branch, List<List<Integer>> allBranches){
        if(node.isLeaf()){
            allBranches.add(new ArrayList<>(branch));
        } else {
            branch.add(node.getColId());
            int index = branch.size() - 1;
            collectBranchIndices(node.getLeftChild(), branch, allBranches);
            SharedTreeNode right = node.getRightChild();
            if(right != null && !right.isLeaf()) {
                collectBranchIndices(node.getRightChild(), branch, allBranches);
            }
            branch.remove(index);
        }
    }

    private boolean checkBranches(SharedTreeNode root, GlobalInteractionConstraints ics){
        List<List<Integer>> allBranches = new ArrayList<>();
        collectBranchIndices(root, new ArrayList<>(), allBranches);
        Set<Integer> allowed;
        for(List<Integer> branch : allBranches){
            allowed = ics.getAllowedInteractionForIndex(branch.get(0));
            for (int i = 1; i < branch.size(); i++){
                int colIndex = branch.get(i);
                if (!allowed.contains(colIndex)) {
                    System.out.println("Constraint violated: allowed indices "+Arrays.toString(allowed.toArray())+" index is: "+colIndex);
                    return false;
                }
                if (i < branch.size() - 1) {
                    Set<Integer> interception = new HashSet<>(allowed);
                    interception.retainAll(ics.getAllowedInteractionForIndex(colIndex));
                    allowed = new HashSet<>(interception);
                }
            }
        }
        return true;
    }

    private void checkTrees(GBMModel model, String[] names){
        GlobalInteractionConstraints ics = new GlobalInteractionConstraints(model._parms._interaction_constraints, names);
        for (int i = 0; i < model._parms._ntrees; i++) {
            for (int c = 0; c < 2; c++) {
                System.out.println("===================");
                System.out.println("Tree " + i + " class "+ c +" :");
                SharedTreeNode[] nodes = model.getSharedTreeSubgraph(i, 0).getNodes();
                boolean isTreeOk = checkBranches(nodes[0], ics);
                System.out.println("Tree is ok: " + isTreeOk);
                if (!isTreeOk) {
                    for (SharedTreeNode node : nodes) {
                        if (node.getColName() != null) {
                            System.out.println("Node ID: " + node.getNodeNumber() + " split col name: " + node.getColName());
                        }
                    }
                }
                Assert.assertTrue("Tree violates interaction constraints.", isTreeOk);
            }
        }
    }
}
