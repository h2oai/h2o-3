from builtins import range
import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import random
import re
from h2o.estimators.deeplearning import H2ODeepLearningEstimator

# number of test dataset rows
NTESTROWS = 200
MAXLAYERS = 8
MAXNODESPERLAYER = 20
TMPDIR = ""
MOJONAME = ""


def deeplearning_mojo_pojo():
    problem_types = ["regression", "binomial", "multinomial"]
    auto_encoder_on = [False]
    missing_values = ['Skip', 'MeanImputation']
    all_factors = [False, True]
    for problem in problem_types:
        # generate random dataset
        df = random_dataset(problem)
        train = df[NTESTROWS:, :]
        test = df[:NTESTROWS, :]
        x = list(set(df.names) - {"response"})

        for encoder_on in auto_encoder_on:
            # maxout not support for autoEncoder
            if encoder_on:
                all_act = ["rectifier", "tanh_with_dropout", "rectifier_with_dropout", "tanh"]
            else:
                all_act = ["maxout", "rectifier","maxout_with_dropout", "tanh_with_dropout", "rectifier_with_dropout", 
                           "tanh"]

            for act_fun in all_act:
                for missing_values_handling in missing_values:
                    for set_all_factor in all_factors:
                        print("AutoEncoderOn is: {0} and problem type is: {1}".format(encoder_on, problem))
                        print("Activation function: {0}, missing value handling: {1}, skippAllFactor: "
                              "{2}".format(act_fun, missing_values_handling, set_all_factor))
                        run_comparison_tests(encoder_on, act_fun, missing_values_handling, set_all_factor, train, test, 
                                             x)


def run_comparison_tests(auto_encoder, act_fun, missing_values_handling, set_all_factor, train, test, x):
    # set deeplearning model parameters
    params = set_params(act_fun, missing_values_handling, set_all_factor, auto_encoder) 
    
    if auto_encoder:
        try:
            # build and save mojo model
            deeplearning_model = build_save_model(params, x, train) 
        except Exception as err:
            if not("Trying to predict with an unstable model" in err.args[0]):
                raise Exception('Deeplearning autoencoder model failed to build.  Fix it.')
            return
    else:
        # build and save mojo model
        deeplearning_model = build_save_model(params, x, train) 

    # save test file, h2o predict/mojo use same file
    h2o.download_csv(test[x], os.path.join(TMPDIR, 'in.csv'))  
    # load model and perform predict
    pred_h2o, pred_mojo = pyunit_utils.mojo_predict(deeplearning_model, TMPDIR, MOJONAME)  
    pred_pojo = pyunit_utils.pojo_predict(deeplearning_model, TMPDIR, MOJONAME)
    # save model for debugging
    h2o.save_model(deeplearning_model, path=TMPDIR, force=True)  
    print("Comparing mojo predict and h2o predict...")
    pyunit_utils.compare_frames_local_onecolumn_NA(pred_h2o, pred_mojo, prob=1, tol=1e-10)
    print("Comparing pojo predict and h2o predict...")
    pyunit_utils.compare_frames_local_onecolumn_NA(pred_mojo, pred_pojo, prob=1, tol=1e-10)


def set_params(act_fun, missing_values_handling, set_all_factor, enable_encoder=False):
    dropOutRatio = 0.25
    # generate random size layers
    hiddens, hidden_dropout_ratios = random_network_size(act_fun)
    seed = 12345
    if 'dropout' in act_fun:
        params = {'hidden': hiddens, 'standardize': True,
                  'missing_values_handling': missing_values_handling, 'activation': act_fun,
                  'use_all_factor_levels': set_all_factor,
                  'hidden_dropout_ratios': hidden_dropout_ratios,
                  'input_dropout_ratio': dropOutRatio,
                  'autoencoder': enable_encoder,
                  'seed': seed, 'reproducible': True
                  }
    else:
        params = {'hidden': hiddens, 'standardize': True,
                  'missing_values_handling': missing_values_handling, 'activation': act_fun,
                  'use_all_factor_levels': set_all_factor,
                  'input_dropout_ratio': dropOutRatio,
                  'autoencoder': enable_encoder,
                  'seed': seed, 'reproducible': True
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
    df = h2o.create_frame(rows=5000 + NTESTROWS, cols=5,
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

