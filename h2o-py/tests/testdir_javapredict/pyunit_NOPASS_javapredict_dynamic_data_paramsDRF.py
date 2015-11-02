import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import random
import os

def javapredict_dynamic_data():

    # Generate random dataset
    dataset_params = {}
    dataset_params['rows'] = random.sample(range(5000,15001),1)[0]
    dataset_params['cols'] = random.sample(range(10,21),1)[0]
    dataset_params['categorical_fraction'] = round(random.random(),1)
    left_over = (1 - dataset_params['categorical_fraction'])
    dataset_params['integer_fraction'] = round(left_over - round(random.uniform(0,left_over),1),1)
    if dataset_params['integer_fraction'] + dataset_params['categorical_fraction'] == 1:
        if dataset_params['integer_fraction'] > dataset_params['categorical_fraction']:
            dataset_params['integer_fraction'] = dataset_params['integer_fraction'] - 0.1
        else:
            dataset_params['categorical_fraction'] = dataset_params['categorical_fraction'] - 0.1
    dataset_params['missing_fraction'] = random.uniform(0,0.5)
    dataset_params['has_response'] = True
    dataset_params['randomize'] = True
    dataset_params['factors'] = random.randint(2,2000)
    print "Dataset parameters: {0}".format(dataset_params)

    problem = random.sample(range(0,3),1)
    print "Model-building exercise (0:regression, 1:binomial, 2:multinomial): {0}".format(problem)
    if   problem == 'binomial':    dataset_params['response_factors'] = 2
    elif problem == 'regression':  dataset_params['response_factors'] = 1
    else:                          dataset_params['response_factors'] = random.randint(3,100)


    train = h2o.create_frame(**dataset_params)
    if problem == 'binomial' or problem == 'multinomial': train['response'] = train['response'].asfactor()
    results_dir = pyunit_utils.locate("results")
    h2o.download_csv(train["response"],os.path.join(results_dir,"drf_dynamic_preimputed_response.log"))
    train = train.impute("response", method="mode")
    print "Training dataset:"
    print train

    # Save dataset to results directory
    h2o.download_csv(train,os.path.join(results_dir,"drf_dynamic_training_dataset.log"))

    # Generate random parameters
    params = {}
    if random.randint(0,1): params['ntrees'] = random.sample(range(1,21),1)[0]
    if random.randint(0,1): params['max_depth'] = random.sample(range(1,11),1)[0]
    if random.randint(0,1): params['min_rows'] = random.sample(range(1,11),1)[0]
    if random.randint(0,1): params['nbins'] = random.sample(range(2,21),1)[0]
    if random.randint(0,1): params['nbins_cats'] = random.sample(range(2,1025),1)[0]
    if random.randint(0,1): params['mtries'] = random.sample(range(1,dataset_params['cols']+1),1)[0]
    if random.randint(0,1): params['sample_rate'] = random.random()
    print "Parameter list: {0}".format(params)

    x = range(1,train.ncol)
    y = "response"

    pyunit_utils.javapredict(algo="random_forest", equality=None, train=train, test=None, x=x, y=y, compile_only=True,
                             **params)

if __name__ == "__main__":
    pyunit_utils.standalone_test(javapredict_dynamic_data)
else:
    javapredict_dynamic_data()
