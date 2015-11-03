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

    append_response = False
    distribution = random.sample(['bernoulli','multinomial','gaussian','poisson','gamma'], 1)[0]
    if   distribution == 'bernoulli': dataset_params['response_factors'] = 2
    elif distribution == 'gaussian':  dataset_params['response_factors'] = 1
    elif distribution == 'multinomial': dataset_params['response_factors'] = random.randint(3,100)
    else:
        dataset_params['has_response'] = False
        response = h2o.H2OFrame([random.randint(1,1000) for r in range(0,dataset_params['rows'])])
        append_response = True
    print "Distribution: {0}".format(distribution)

    train = h2o.create_frame(**dataset_params)
    if append_response:
        train = response.cbind(train)
        train.set_name(0,"response")
    if distribution == 'bernoulli' or distribution == 'multinomial': train['response'] = train['response'].asfactor()
    results_dir = pyunit_utils.locate("results")
    h2o.download_csv(train["response"],os.path.join(results_dir,"dl_dynamic_preimputed_response.log"))
    train = train.impute("response", method="mode")
    print "Training dataset:"
    print train

    # Save dataset to results directory
    h2o.download_csv(train,os.path.join(results_dir,"dl_dynamic_training_dataset.log"))

    # Generate random parameters
    params = {}
    if random.randint(0,1): params['activation'] = random.sample(["Rectifier", "Tanh", "TanhWithDropout",
                                                                  "RectifierWithDropout", "MaxoutWithDropout"],1)[0]
    if random.randint(0,1): params['epochs'] = random.sample(range(1,10),1)[0]
    if random.randint(0,1):
        h = random.randint(1,21)
        params['hidden'] = [h for x in range(random.randint(2,3))]
    params['distribution'] = distribution
    params['l1'] = random.random()
    print "Parameter list: {0}".format(params)

    x = train.names
    x.remove("response")
    y = "response"

    pyunit_utils.javapredict(algo="deeplearning", equality=None, train=train, test=None, x=x, y=y, compile_only=True,
                             **params)

if __name__ == "__main__":
    pyunit_utils.standalone_test(javapredict_dynamic_data)
else:
    javapredict_dynamic_data()
