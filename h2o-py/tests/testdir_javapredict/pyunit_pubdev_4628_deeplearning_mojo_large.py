from builtins import range
import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import random
from random import randint

NTESTROWS = 1000    # number of test dataset rows
MAXLAYERS = 8
MAXNODESPERLAYER = 20

def deeplearning_mojo():

    # h2o_data = h2o.upload_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    # h2o_data.summary()
    # parmsGLM = {'family':'binomial', 'alpha':0.5, 'standardize':True}
    # pyunit_utils.javapredict("glm", "class", h2o_data, h2o_data, list(range(2, h2o_data.ncol)), 1, pojo_model=False,
    #                          **parmsGLM)

    allAct = ["maxout", "rectifier", "maxout_with_dropout", "tanh_with_dropout", "rectifier_with_dropout", "tanh"]
    problemType = ["binomial", "multinomial", "regression"]
    missingValues = ['Skip', 'MeanImputation']
    allFactors = [True, False]
    categoricalEncodings = ['auto', 'one_hot_internal', 'binary', 'eigen']

    problem = problemType[randint(0,len(problemType)-1)]
    actFunc = allAct[randint(0,len(allAct)-1)]
    missing_values = missingValues[randint(0, len(missingValues)-1)]
    cateEn = categoricalEncodings[randint(0, len(categoricalEncodings)-1)]


    if (problem=='regression'):
        loss = 'Automatic'
        prob = 'numeric'
    else:
        loss = 'CrossEntropy'
        prob = 'class'

    hiddens, hidden_dropout_ratios = random_networkSize(actFunc)    # generate random size layers
    params = {}
    if ('dropout') in actFunc:
        params = {'loss': loss, 'hidden': hiddens, 'standardize': True,
                  'missing_values_handling': missing_values, 'activation': actFunc,
                  'use_all_factor_levels': allFactors[randint(0, len(allFactors) - 1)],
                  'hidden_dropout_ratios': hidden_dropout_ratios,
                  'input_dropout_ratio': random.uniform(0, 0.5),
                  'categorical_encoding':cateEn
                  }
    else:
        params = {'loss': loss, 'hidden': hiddens, 'standardize': True,
                  'missing_values_handling': missing_values, 'activation': actFunc,
                  'use_all_factor_levels': allFactors[randint(0, len(allFactors) - 1)],
                  'input_dropout_ratio': random.uniform(0, 0.5),
                  'categorical_encoding':cateEn
                  }
    df = random_dataset(problem)       # generate random dataset
    train = df[NTESTROWS:, :]
    test = df[:NTESTROWS, :]
    print(params)
    pyunit_utils.javapredict("deeplearning", prob, train, test, list(set(df.names) - {"response"}),
                              "response", pojo_model=False, save_model=False, **params) # want to build mojo

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
    # fractions["categorical_fraction"] = 0
    sum_fractions = sum(fractions.values())
    for k in fractions:
        fractions[k] /= sum_fractions
    response_factors = (1 if response_type == "regression" else
                        2 if response_type == "binomial" else
                        random.randint(10, 30))
    df = h2o.create_frame(rows=random.randint(15000, 25000) + NTESTROWS, cols=random.randint(20, 100),
                          missing_fraction=random.uniform(0, 0.05),
                          has_response=True, response_factors=response_factors, positive_response=True,
                          **fractions)
    if verbose:
        print()
        df.show()
    return df

if __name__ == "__main__":
    pyunit_utils.standalone_test(deeplearning_mojo)
else:
    deeplearning_mojo()
