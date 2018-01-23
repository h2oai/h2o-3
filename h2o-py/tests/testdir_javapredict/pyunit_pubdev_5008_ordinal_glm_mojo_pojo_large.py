from builtins import range
import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import random
from random import randint
import re
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

NTESTROWS = 200    # number of test dataset rows
MAXLAYERS = 8
MAXNODESPERLAYER = 20
TMPDIR = ""
MOJONAME = ""
PROBLEM="multinomial"

def glm_ordinal_mojo_pojo():
    h2o.remove_all()
    params = set_params()   # set deeplearning model parameters
    df = random_dataset(PROBLEM)       # generate random dataset
    train = df[NTESTROWS:, :]
    test = df[:NTESTROWS, :]
    x = list(set(df.names) - {"response"})


    try:
        glmOrdinalModel = build_save_model(params, x, train, "response") # build and save mojo model
        h2o.download_csv(test[x], os.path.join(TMPDIR, 'in.csv'))  # save test file, h2o predict/mojo use same file
        pred_h2o, pred_mojo = pyunit_utils.mojo_predict(glmOrdinalModel, TMPDIR, MOJONAME)  # load model and perform predict
        h2o.download_csv(pred_h2o, os.path.join(TMPDIR, "h2oPred.csv"))
        pred_pojo = pyunit_utils.pojo_predict(glmOrdinalModel, TMPDIR, MOJONAME)
        print("Comparing mojo predict and h2o predict...")
        pyunit_utils.compare_frames_local(pred_h2o, pred_mojo, 0.1, tol=1e-10)    # make sure operation sequence is preserved from Tomk        h2o.save_model(glmOrdinalModel, path=TMPDIR, force=True)  # save model for debugging
        print("Comparing pojo predict and h2o predict...")
        pyunit_utils.compare_frames_local(pred_mojo, pred_pojo, 0.1, tol=1e-10)
    except Exception as ex:
        print("***************  ERROR and type is ")
        print(str(type(ex)))
        print(ex)
        if "AssertionError" in str(type(ex)):   # only care if there is an AssertionError, ignore the others
            sys.exit(1)

def set_params():
    global PROBLEM
    #missingValues = ['Skip', 'MeanImputation']
    missingValues = ['MeanImputation']
    PROBLEM = "multinomial"
    print("PROBLEM is {0}".format(PROBLEM))
    missing_values = missingValues[randint(0, len(missingValues)-1)]

    params = {'missing_values_handling': missing_values, 'family':"ordinal"}
    print(params)
    return params

def build_save_model(params, x, train, respName):
    global TMPDIR
    global MOJONAME
    # build a model
    model = H2OGeneralizedLinearEstimator(**params)
    model.train(x=x, y=respName, training_frame=train)
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
    response_factors = random.randint(3, 10)
    df = h2o.create_frame(rows=random.randint(15000, 25000) + NTESTROWS, cols=random.randint(3, 20),
                          missing_fraction=0,
                          has_response=True, response_factors=response_factors, positive_response=True, factors=10,
                          **fractions)
    if verbose:
        print()
        df.show()
    return df

if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_ordinal_mojo_pojo)
else:
    glm_ordinal_mojo_pojo()
