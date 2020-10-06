package hex.rulefit;

import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Arrays;

public class RulefitTestUtils {

    public static Frame transform(Frame frame, Rule rule) {
        RuleConverter rc = new RuleConverter(rule);
        return rc.doAll(1, Vec.T_NUM, frame).outputFrame();
    }

    public static Frame transform(Frame frame, Condition condition) {
        ConditionConverter mrtask = new ConditionConverter(condition);
        return mrtask.doAll(1, Vec.T_NUM, frame).outputFrame();
    }

    public static class ConditionConverter extends MRTask<ConditionConverter> {
        private final Condition _condition;

        ConditionConverter(Condition condition){
            _condition = condition;
        }

        @Override
        public void map(Chunk[] cs, NewChunk nc) {
            Chunk col = cs[_condition.featureIndex];
            byte[] out = MemoryManager.malloc1(col.len());
            Arrays.fill(out, (byte) 1);
            _condition.map(cs, out);
            for (byte b : out) {
                nc.addNum(b);
            }
        }
    }

    public static class RuleConverter extends MRTask<RuleConverter> {
        private final Rule _rule;

        RuleConverter(Rule rule) {
            _rule = rule;
        }

        @Override
        public void map(Chunk[] cs, NewChunk nc) {
            byte[] out = MemoryManager.malloc1(cs[0].len());
            Arrays.fill(out, (byte) 1);
            _rule.map(cs, out);
            for (byte b : out) {
                nc.addNum(b);
            }
        }
    }
}
