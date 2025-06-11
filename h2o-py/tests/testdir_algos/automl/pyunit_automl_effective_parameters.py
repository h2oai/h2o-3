import sys, os
import random

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import get_partitioned_model_names, import_dataset


def check_model_property(models, prop_name, present=True, actual_value=None, default_value=None, input_value=None):
    for m in models:
        model = h2o.get_model(m) if isinstance(m, str) else m
        if present:
            assert prop_name in model.params.keys(), \
                "missing {prop} in model {model}".format(prop=prop_name, model=model.model_id)
            assert actual_value is None or model.params[prop_name]['actual'] == actual_value, \
                "actual value for {prop} in model {model} is {val}, expected {exp}".format(prop=prop_name, model=model.model_id, val=model.params[prop_name]['actual'], exp=actual_value)
            assert default_value is None or model.params[prop_name]['default'] == default_value, \
                "default value for {prop} in model {model} is {val}, expected {exp}".format(prop=prop_name, model=model.model_id, val=model.params[prop_name]['default'], exp=default_value)
            assert input_value is None or model.params[prop_name]['input'] == input_value, \
                "input value for {prop} in model {model} is {val}, expected {exp}".format(prop=prop_name, model=model.model_id, val=model.params[prop_name]['input'], exp=input_value)
        else:
            assert prop_name not in model.params.keys(), "unexpected {prop} in model {model}".format(prop=prop_name, model=model.model_id)


def test_actual_default_input_stopping_rounds():
    ds = import_dataset('regression')
    exclude_algos = ["DeepLearning", "GLM"]
    aml = H2OAutoML(project_name="aml_actual_default_input_stopping_rounds",
                    exclude_algos=exclude_algos,
                    max_models=10,
                    seed=1764)
    aml.train(y=ds.target, training_frame=ds.train)

    models = get_partitioned_model_names(aml.leaderboard)
    # when using cv, all cv models are trained with the stopping_rounds = 3 (default), but the final model resets stopping_rounds to 0 and use e. g. average ntrees, iterations...
    check_model_property(models.base, 'stopping_rounds', True, 0, 0, 3)


def test_actual_stopping_criteria_propagated_to_models():
    ds = import_dataset('regression')
    aml = H2OAutoML(project_name="aml_actual_stopping_criteria_propagated",
                    max_models=10,
                    stopping_rounds=2,
                    stopping_tolerance=0.5,
                    nfolds=0,  # disable cv to test stopping criteria on final model more easily
                    seed=1764)
    aml.train(y=ds.target, training_frame=ds.train, blending_frame=ds.valid, leaderboard_frame=ds.test)

    models = get_partitioned_model_names(aml.leaderboard)
    
    def partition(predicate, iterable):
        truthy, falsy = [], []
        for i in iterable:
            if predicate(i):
                truthy.append(i)
            else:
                falsy.append(i)
        return truthy, falsy
    
    base_models = [h2o.get_model(b) for b in models.base]
    base_glms, base_others = partition(lambda m: m.algo == 'glm', base_models)
    print([m.model_id for m in base_glms])
    print([m.model_id for m in base_others])
    check_model_property(base_others, 'stopping_rounds', True, 2, 0, 2)
    check_model_property(base_glms, 'stopping_rounds', True, 0, 0, 0)  # AutoML GLMs disable stopping_rounds due to lambda search
    check_model_property(base_others, 'stopping_tolerance', True, 0.5, None, 0.5)
    check_model_property(base_glms, 'stopping_tolerance', True, 0.001, 0.001, 0.001)
    check_model_property(base_glms, 'objective_epsilon', True, 0.5, -1, 0.5)
    
    metaleaners = [h2o.get_model(se).metalearner() for se in models.se]
    metaleaners_glms, metaleaners_others = partition(lambda m: m.algo == 'glm', metaleaners)
    print([m.model_id for m in metaleaners_glms])
    print([m.model_id for m in metaleaners_others])
    check_model_property(metaleaners_others, 'stopping_rounds', True, 2, 0, 2)
    check_model_property(metaleaners_glms, 'stopping_rounds', True, 0, 0, 0)
    check_model_property(metaleaners_glms, 'objective_epsilon', True, 0.5, -1, 0.5)
    check_model_property(metaleaners, 'stopping_tolerance', True, 0.5, None, 0.5)

    
pu.run_tests([
    test_actual_default_input_stopping_rounds,
    test_actual_stopping_criteria_propagated_to_models
])
