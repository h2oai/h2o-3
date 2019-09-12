package ai.h2o.automl;

import water.Iced;

import java.util.Arrays;

public class StepDefinition extends Iced<StepDefinition> {

    public enum Alias { all, defaults, grids }

    String _name;
    Alias _alias;
    String[] _ids;

    public StepDefinition(String name, Alias alias) {
        _name = name;
        _alias = alias;
    }

    public StepDefinition(String name, String[] ids) {
        _name = name;
        _ids = ids;
    }

    @Override
    public String toString() {
        return "{"+_name+" : "+(_ids == null ? _alias : Arrays.toString(_ids))+"}";
    }
}
