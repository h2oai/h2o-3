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
            final Frame fr = parseTestFile("./smalldata/gbm_test/titanic.csv");
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
            Frame train = Scope.track(parseTestFile("smalldata/extdata/prostate.csv"));

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
            Set<Rule> treeRules =  Rule.extractRulesFromTree(sharedTreeSubgraph, 0, null);
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
            Frame train = Scope.track(parseTestFile("smalldata/testng/airlines.csv"));

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
            Set<Rule> treeRules =  Rule.extractRulesFromTree(sharedTreeSubgraph, 0, null);
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
    
    @Test
    public void consolidateRulesTest() {
        try {
            Scope.enter();
            Condition condition1 = new Condition(6, Condition.Type.Numerical, Condition.Operator.LessThan, 6.5, null, null,"PSA", true);
            Condition condition2 = new Condition(6, Condition.Type.Numerical, Condition.Operator.LessThan, 14.730077743530273, null, null, "PSA", false);
            Condition condition3 = new Condition(2, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 2.5, null, null,"DPROS", false);
            Condition[] conditions = new Condition[] {condition1, condition2, condition3};

            Rule rule = new Rule(conditions, 0.032236840575933456, "somevarname");
            Rule consolidatedRule = RuleFitUtils.consolidateRule(rule, true);
            assertEquals("(PSA < 14.730077743530273 or PSA is NA) & (DPROS >= 2.5)", consolidatedRule.languageRule);
            
            condition1 = new Condition(6, Condition.Type.Categorical, Condition.Operator.In, -1, new String[] {"ABC", "AAA"}, new int[] {2, 6},"PSA", true);
            condition2 = new Condition(6, Condition.Type.Categorical, Condition.Operator.In, -1,  new String[] { "CCC", "BBB", "AAA"}, new int[] {1, 3, 6}, "PSA", false);
            condition3 = new Condition(2, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 2.5, null, null,"DPROS", false);
            conditions = new Condition[] {condition1, condition2, condition3};

            rule = new Rule(conditions, 0.032236840575933456, "somevarname");
            consolidatedRule = RuleFitUtils.consolidateRule(rule, true);
            assertEquals("(PSA in {ABC, AAA, CCC, BBB} or PSA is NA) & (DPROS >= 2.5)", consolidatedRule.languageRule);

            condition1 = new Condition(6, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 6.5, null, null,"PSA", true);
            condition2 = new Condition(6, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 14.730077743530273, null, null, "PSA", false);
            condition3 = new Condition(2, Condition.Type.Numerical, Condition.Operator.LessThan, 2.5, null, null,"DPROS", false);
            conditions = new Condition[] {condition1, condition2, condition3};

            rule = new Rule(conditions, 0.032236840575933456, "somevarname");
            consolidatedRule = RuleFitUtils.consolidateRule(rule, true);
            assertEquals("(PSA >= 6.5 or PSA is NA) & (DPROS < 2.5)", consolidatedRule.languageRule);

            condition1 = new Condition(6, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 6.5, null, null,"PSA", true);
            condition2 = new Condition(6, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 14.730077743530273, null, null, "PSA", false);
            condition3 = new Condition(2, Condition.Type.Numerical, Condition.Operator.LessThan, 2.5, null, null,"DPROS", false);
            Condition condition4 = new Condition(6, Condition.Type.Numerical, Condition.Operator.LessThan, 10.0, null, null, "PSA", false);

            conditions = new Condition[] {condition1, condition2, condition3, condition4};

            rule = new Rule(conditions, 0.032236840575933456, "somevarname");
            consolidatedRule = RuleFitUtils.consolidateRule(rule, true);
            assertEquals("(PSA < 10.0) & (PSA >= 6.5 or PSA is NA) & (DPROS < 2.5)", consolidatedRule.languageRule);
            
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void deduplicateRulesTest() {
        try {
            Scope.enter();

            Condition conditionr11 = new Condition(6, Condition.Type.Numerical, Condition.Operator.LessThan, 6.5, null, null,"PSA", true);
            Condition conditionr12 = new Condition(6, Condition.Type.Numerical, Condition.Operator.LessThan, 14.730077743530273, null, null, "PSA", false);
            Condition conditionr13 = new Condition(2, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 2.5, null, null,"DPROS", false);
            Condition[] conditions1 = new Condition[] {conditionr11, conditionr13, conditionr12};

            Rule rule1 = new Rule(conditions1, 0.032236840575933456, "somevarname1");
            rule1.coefficient = 4.0;

            Condition conditionr21 = new Condition(6, Condition.Type.Categorical, Condition.Operator.In, -1, new String[] {"ABC", "AAA"}, new int[] {2, 6},"PSA", true);
            Condition conditionr22 = new Condition(6, Condition.Type.Categorical, Condition.Operator.In, -1,  new String[] { "CCC", "BBB", "AAA"}, new int[] {1, 3, 6}, "PSA", false);
            Condition conditionr23 = new Condition(2, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 2.5, null, null,"DPROS", false);
            Condition[] conditions2 = new Condition[] {conditionr21, conditionr22, conditionr23};

            Rule rule2 = new Rule(conditions2, 0.032236840575933456, "somevarname2");

            Condition condition31 = new Condition(6, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 6.5, null, null,"PSA", true);
            Condition condition32 = new Condition(6, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 14.730077743530273, null, null, "PSA", false);
            Condition condition33 = new Condition(2, Condition.Type.Numerical, Condition.Operator.LessThan, 2.5, null, null,"DPROS", false);
            Condition[] conditions3 = new Condition[] {condition31, condition32, condition33};

            Rule rule3 = new Rule(conditions3, 0.032236840575933456, "somevarname3");

            Condition conditionr41 = new Condition(6, Condition.Type.Numerical, Condition.Operator.LessThan, 6.5, null, null,"PSA", true);
            Condition conditionr42 = new Condition(6, Condition.Type.Numerical, Condition.Operator.LessThan, 14.730077743530273, null, null, "PSA", false);
            Condition conditionr43 = new Condition(2, Condition.Type.Numerical, Condition.Operator.GreaterThanOrEqual, 2.5, null, null,"DPROS", false);
            Condition[] conditions4 = new Condition[] {conditionr41, conditionr42, conditionr43};

            Rule rule4 = new Rule(conditions4, 10.23, "somevarname4");
            rule4.coefficient = 3.0;
            
            Rule[] rulesToDeduplicate = new Rule[] {rule1, rule2, rule3, rule4};

            Rule[]  deduplicatedRules = RuleFitUtils.deduplicateRules(rulesToDeduplicate);
            
            assertEquals(3, deduplicatedRules.length);

            Rule deduplicatedRule = Arrays.asList(deduplicatedRules).stream()
                    .filter(rule -> rule.coefficient != 0.0)
                    .findAny()
                    .orElse(null);
            
            assertEquals(deduplicatedRule.coefficient, rule1.coefficient + rule4.coefficient, 0.0);
            assertEquals("somevarname1, somevarname4", deduplicatedRule.varName);
            
        } finally {
            Scope.exit();
        }
    }

}
