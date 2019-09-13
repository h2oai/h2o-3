package ai.h2o.automl;

import water.Iced;

import java.util.Arrays;

public class StepDefinition extends Iced<StepDefinition> {

    public enum Alias { all, defaults, grids }

    public static class Step extends Iced<Step> {
        static final int DEFAULT_WEIGHT = -1;  // means that the Step will use the default weight set by the TrainingStep

        String _id;
        int _weight = DEFAULT_WEIGHT;  // share of time dedicated

        public Step(String _id) {
            this._id = _id;
        }

        public Step(String id, int weight) {
            assert weight > DEFAULT_WEIGHT : "weight should be >= 0";
            this._id = id;
            this._weight = weight;
        }

        @Override
        public String toString() {
            return _id+(_weight > DEFAULT_WEIGHT ? " ("+ _weight +")" : "");
        }
    }

    String _name;
    Alias _alias;
    Step[] _steps;

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

    @Override
    public String toString() {
        return "{"+_name+" : "+(_steps == null ? _alias : Arrays.toString(_steps))+"}";
    }
}
