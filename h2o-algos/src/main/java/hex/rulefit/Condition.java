package hex.rulefit;

import water.Iced;
import water.fvec.*;
import water.parser.BufferedString;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class Condition extends Iced {
    public enum Type {Categorical, Numerical}
    public enum Operator {LessThan, GreaterThanOrEqual, In}
    int featureIndex;
    Type type;
    public Operator operator;
    public String featureName;
    public boolean NAsIncluded;
    public String languageCondition;
    public double numTreshold;
    public String[] languageCatTreshold;
    public int[] catTreshold;

    public Condition(int featureIndex, Type type, Operator operator, double numTreshold, String[] languageCatTreshold, int[] catTreshold, String featureName, boolean NAsIncluded) {
        this.featureIndex = featureIndex;
        this.type = type;
        this.operator = operator;
        this.featureName = featureName;
        this.NAsIncluded = NAsIncluded;
        this.numTreshold = numTreshold;
        this.languageCatTreshold = languageCatTreshold;
        this.catTreshold = catTreshold;
        
        this.languageCondition = constructLanguageCondition();
    }
    
    String constructLanguageCondition() {
        StringBuilder description = new StringBuilder();
        description.append("(").append(this.featureName);
        
        if (Operator.LessThan.equals(this.operator)) {
            description.append(" < ").append(this.numTreshold);
        } else if (Operator.GreaterThanOrEqual.equals(this.operator)) {
            description.append(" >= ").append(this.numTreshold);
        } else if (Operator.In.equals(this.operator)) {
            description.append(" in {");
            for (int i = 0; i < languageCatTreshold.length; i++) {
                if (i != 0) description.append(", ");
                description.append(languageCatTreshold[i]);
            }
            description.append("}");
        }
        if (this.NAsIncluded) {
            description.append(" or ").append(this.featureName).append(" is NA");
        }
        description.append(")");
        return description.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return this.hashCode() == obj.hashCode();
    }

    @Override
    public int hashCode() {
        return this.languageCondition.hashCode();
    }

    public void map(Chunk[] cs, byte[] out) {
        Chunk col = cs[Condition.this.featureIndex];
        for (int iRow = 0; iRow < col._len; ++iRow) {
            if (out[iRow] == 0)
                continue;
            byte newVal = 0;
            boolean isNA = col.isNA(iRow);
            // check whether condition is fulfilled:
            if (Condition.this.NAsIncluded && isNA) {
                newVal = 1;
            } else if (!isNA) {
                if (Condition.Type.Numerical.equals(Condition.this.type)) {
                    if (Condition.Operator.LessThan.equals(Condition.this.operator)) {
                        if (col.atd(iRow) < Condition.this.numTreshold) {
                            newVal = 1;
                        }
                    } else if (Condition.Operator.GreaterThanOrEqual.equals(Condition.this.operator)) {
                        if (col.atd(iRow) >= Condition.this.numTreshold) {
                            newVal = 1;
                        }
                    }
                } else if (Condition.Type.Categorical.equals(Condition.this.type)) {
                    BufferedString tmpStr = new BufferedString();
                    for (int i = 0; i < Condition.this.catTreshold.length; i++) {
                        // for string vecs
                        if (col instanceof CStrChunk) {
                            if (ArrayUtils.contains(Condition.this.languageCatTreshold, col.atStr(tmpStr,iRow))) {
                                newVal = 1;
                            }
                            // for other categorical vecs
                        } else if (Condition.this.catTreshold[i] == col.atd(iRow)) {
                            newVal = 1;
                        }
                    }
                }
            }
            out[iRow] = newVal;
        }
    }

    Condition expandBy(Condition otherCondition) {
        assert this.type.equals(otherCondition.type);
        assert this.operator.equals(otherCondition.operator);
        assert this.featureIndex == otherCondition.featureIndex;
        assert this.featureName.equals(otherCondition.featureName);
        
        double expandedNumThreshold;
        String[] expandedlanguageCatTreshold;
        int[] expandedCatTreshold;
        boolean expandedNAsIncluded = false;
        
        if (this.type.equals(Type.Categorical)) {
            expandedNumThreshold = -1;
            
            List<String> expandedlanguageCatTresholdList = new ArrayList<>();
            List<Integer> expandedCatTresholdList = new ArrayList<>();
            expandedlanguageCatTresholdList.addAll(Arrays.asList(this.languageCatTreshold));
            expandedCatTresholdList.addAll(Arrays.stream(this.catTreshold).boxed().collect(Collectors.toList()));
            for (int i = 0; i < otherCondition.catTreshold.length; i++) {
                if (!expandedCatTresholdList.contains(otherCondition.catTreshold[i])) {
                    expandedCatTresholdList.add(otherCondition.catTreshold[i]);
                    expandedlanguageCatTresholdList.add(otherCondition.languageCatTreshold[i]);
                }
            }
            expandedlanguageCatTreshold = expandedCatTresholdList.toArray(new String[0]);
            expandedCatTreshold = expandedCatTresholdList.stream().mapToInt(i->i).toArray();

        } else {
            if (Operator.LessThan.equals(this.operator)) {
                expandedNumThreshold = Double.max(this.numTreshold, otherCondition.numTreshold);
            } else {
                assert Operator.GreaterThanOrEqual.equals(this.operator);
                expandedNumThreshold = Double.min(this.numTreshold, otherCondition.numTreshold);
            }

            expandedlanguageCatTreshold = null;
            expandedCatTreshold = null;
        }
        
        if (this.NAsIncluded || otherCondition.NAsIncluded)
            expandedNAsIncluded = true;
        
        return new Condition(this.featureIndex, this.type, this.operator, expandedNumThreshold, 
                expandedlanguageCatTreshold, expandedCatTreshold, this.featureName, expandedNAsIncluded);
    }
}
