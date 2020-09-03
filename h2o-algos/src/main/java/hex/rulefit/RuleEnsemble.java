package hex.rulefit;

import water.Iced;
import water.fvec.Frame;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RuleEnsemble extends Iced {
    
   Rule[] rules;
    
    public RuleEnsemble(Rule[] rules) {
        this.rules = rules;
    }

    public Frame transform(Frame frame) {
        Frame transformedFrame = new Frame();
        Frame actFrame;
        for (Rule rule : rules) {
            actFrame = rule.transform(frame);
            actFrame.setNames(new String[] {rule.languageRule});
            ////actFrame.setNames(new String[] {String.valueOf(rule.varName)});
            transformedFrame.add(actFrame);
        }
        return transformedFrame;
    }
    
    
    public Rule getRuleByLanguageRule(String languageRule) {
        List<Rule> filteredRule = Arrays.stream(this.rules)
                .filter(rule -> languageRule.equals(rule.languageRule))
                .collect(Collectors.toList());
        
        return filteredRule.get(0);
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
