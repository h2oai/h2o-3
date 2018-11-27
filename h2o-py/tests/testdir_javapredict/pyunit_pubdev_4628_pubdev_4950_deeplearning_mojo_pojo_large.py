from builtins import range
import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import random
from random import randint
import re
from h2o.estimators.deeplearning import H2ODeepLearningEstimator

NTESTROWS = 200    # number of test dataset rows
MAXLAYERS = 8
MAXNODESPERLAYER = 20
TMPDIR = ""
MOJONAME = ""

def deeplearning_mojo_pojo():
    h2o.remove_all()
    problemtypes = ["regression", "binomial", "multinomial"]
    autoEncoderOn = [True, False]

    for encoderOn in autoEncoderOn:
        for problem in problemtypes:
            print("AutoEncoderOn is: {0} and problem type is: {1}".format(encoderOn, problem))
            random.seed(9876) # set python random seed
            runComparisonTests(encoderOn, problem)


def runComparisonTests(autoEncoder, probleyType):
    params = set_params(autoEncoder)   # set deeplearning model parameters
    df = random_dataset(probleyType)       # generate random dataset
    train = df[NTESTROWS:, :]
    test = df[:NTESTROWS, :]
    x = list(set(df.names) - {"response"})

    deeplearningModel = build_save_model(params, x, train) # build and save mojo model
    h2o.download_csv(test[x], os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
    pred_h2o, pred_mojo = pyunit_utils.mojo_predict(deeplearningModel, TMPDIR, MOJONAME)  # load model and perform predict
    pred_pojo = pyunit_utils.pojo_predict(deeplearningModel, TMPDIR, MOJONAME)
    h2o.save_model(deeplearningModel, path=TMPDIR, force=True)  # save model for debugging
    print("Comparing mojo predict and h2o predict...")
    pyunit_utils.compare_frames_local_onecolumn_NA(pred_h2o, pred_mojo, prob=1, tol=1e-10)
    print("Comparing pojo predict and h2o predict...")
    pyunit_utils.compare_frames_local_onecolumn_NA(pred_mojo, pred_pojo, prob=1, tol=1e-10)

def set_params( enableEncoder=False):
    allAct = ["maxout", "rectifier", "maxout_with_dropout", "tanh_with_dropout", "rectifier_with_dropout", "tanh"]
    problemType = ["binomial", "multinomial", "regression"]
    missingValues = ['Skip', 'MeanImputation']
    allFactors = [True, False]
    if (enableEncoder): # maxout not support for autoEncoder
        allAct = ["rectifier", "tanh_with_dropout", "rectifier_with_dropout", "tanh"]
    actFunc = allAct[randint(0,len(allAct)-1)]
    missing_values = missingValues[randint(0, len(missingValues)-1)]

    hiddens, hidden_dropout_ratios = random_networkSize(actFunc)    # generate random size layers
    params = {}
    if ('dropout') in actFunc:
        params = {'hidden': hiddens, 'standardize': True,
                  'missing_values_handling': missing_values, 'activation': actFunc,
                  'use_all_factor_levels': allFactors[randint(0, len(allFactors) - 1)],
                  'hidden_dropout_ratios': hidden_dropout_ratios,
                  'input_dropout_ratio': random.uniform(0, 0.5),
                  'autoencoder':enableEncoder,
                  'seed':1234
                  }
    else:
        params = {'hidden': hiddens, 'standardize': True,
                  'missing_values_handling': missing_values, 'activation': actFunc,
                  'use_all_factor_levels': allFactors[randint(0, len(allFactors) - 1)],
                  'input_dropout_ratio': random.uniform(0, 0.5),
                  'autoencoder':enableEncoder,
                  'seed':1234
                  }
    print(params)
    return params

def build_save_model(params, x, train):
    global TMPDIR
    global MOJONAME
    # build a model
    model = H2ODeepLearningEstimator(**params)
    if params['autoencoder']:
        model.train(x=x, training_frame=train)
    else:
        model.train(x=x, y="response", training_frame=train)
    # save model
    regex = re.compile("[+\\-* !@#$%^&()={}\\[\\]|;:'\"<>,.?/]")
    MOJONAME = regex.sub("_", model._id)

    print("Downloading Java prediction model code from H2O")
    TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", MOJONAME))
    os.makedirs(TMPDIR)
    model.download_mojo(path=TMPDIR)    # save mojo
    return model

# generate random neural network architecture
def random_networkSize(actFunc):
    no_hidden_layers = randint(1, MAXLAYERS)
    hidden = []
    hidden_dropouts = []
    for k in range(1, no_hidden_layers+1):
        hidden.append(randint(1,MAXNODESPERLAYER))
        if 'dropout' in actFunc.lower():
            hidden_dropouts.append(random.uniform(0,0.5))

    return hidden, hidden_dropouts

# generate random dataset
def random_dataset(response_type="regression", verbose=True):
    """Create and return a random dataset."""
    if verbose: print("\nCreating a dataset for a %s problem:" % response_type)
    fractions = {k + "_fraction": random.random() for k in "real categorical integer time string binary".split()}
    fractions["string_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["binary_fraction"] /= 3
    fractions["time_fraction"] /= 2

    sum_fractions = sum(fractions.values())
    for k in fractions:
        fractions[k] /= sum_fractions
    response_factors = (1 if response_type == "regression" else
                        2 if response_type == "binomial" else
                        random.randint(3, 10))
    df = h2o.create_frame(rows=random.randint(5000, 10000) + NTESTROWS, cols=random.randint(3, 20),
                          missing_fraction=random.uniform(0, 0.05),
                          has_response=True, response_factors=response_factors, positive_response=True, factors=10,
                          seed=1234, **fractions)
    if verbose:
        print()
        df.show()
    return df

if __name__ == "__main__":
    pyunit_utils.standalone_test(deeplearning_mojo_pojo)
else:
    deeplearning_mojo_pojo()
