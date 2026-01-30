from builtins import range
import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import random
import re
from h2o.estimators.deeplearning import H2ODeepLearningEstimator

NTESTROWS = 100    # number of test dataset rows
MAXLAYERS = 4
MAXNODESPERLAYER = 10
TMPDIR = ""
MOJONAME = ""

def deeplearning_mojo_pojo():
    problemtypes = ["regression", "binomial", "multinomial"]
    autoEncoderOn = [False]
    missingValues = ['Skip', 'MeanImputation']
    allFactors = [False, True]
    for problem in problemtypes:
        df = random_dataset(problem)       # generate random dataset
        train = df[NTESTROWS:, :]
        test = df[:NTESTROWS, :]
        x = list(set(df.names) - {"response"})

        for encoderOn in autoEncoderOn:
            if (encoderOn): # maxout not support for autoEncoder
                allAct = ["rectifier", "tanh_with_dropout", "rectifier_with_dropout", "tanh"]
            else:
                allAct = ["maxout", "rectifier","maxout_with_dropout", "tanh_with_dropout",
                          "rectifier_with_dropout", "tanh"]

            for actFun in allAct:
                for missingValuesHandling in missingValues:
                    for setAllFactor in allFactors:
                        print("AutoEncoderOn is: {0} and problem type is: {1}".format(encoderOn, problem))
                        print("Activation function: {0}, missing value handling: {1}, skippAllFactor: "
                              "{2}".format(actFun, missingValuesHandling, setAllFactor))
                        run_comparison_tests(encoderOn, actFun, missingValuesHandling, setAllFactor, train,
                                             test, x)


def run_comparison_tests(autoEncoder, actFun, missingValuesHandling, setAllFactor, train, test, x):
    params = set_params(actFun, missingValuesHandling, setAllFactor, autoEncoder)   # set deeplearning model parameters
    
    if autoEncoder:
        try:
            deeplearningModel = build_save_model(params, x, train) # build and save mojo model
        except Exception as err:
            if not("Trying to predict with an unstable model" in err.args[0]):
                raise Exception('Deeplearning autoencoder model failed to build.  Fix it.')
            return
    else:
        deeplearningModel = build_save_model(params, x, train) # build and save mojo model
        
    h2o.download_csv(test[x], os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
    pred_h2o, pred_mojo = pyunit_utils.mojo_predict(deeplearningModel, TMPDIR, MOJONAME)  # load model and perform predict
    pred_pojo = pyunit_utils.pojo_predict(deeplearningModel, TMPDIR, MOJONAME)
    h2o.save_model(deeplearningModel, path=TMPDIR, force=True)  # save model for debugging
    print("Comparing mojo predict and h2o predict...")
    pyunit_utils.compare_frames_local_onecolumn_NA(pred_h2o, pred_mojo, prob=1, tol=1e-10)
    print("Comparing pojo predict and h2o predict...")
    pyunit_utils.compare_frames_local_onecolumn_NA(pred_mojo, pred_pojo, prob=1, tol=1e-10)


def set_params(actFun, missingValuesHandling, setAllFactor, enableEncoder=False):
    dropOutRatio = 0.25
    hiddens, hidden_dropout_ratios = random_network_size(actFun)    # generate random size layers
    seed = 12345
    if ('dropout') in actFun:
        params = {'hidden': hiddens, 'standardize': True,
                  'missing_values_handling': missingValuesHandling, 'activation': actFun,
                  'use_all_factor_levels': setAllFactor,
                  'hidden_dropout_ratios': hidden_dropout_ratios,
                  'input_dropout_ratio': dropOutRatio,
                  'autoencoder':enableEncoder,
                  'seed':seed, 'reproducible':True
                  }
    else:
        params = {'hidden': hiddens, 'standardize': True,
                  'missing_values_handling': missingValuesHandling, 'activation': actFun,
                  'use_all_factor_levels': setAllFactor,
                  'input_dropout_ratio': dropOutRatio,
                  'autoencoder':enableEncoder,
                  'seed':seed, 'reproducible':True
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
def random_network_size(actFunc):
    no_hidden_layers = 5
    hidden = []
    hidden_dropouts = []
    for k in range(1, no_hidden_layers+1):
        hidden.append(8)
        if 'dropout' in actFunc.lower():
            hidden_dropouts.append(0.25)

    return hidden, hidden_dropouts


# generate random dataset
def random_dataset(response_type="regression", verbose=True):
    """Create and return a random dataset."""
    if verbose: print("\nCreating a dataset for a %s problem:" % response_type)
    fractions = {k + "_fraction": 0.5 for k in "real categorical integer time string binary".split()}
    fractions["string_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["binary_fraction"] /= 3
    fractions["time_fraction"] /= 2

    sum_fractions = sum(fractions.values())
    for k in fractions:
        fractions[k] /= sum_fractions
    response_factors = (1 if response_type == "regression" else
                        2 if response_type == "binomial" else
                        random.randint(3, 10))
    df = h2o.create_frame(rows=1000 + NTESTROWS, cols=5,
                          missing_fraction=0.025,
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
