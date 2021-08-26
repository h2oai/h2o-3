package ai.h2o.automl;

import water.Iced;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Defines a step or a list of steps to be executed.
 * The steps implementations are provided by instances of (@link {@link ModelingStepsProvider}.
 */
public class StepDefinition extends Iced<StepDefinition> {

    public enum Alias { all, defaults, grids, exploitation, optionals }

    public static class Step extends Iced<Step> {
        public static final int DEFAULT_GROUP = -1; //step will use the default priority group as defined by the ModelingStep.
        public static final int DEFAULT_WEIGHT = -1;  // means that the Step will use the default weight set by the ModelingStep.

        /**
         * The id of the step (must be unique per step provider).
         */
        String _id;
        int _group = DEFAULT_GROUP;
        /**
         * The relative weight for the given step.
         * The higher the weight, the more time percentage it will be offered in a time-constrained context.
         * For hyperparameter search, the weight may also impact the number of models trained in a count-model-constrained context.
         */
        int _weight = DEFAULT_WEIGHT;  // share of time dedicated

        public Step() { /* for autofill from schema */ }

        public Step(String _id) {
            this._id = _id;
        }

        public Step(String id, int group, int weight) {
            assert group == DEFAULT_GROUP || group >= 0: "non-default group must be >= 0";
            assert weight == DEFAULT_WEIGHT || weight >= 0: "non-default weight must be >= 0";
            this._id = id;
            this._group = group;
            this._weight = weight;
        }
        
        public String getId() {
            return _id;
        }
        
        public int getGroup() {
            return _group;
        }

        public int getWeight() {
            return _weight;
        }

        @Override
        public String toString() {
            String s = _id;
            if (_group > DEFAULT_GROUP || _weight > DEFAULT_WEIGHT) {
                s += " (";
                String sep = "";
                if (_group > DEFAULT_GROUP) {
                    s += (sep+ _group +"g");
                    sep = ", ";
                }
                if (_weight > DEFAULT_WEIGHT) {
                    s += (sep+_weight+"w");
                    sep = ", ";
                }
                s += ")";
            }
            return s;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Step step = (Step) o;
            return _id.equals(step._id)
                    && _group== step._group
                    && _weight == step._weight;
        }

        @Override
        public int hashCode() {
            return Objects.hash(_id, _group, _weight);
        }
    }

    /**
     * The name of the step provider ({@link ModelingStepsProvider}): this is usually also the name of the algorithm.
     */
    String _name;
    /**
     * An alias representing a predefined list of steps to be executed.
     */
    Alias _alias;
    /**
     * The list of steps to be executed.
     */
    Step[] _steps;

    public StepDefinition() { /* for autofill from schema */ }

    public StepDefinition(String name) {
        this(name, Alias.all);
    }

    public StepDefinition(String name, Alias alias) {
        _name = name;
        _alias = alias;
    }

    public StepDefinition(String name, String[] ids) {
        _name = name;
        _steps = new Step[ids.length];
        for (int i=0; i<ids.length; i++) _steps[i] = new Step(ids[i]);
    }

    public StepDefinition(String name, Step[] steps) {
        _name = name;
        _steps = steps;
    }
    
    public String getName() {
        return _name;
    }
    
    public Alias getAlias() {
        return _alias;
    }
    
    public List<Step> getSteps() {
        return _steps == null ? Collections.emptyList() : Collections.unmodifiableList(Arrays.asList(_steps));
    } 

    @Override
    public String toString() {
        return "{"+_name+" : "+(_steps == null ? _alias : Arrays.toString(_steps))+"}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StepDefinition that = (StepDefinition) o;
        return _name.equals(that._name)
                && _alias == that._alias
                && Arrays.equals(_steps, that._steps);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(_name, _alias);
        result = 31 * result + Arrays.hashCode(_steps);
        return result;
    }
}
