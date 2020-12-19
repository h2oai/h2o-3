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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
            final Frame fr2 = RulefitTestUtils.transform(fr, condition);
            Scope.track(fr2);
            
            assertEquals(1,fr2.numCols());
            assertEquals(fr.numRows(),fr2.numRows());
            assertEquals(0,fr2.vec(0).at8(322));
            assertEquals(1,fr2.vec(0).at8(323));

            Condition condition2 = new Condition(13, Condition.Type.Categorical, Condition.Operator.In, 0, new String[] {fr.vec(13).stringAt(20)/*"New York  NY"*/, fr.vec(13).stringAt(0)/*"St Louis  MO"*/}, new int[] {(int)fr.vec(13).at(20)/*236*/, (int)fr.vec(13).at(0)/*308*/},"home.dest", false);
            final Frame fr3 = RulefitTestUtils.transform(fr, condition2);
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
            final Frame fr4 = RulefitTestUtils.transform(fr, rule);
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

            List<Rule> wholeModelRules = Rule.extractRulesListFromModel(gbm, 0, 1);
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

            List<String> languageRules = treeRules.stream().map(it -> it.languageRule).sorted().collect(Collectors.toList());
            // Note: this hard-coded list of expected rules is sensitive to changes in the upstream algo
            List<String> expectedRules = Arrays.asList(
                    "(fYear in {f1987, f1988, f1989, f1990, f1991}) & (fYear in {f1987, f1988}) & (fYear in {f1987})",
                    "(fYear in {f1987, f1988, f1989, f1990, f1991}) & (fYear in {f1987, f1988}) & (fYear in {f1988})",
                    "(fYear in {f1987, f1988, f1989, f1990, f1991}) & (fYear in {f1989, f1990, f1991, f1992, f1993, f1994, f1995, f1996, f1997, f1998, f1999, f2000}) & (fDayOfWeek in {f1, f2, f3, f4} or fDayOfWeek is NA)",
                    "(fYear in {f1987, f1988, f1989, f1990, f1991}) & (fYear in {f1989, f1990, f1991, f1992, f1993, f1994, f1995, f1996, f1997, f1998, f1999, f2000}) & (fDayOfWeek in {f5, f6, f7})",
                    "(fYear in {f1992, f1993, f1994, f1995, f1996, f1997, f1998, f1999, f2000} or fYear is NA) & (Distance < 211.5) & (fYear in {f1987, f1988, f1989, f1990, f1991, f1992, f1993} or fYear is NA)",
                    "(fYear in {f1992, f1993, f1994, f1995, f1996, f1997, f1998, f1999, f2000} or fYear is NA) & (Distance < 211.5) & (fYear in {f1994, f1995, f1996, f1997, f1998, f1999, f2000})",
                    "(fYear in {f1992, f1993, f1994, f1995, f1996, f1997, f1998, f1999, f2000} or fYear is NA) & (Distance >= 211.5 or Distance is NA) & (Distance < 348.5)",
                    "(fYear in {f1992, f1993, f1994, f1995, f1996, f1997, f1998, f1999, f2000} or fYear is NA) & (Distance >= 211.5 or Distance is NA) & (Distance >= 348.5 or Distance is NA)"
            );
            assertEquals(expectedRules, languageRules);

            List<Rule> wholeModelRules = Rule.extractRulesListFromModel(isofor, 0, 1);
            assertEquals(wholeModelRules.size(), 8);

        } finally {
            Scope.exit();
        }
    }

}
