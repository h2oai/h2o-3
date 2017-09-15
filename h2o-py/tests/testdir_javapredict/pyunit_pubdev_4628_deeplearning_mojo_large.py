from builtins import range
import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import random
from random import randint
import re
import subprocess
from subprocess import STDOUT,PIPE
from h2o.estimators.deeplearning import H2ODeepLearningEstimator

NTESTROWS = 200    # number of test dataset rows
MAXLAYERS = 8
MAXNODESPERLAYER = 20
TMPDIR = ""
POJONAME = ""
PROBLEM="regression"

def deeplearning_mojo():
    h2o.remove_all()

    params = set_params()   # set deeplearning model parameters

    df = random_dataset(PROBLEM)       # generate random dataset
    train = df[NTESTROWS:, :]
    test = df[:NTESTROWS, :]
    x = list(set(df.names) - {"response"})

    try:
        deeplearningModel = build_save_model(params, x, train) # build and save mojo model
        h2o.download_csv(test[x], os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
        pred_h2o = mojo_predict(deeplearningModel, x)  # load model and perform predict

        pred_mojo = h2o.import_file(os.path.join(TMPDIR, 'out_mojo.csv'), header=1)  # load mojo prediction into a frame and compare
        pyunit_utils.compare_numeric_frames(pred_h2o, pred_mojo, 0.1, tol=1e-4)
    except Exception as ex:
        print("***************  ERROR and type is ")
        print(str(type(ex)))
        if hasattr(ex, 'args') and type(ex.args[0])==type("what"):
            if "unstable model" not in ex.args[0] and "DistributedException" not in ex.args[0]:
                print(params)
                sys.exit(1)     # okay to encounter unstable model but nothingh else
            else:
                print("An unstable model is found and no mojo is built.")
        elif "EnvironmentError" in str(type(ex)):
            print("An unstable model is found and no mojo is built.")
        else:
            sys.exit(1)         # other errors occurred.


def set_params():
    global PROBLEM
    allAct = ["maxout", "rectifier", "maxout_with_dropout", "tanh_with_dropout", "rectifier_with_dropout", "tanh"]
    problemType = ["binomial", "multinomial", "regression"]
    missingValues = ['Skip', 'MeanImputation']
    allFactors = [True, False]
    categoricalEncodings = ['auto', 'one_hot_internal', 'binary', 'eigen']

    enableEncoder = allFactors[randint(0,len(allFactors)-1)]    # enable autoEncoder or not
    if (enableEncoder): # maxout not support for autoEncoder
        allAct = ["rectifier", "tanh_with_dropout", "rectifier_with_dropout", "tanh"]
    PROBLEM = problemType[randint(0,len(problemType)-1)]
    actFunc = allAct[randint(0,len(allAct)-1)]
    missing_values = missingValues[randint(0, len(missingValues)-1)]
    cateEn = categoricalEncodings[randint(0, len(categoricalEncodings)-1)]

    hiddens, hidden_dropout_ratios = random_networkSize(actFunc)    # generate random size layers
    params = {}
    if ('dropout') in actFunc:
        params = {'hidden': hiddens, 'standardize': True,
                  'missing_values_handling': missing_values, 'activation': actFunc,
                  'use_all_factor_levels': allFactors[randint(0, len(allFactors) - 1)],
                  'hidden_dropout_ratios': hidden_dropout_ratios,
                  'input_dropout_ratio': random.uniform(0, 0.5),
                  'categorical_encoding':cateEn,
                  'autoencoder':enableEncoder
                  }
    else:
        params = {'hidden': hiddens, 'standardize': True,
                  'missing_values_handling': missing_values, 'activation': actFunc,
                  'use_all_factor_levels': allFactors[randint(0, len(allFactors) - 1)],
                  'input_dropout_ratio': random.uniform(0, 0.5),
                  'categorical_encoding':cateEn,
                  'autoencoder':enableEncoder
                  }
    print(params)
    return params

# perform h2o predict and mojo predict.  Frame containing h2o prediction is returned and mojo predict is
# written to file.
def mojo_predict(model, x):
    newTest = h2o.import_file(os.path.join(TMPDIR, 'in.csv'))   # Make sure h2o and mojo use same in.csv
    predictions1 = model.predict(newTest)

    # load mojo and have it do predict
    outFileName = os.path.join(TMPDIR, 'out_mojo.csv')
    genJarDir = str.split(str(TMPDIR),'/')
    genJarDir = '/'.join(genJarDir[0:genJarDir.index('h2o-py')])    # locate directory of genmodel.jar
    java_cmd = ["java", "-ea", "-cp", os.path.join(genJarDir, "h2o-assemblies/genmodel/build/libs/genmodel.jar"),
                "-Xmx12g", "-XX:MaxPermSize=2g", "-XX:ReservedCodeCacheSize=256m", "hex.genmodel.tools.PredictCsv",
                "--input", os.path.join(TMPDIR, 'in.csv'), "--output",
                outFileName, "--mojo", os.path.join(TMPDIR, POJONAME)+".zip", "--decimal"]
    p = subprocess.Popen(java_cmd, stdout=PIPE, stderr=STDOUT)
    o, e = p.communicate()
    return predictions1

def build_save_model(params, x, train):
    global TMPDIR
    global POJONAME
    # build a model
    model = H2ODeepLearningEstimator(**params)
    if params['autoencoder']:
        model.train(x=x, training_frame=train)
    else:
        model.train(x=x, y="response", training_frame=train)
    # save model
    regex = re.compile("[+\\-* !@#$%^&()={}\\[\\]|;:'\"<>,.?/]")
    POJONAME = regex.sub("_", model._id)

    print("Downloading Java prediction model code from H2O")
    TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", POJONAME))
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
def random_dataset(response_type, verbose=True):
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
    df = h2o.create_frame(rows=random.randint(15000, 25000) + NTESTROWS, cols=random.randint(3, 20),
                          missing_fraction=random.uniform(0, 0.05),
                          has_response=True, response_factors=response_factors, positive_response=True, factors=10,
                          **fractions)
    if verbose:
        print()
        df.show()
    return df

if __name__ == "__main__":
    pyunit_utils.standalone_test(deeplearning_mojo)
else:
    deeplearning_mojo()
