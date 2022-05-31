package hex.rulefit;

import water.Iced;
import water.fvec.*;
import water.parser.BufferedString;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static hex.rulefit.Condition.Type.Numerical;


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

    }

    public int getFeatureIndex() {
        return featureIndex;
    }

    public Type getType() {
        return type;
    }

    public Operator getOperator() {
        return operator;
    }

    public boolean isNAsIncluded() {
        return NAsIncluded;
    }

    public int getNumCatTreshold() {
        return catTreshold.length;
    }

    public double getNumTreshold() {
        return numTreshold;
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
        if (!(obj instanceof Condition))
            return false;

        Condition condition = (Condition) obj;
        if (Numerical.equals(condition.type)) {
            return (this.featureIndex == condition.featureIndex &&
                    this.operator == condition.operator &&
                    this.featureName.equals(condition.featureName) &&
                    Math.abs(this.numTreshold - condition.numTreshold) < 1e-5 &&
                    this.type == condition.type);
        } else {
            return (this.NAsIncluded == condition.NAsIncluded &&
                    this.operator == condition.operator &&
                    Arrays.equals(this.catTreshold, condition.catTreshold) &&
                    this.featureIndex == condition.featureIndex &&
                    this.featureName.equals(condition.featureName) &&
                    Arrays.equals(this.languageCatTreshold, condition.languageCatTreshold) &&
                    this.type == condition.type);
        }
    }

    @Override
    public int hashCode() {
        if (Numerical.equals(type)) {
            int result = Objects.hash(featureIndex, type, operator, featureName, numTreshold);
            return result;
        } else {
            int result = Objects.hash(featureIndex, type, operator, featureName, NAsIncluded);
            result = 31 * result + Arrays.hashCode(languageCatTreshold);
            result = 31 * result + Arrays.hashCode(catTreshold);
            return result;
        }
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
                if (Numerical.equals(Condition.this.type)) {
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
            
            List<String> expandedLanguageCatTresholdList = new ArrayList<>();
            List<Integer> expandedCatTresholdList = new ArrayList<>();
            expandedLanguageCatTresholdList.addAll(Arrays.asList(this.languageCatTreshold));
            expandedCatTresholdList.addAll(Arrays.stream(this.catTreshold).boxed().collect(Collectors.toList()));
            for (int i = 0; i < otherCondition.catTreshold.length; i++) {
                if (!expandedCatTresholdList.contains(otherCondition.catTreshold[i])) {
                    expandedCatTresholdList.add(otherCondition.catTreshold[i]);
                    expandedLanguageCatTresholdList.add(otherCondition.languageCatTreshold[i]);
                }
            }
            expandedlanguageCatTreshold = expandedLanguageCatTresholdList.toArray(new String[0]);
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
