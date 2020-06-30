from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
import random
import sys
from tests import pyunit_utils
from h2o.automl import H2OAutoML

# Random positive seed for AutoML
if sys.version_info[0] < 3: #Python 2
    automl_seed = random.randint(0, sys.maxint)
else: # Python 3
    automl_seed = random.randint(0, sys.maxsize)
print("Random Seed for pyunit_automl_leaderboard.py = " + str(automl_seed))


class Obj(object):
    pass

def get_partitioned_model_names(leaderboard):
    model_names = Obj()
    model_names.all = list(h2o.as_list(leaderboard['model_id'])['model_id'])
    model_names.se = [m for m in model_names.all if m.startswith('StackedEnsemble')]
    model_names.non_se = [m for m in model_names.all if m not in model_names.se]
    return model_names


def check_model_property(model_names, prop_name, present=True, actual_value=None, default_value=None, input_value=None):
    for mn in model_names:
        model = h2o.get_model(mn)
        if present:
            assert prop_name in model.params.keys(), \
                "missing {prop} in model {model}".format(prop=prop_name, model=mn)
            assert actual_value is None or model.params[prop_name]['actual'] == actual_value, \
                "actual value for {prop} in model {model} is {val}, expected {exp}".format(prop=prop_name, model=mn, val=model.params[prop_name]['actual'], exp=actual_value)
            assert default_value is None or model.params[prop_name]['default'] == default_value, \
                "default value for {prop} in model {model} is {val}, expected {exp}".format(prop=prop_name, model=mn, val=model.params[prop_name]['default'], exp=default_value)
            assert input_value is None or model.params[prop_name]['input'] == input_value, \
                "default value for {prop} in model {model} is {val}, expected {exp}".format(prop=prop_name, model=mn, val=model.params[prop_name]['input'], exp=input_value)
        else:
            assert prop_name not in model.params.keys(), "unexpected {prop} in model {model}".format(prop=prop_name, model=mn)



def test_actual_default_input_stopping_rounds():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/extdata/australia.csv"))
    target = 'runoffnew'
    exclude_algos = ["DeepLearning", "GLM"]
    aml = H2OAutoML(project_name="actual_default_input_stopping_rounds",
                    exclude_algos=exclude_algos,
                    max_models=10,
                    seed=automl_seed)
    aml.train(y=target, training_frame=train)

    non_se = get_partitioned_model_names(aml.leaderboard).non_se
    # when using cv, all cv models are trained with the stopping_rounds = 3 (default), but the final model resets stopping_rounds to 0 and use e. g. average ntrees, iterations...
    check_model_property(non_se, 'stopping_rounds', True, 0, 0, 3)



pyunit_utils.run_tests([
    test_actual_default_input_stopping_rounds,
])
