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

    train = h2o.create_frame(**dataset_params)

    print "Training dataset:"
    print train

    # Save dataset to results directory
    results_dir = pyunit_utils.locate("results")
    h2o.download_csv(train,os.path.join(results_dir,"kmeans_dynamic_training_dataset.log"))

    # Generate random parameters
    params = {}
    params['k'] = random.sample(range(1,10),1)[0]
    if random.randint(0,1): params['max_iterations'] = random.sample(range(1,1000),1)[0]
    if random.randint(0,1): params['standardize'] = random.sample([True, False],1)[0]
    if random.randint(0,1): params['seed'] = random.sample(range(1,1000),1)[0]
    if random.randint(0,1): params['init'] = random.sample(['Random','PlusPlus','Furthest'],1)[0]
    print "Parameter list: {0}".format(params)

    x = train.names
    x.remove("response")
    y = "response"

    pyunit_utils.javapredict(algo="kmeans", equality=None, train=train, test=None, x=x, y=y, compile_only=True, **params)

if __name__ == "__main__":
    pyunit_utils.standalone_test(javapredict_dynamic_data)
else:
    javapredict_dynamic_data()
