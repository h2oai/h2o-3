package hex.rulefit;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RuleEnsemble extends Iced {
    
   Rule[] rules;
    
    public RuleEnsemble(Rule[] rules) {
        this.rules = rules;
    }
    
    public Frame createGLMTrainFrame(Frame frame, int depth, int ntrees) {
        Frame glmTrainFrame = new Frame();
        // filter rules and create a column for each tree
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < ntrees; j++) {
                // filter rules according to varname
                // varname is of structue "M" + modelId + "T" + node.getSubgraphNumber() + "N" + node.getNodeNumber()
                String regex = "M" + i + "T" + j + "N" + "\\d+";
                List<Rule> filteredRules = Arrays.asList(rules)
                        .stream()
                        .filter(rule ->  rule.varName.matches(regex))
                        .collect(Collectors.toList());
                
                RuleEnsemble ruleEnsemble = new RuleEnsemble(filteredRules.toArray(new Rule[] {}));
                Frame frameToMakeCategorical = ruleEnsemble.transform(frame);
                Vec tmpCol = null;
                try {
                    Decoder mrtask = new Decoder(frameToMakeCategorical.names());
                    tmpCol = mrtask.doAll(1, Vec.T_STR, frameToMakeCategorical).outputFrame().vec(0);
                    Vec catCol = tmpCol.toCategoricalVec();
                    glmTrainFrame.add("M" + i + "T" + j, catCol);
                } finally {
                    frameToMakeCategorical.remove();
                    if (tmpCol != null)
                        tmpCol.remove();
                }
            }
        }
        return glmTrainFrame;
    }

    public Frame transform(Frame frame) {
        String[] names = new String[rules.length];
        Transform mrtask = new Transform(rules, names);
        Frame transformedFrame = mrtask.doAll(rules.length, Vec.T_NUM, frame).outputFrame();
        transformedFrame.setNames(mrtask._names); // or names?

        return transformedFrame;
    }

    public Rule getRuleByVarName(String code) {
        List<Rule> filteredRule = Arrays.stream(this.rules)
                .filter(rule -> code.equals(String.valueOf(rule.varName)))
                .collect(Collectors.toList());

        if (filteredRule.size() == 1)
            return filteredRule.get(0);
        else if (filteredRule.size() > 1) {
            throw new RuntimeException("Multiple rules with the same varName in RuleEnsemble!");
        } else {
            throw new RuntimeException("No rule with varName " + code + " found!");
        }
    }
}

class Transform extends MRTask<Transform> {
    Rule[] _rules;
    String[] _names;

    Transform(Rule[] rules, String[] names) {
        _rules = rules;
        _names = names;
    }

    @Override public void map(Chunk[] cs, NewChunk[] ncs) {
        NewChunk chunk[] = new NewChunk[_rules.length];
        for (int i = 0; i < _rules.length; i++) {
            chunk[i] = _rules[i].transform(cs);
            _names[i] = _rules[i].varName;

            // write result to NewChunk
            for (int j = 0; j < chunk[i]._len; j++)
                ncs[i].addNum(chunk[i].at8(j));
        }
    }
}

class Decoder extends MRTask<hex.rulefit.Decoder> {
    final String[] _colNames;
    
    Decoder(String[] colNames) {
        super();
        _colNames = colNames;
    }
    
    @Override public void map(Chunk[] cs, NewChunk[] ncs) {
        String newValue = "";
        for (int iRow = 0; iRow < cs[0].len(); iRow++) {
            for (int iCol = 0; iCol < cs.length; iCol++) {
                if (cs[iCol].at8(iRow) == 1) {
                    newValue = _colNames[iCol];
                }
            }
            ncs[0].addStr(newValue);
        }
    }
}
