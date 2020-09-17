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
        Frame glmTrainFrame = new Frame(Key.make());
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
                Decoder mrtask = new Decoder(frameToMakeCategorical.names());
                Vec column = mrtask.doAll(1, Vec.T_STR, frameToMakeCategorical).outputFrame().vec(0).toCategoricalVec();
                glmTrainFrame.add("M" + i + "T" + j, column);
            }
        }
        return glmTrainFrame;
    }

    public Frame transform(Frame frame) {
        Frame transformedFrame = new Frame();
        Frame actFrame;
        for (Rule rule : rules) {
            actFrame = rule.transform(frame);
            actFrame.setNames(new String[] {String.valueOf(rule.varName)});
            transformedFrame.add(actFrame);
        }
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
