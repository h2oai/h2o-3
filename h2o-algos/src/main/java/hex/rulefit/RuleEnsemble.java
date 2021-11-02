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
    
    public Frame createGLMTrainFrame(Frame frame, int depth, int ntrees, String[] classNames) {
        Frame glmTrainFrame = new Frame();
        // filter rules and create a column for each tree
        boolean isMultinomial = classNames != null && classNames.length > 2;
        int nclasses = isMultinomial ? classNames.length : 1;
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < ntrees; j++) {
                for (int k = 0; k < nclasses; k++) {
                    // filter rules according to varname
                    // varname is of structure "M" + modelId + "T" + node.getSubgraphNumber() + "N" + node.getNodeNumber()
                    String regex = "M" + i + "T" + j + "N" + "\\d+";
                    if (isMultinomial) {
                        regex +=  "_" + classNames[k];
                    }
                    String finalRegex = regex;
                    List<Rule> filteredRules = Arrays.stream(rules)
                            .filter(rule -> rule.varName.matches(finalRegex))
                            .collect(Collectors.toList());
                    if (filteredRules.size() == 0)
                        continue;
                    RuleEnsemble ruleEnsemble = new RuleEnsemble(filteredRules.toArray(new Rule[]{}));
                    Frame frameToMakeCategorical = ruleEnsemble.transform(frame);
                    try {
                        Decoder mrtask = new Decoder();
                        Vec catCol = mrtask.doAll(1, Vec.T_CAT, frameToMakeCategorical)
                                .outputFrame(null, null, new String[][]{frameToMakeCategorical.names()}).vec(0);
                        String name = isMultinomial ? "M" + i + "T" + j + "C" + k : "M" + i + "T" + j;
                        
                        glmTrainFrame.add(name, catCol);
                    } finally {
                        frameToMakeCategorical.remove();
                    }
                }
            }
        }
        return glmTrainFrame;
    }

    public Frame transform(Frame frame) {
        RuleEnsembleConverter rc = new RuleEnsembleConverter(new String[rules.length]);
        Frame transformedFrame =  rc.doAll(rules.length, Vec.T_NUM, frame).outputFrame();
        transformedFrame.setNames(rc._names);
        return transformedFrame;
    }

    class RuleEnsembleConverter extends MRTask<RuleEnsembleConverter> {
        String[] _names;
        
        RuleEnsembleConverter(String[] names) {
            _names = names;
        }
        
        @Override
        public void map(Chunk[] cs, NewChunk[] nc) {
            byte[] out = MemoryManager.malloc1(cs[0].len());
            for (int i = 0; i < rules.length; i++) {
                Arrays.fill(out, (byte) 1);
                rules[i].map(cs, out);
                _names[i] = rules[i].varName;
                for (byte b : out) {
                    nc[i].addNum(b);
                }
            }
        }
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

    static class Decoder extends MRTask<Decoder> {
        Decoder() {
            super();
        }

        @Override public void map(Chunk[] cs, NewChunk[] ncs) {
            for (int iRow = 0; iRow < cs[0].len(); iRow++) {
                int newValue = -1;
                for (int iCol = 0; iCol < cs.length; iCol++) {
                    if (cs[iCol].at8(iRow) == 1) {
                        newValue = iCol;
                    }
                }
                if (newValue >= 0)
                    ncs[0].addNum(newValue);
                else
                    ncs[0].addNA();
            }
        }
    }
    
    public int size() {
        return rules.length;
    }
}
