from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import os

# PUBDEV-2282
def javapredict_dynamic_data():

    dataset_params = {}
    dataset_params['rows'] = 13183
    dataset_params['cols'] = 13
    dataset_params['categorical_fraction'] = 0.4
    dataset_params['integer_fraction'] = 0.3
    dataset_params['missing_fraction'] = 0.27539154084819495
    dataset_params['has_response'] = True
    dataset_params['randomize'] = True
    dataset_params['factors'] = 819
    print("Dataset parameters: {0}".format(dataset_params))

    problem = 2
    print("Model-building exercise (0:regression, 1:binomial, 2:multinomial): {0}".format(problem))
    if   problem == 'binomial':    dataset_params['response_factors'] = 2
    elif problem == 'regression':  dataset_params['response_factors'] = 1
    else:                          dataset_params['response_factors'] = 16


    train = h2o.create_frame(**dataset_params)
    if problem == 'binomial' or problem == 'multinomial': train['response'] = train['response'].asfactor()
    results_dir = pyunit_utils.locate("results")
    h2o.download_csv(train["response"],os.path.join(results_dir,"drf_dynamic_preimputed_response.log"))
    train.impute("response", method="mode")
    print("Training dataset:")
    print(train)

    # Save dataset to results directory
    h2o.download_csv(train,os.path.join(results_dir,"drf_dynamic_training_dataset.log"))

    params = {}
    params['nbins'] = 5
    params['min_rows'] = 7
    params['mtries'] = 4
    params['sample_rate'] = 0.7867986759373544
    params['seed'] = 1304644573760597606
    print("Parameter list: {0}".format(params))

    x = list(range(1,train.ncol))
    y = "response"

    pyunit_utils.javapredict(algo="random_forest", equality=None, train=train, test=None, x=x, y=y, compile_only=True,
                             **params)

if __name__ == "__main__":
    pyunit_utils.standalone_test(javapredict_dynamic_data)
else:
    javapredict_dynamic_data()
