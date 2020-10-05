package hex.rulefit;


import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.tree.SharedTreeModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import hex.tree.isofor.IsolationForest;
import hex.tree.isofor.IsolationForestModel;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import java.util.List;
import java.util.Set;
import static hex.tree.TreeUtils.getResponseLevelIndex;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RuleFitUtilsTest extends TestUtil {

    @BeforeClass public static void setup() { stall_till_cloudsize(1); }

    @Test
    public void testConditionAndRule() {
        try {
            Scope.enter();
            final Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
            Scope.track(fr);

            Condition condition = new Condition(0, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 2, null, null, "pclass", false );
            final Frame fr2 = condition.transform(fr);
            Scope.track(fr2);
            
            assertEquals(1,fr2.numCols());
            assertEquals(fr.numRows(),fr2.numRows());
            assertEquals(0,fr2.vec(0).at8(322));
            assertEquals(1,fr2.vec(0).at8(323));

            Condition condition2 = new Condition(13, Condition.Type.Categorical, Condition.Operator.In, 0, new String[] {fr.vec(13).stringAt(20)/*"New York  NY"*/, fr.vec(13).stringAt(0)/*"St Louis  MO"*/}, new int[] {(int)fr.vec(13).at(20)/*236*/, (int)fr.vec(13).at(0)/*308*/},"home.dest", false);
            final Frame fr3 = condition2.transform(fr);
            Scope.track(fr3);

            assertEquals(1,fr3.numCols());
            assertEquals(fr.numRows(),fr3.numRows());
            assertEquals(1,fr3.vec(0).at8(0));
            assertEquals(1,fr3.vec(0).at8(5));
            assertEquals(0,fr3.vec(0).at8(4));
            
            assertFalse(condition.equals(condition2));
            Condition condition3 = new Condition(13, Condition.Type.Categorical, Condition.Operator.In, 0, new String[] {fr.vec(13).stringAt(20)/*"New York  NY"*/, fr.vec(13).stringAt(0)/*"St Louis  MO"*/}, new int[] {(int)fr.vec(13).at(20)/*236*/, (int)fr.vec(13).at(0)/*308*/},"home.dest", false);
            assertTrue(condition2.equals(condition3));
            
            Rule rule = new Rule(new Condition[]{condition, condition2}, 0.3456, "somevarname");
            final Frame fr4 = rule.transform(fr);
            Scope.track(fr4);
            
            assertEquals(fr4.vec(0).at(0), fr2.vec(0).at(0)*fr3.vec(0).at(0), 1e-8);
            assertEquals(fr4.vec(0).at(5), fr2.vec(0).at(5)*fr3.vec(0).at(5),1e-8);
            assertEquals(fr4.vec(0).at(4), fr2.vec(0).at(4)*fr3.vec(0).at(4),1e-8);
            assertEquals(fr4.vec(0).at(322), fr2.vec(0).at(322)*fr3.vec(0).at(322),1e-8);
            assertEquals(fr4.vec(0).at(323), fr2.vec(0).at(323)*fr3.vec(0).at(323),1e-8);
            
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void extractRulesFromTreeFromModelTestNumerical() {
        try {
            Scope.enter();
            String[] expectedFileNames = new String[2];
            expectedFileNames[0]="Tree1.png";
            expectedFileNames[1]="Tree0.png";
            Frame train = Scope.track(TestUtil.parse_test_file("smalldata/extdata/prostate.csv"));

            GBMModel.GBMParameters p = new GBMModel.GBMParameters();
            p._train = train._key;
            p._response_column = "CAPSULE";
            p._ignored_columns = new String[]{"ID"};
            p._seed = 1;
            p._ntrees = 2;
            p._max_depth = 3;

            final GBMModel gbm = new GBM(p).trainModel().get();
            Scope.track_generic(gbm);

            final SharedTreeModel.SharedTreeOutput sharedTreeOutput = gbm._output;
            final int treeClass = getResponseLevelIndex(null, sharedTreeOutput);
            SharedTreeSubgraph sharedTreeSubgraph = gbm.getSharedTreeSubgraph(0, treeClass);
            Set<Rule> treeRules =  Rule.extractRulesFromTree(sharedTreeSubgraph, 0);
            assertEquals(treeRules.size(), 8);

            Condition condition1 = new Condition(6, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 6.5, null, null,"GLEASON", false);
            Condition condition2 = new Condition(4, Condition.Type.Numerical, Condition.Operator.LessThan, 14.730077743530273, null, null, "PSA", false);
            Condition condition3 = new Condition(2, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 2.5, null, null,"DPROS", false);
            Condition[] conditions = new Condition[] {condition1, condition2, condition3};

            Rule rule = new Rule(conditions, 0.032236840575933456, "somevarname");

            assertEquals(treeRules.contains(rule),true);

            condition1 = new Condition(6, Condition.Type.Numerical, Condition.Operator.LessThan, 6.5, null, null,"GLEASON", true);
            condition2 = new Condition(2, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 2.5, null, null, "DPROS", false);
            condition3 = new Condition(4, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 6.6455078125, null, null,"PSA", false);
            conditions = new Condition[] {condition1, condition2, condition3};

            rule = new Rule(conditions, 0.032236840575933456, "somevarname");

            assertEquals(treeRules.contains(rule),true);

            List<Rule> wholeModelRules = Rule.extractRulesListFromModel(gbm, 0);
            assertEquals(wholeModelRules.size(), 16);

        } finally {
            Scope.exit();
        }
    }


    @Test
    public void extractRulesFromTreeFromModelTesCategorical() {
        try {
            Scope.enter();
            Frame train = Scope.track(TestUtil.parse_test_file("smalldata/testng/airlines.csv"));

            IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
            p._train = train._key;
            p._seed = 0xFEED;
            p._ntrees = 1;
            p._max_depth = 3;
            p._ignored_columns = new String[] { "Origin", "Dest", "IsDepDelayed" };

            final IsolationForestModel isofor = new IsolationForest(p).trainModel().get();
            Scope.track_generic(isofor);

            final SharedTreeModel.SharedTreeOutput sharedTreeOutput = isofor._output;
            final int treeClass = getResponseLevelIndex(null, sharedTreeOutput);
            SharedTreeSubgraph sharedTreeSubgraph = isofor.getSharedTreeSubgraph(0, treeClass);
            Set<Rule> treeRules =  Rule.extractRulesFromTree(sharedTreeSubgraph, 0);
            assertEquals(treeRules.size(), 8);

            Condition condition1 = new Condition(0, Condition.Type.Categorical, Condition.Operator.In, -1.0, new String[] {"f1995", "f1996", "f1997", "f1998", "f1999", "f2000"}, new int[] {8, 9, 10, 11, 12, 13}, "fYear", false);
            Condition condition2 = new Condition(5, Condition.Type.Numerical, Condition.Operator.LessThan, 228.5, null, null, "Distance", false);
            Condition condition3 = new Condition(2, Condition.Type.Categorical, Condition.Operator.In, -1.0, new String[] {"f24", "f25", "f26", "f27", "f28", "f29", "f3", "f30", "f31", "f4", "f5", "f6", "f7", "f8", "f9"},
                    new int[] {16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30},"fDayofMonth", true);
            Condition[] conditions = new Condition[] {condition1, condition2, condition3};

            Rule rule = new Rule(conditions, 2.0, "whatever");

            assertEquals(treeRules.contains(rule),true);

            condition1 = new Condition(0, Condition.Type.Categorical, Condition.Operator.In, -1.0, new String[] {"f1995", "f1996", "f1997", "f1998", "f1999", "f2000"}, new int[] {8, 9, 10, 11, 12, 13}, "fYear", false);
            condition2 = new Condition(5, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 228.5, null, null,"Distance", true);
            condition3 = new Condition(5, Condition.Type.Numerical, Condition.Operator.LessThan, 362.5, null, null,"Distance", false);
            conditions = new Condition[] {condition1, condition2, condition3};

            rule = new Rule(conditions, 2.0, "whatever");
            
            assertEquals(treeRules.contains(rule),true);

            List<Rule> wholeModelRules = Rule.extractRulesListFromModel(isofor, 0);
            assertEquals(wholeModelRules.size(), 8);

        } finally {
            Scope.exit();
        }
    }

}
