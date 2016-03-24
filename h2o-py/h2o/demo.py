from __future__ import print_function
from __future__ import absolute_import
from . import h2o
from .utils.shared_utils import _locate
import sys, os
import site


def system_file(name):
    """Finds the full file names of data files in the h2o package.

    Parameters
    ----------
      name : str
        Look for this file.

    Returns
    -------
      Absolute path to that file
    """
    h2o_data_paths = [os.path.join(sys.prefix, "h2o_data", name),
                      os.path.join(site.USER_BASE, "h2o_data", name)]

    h2o_data_path = None
    for path in h2o_data_paths:
        if os.path.exists(path):
            h2o_data_path = path
            break

    if h2o_data_path is None:
        if name == "prostate.csv":
            h2o_data_path = _locate(os.path.join("smalldata", "prostate", name))

    if h2o_data_path is None or not os.path.exists(h2o_data_path):
        raise ValueError

    return h2o_data_path


def demo(func=None, interactive=True, echo=True, test=False):
    """
    H2O built-in demo facility

    :param func: A string that identifies the h2o python function to demonstrate.
    :param interactive: If True, the user will be prompted to continue the demonstration after every segment.
    :param echo: If True, the python commands that are executed will be displayed.
    :param test: Used for pyunit testing. h2o.init() will not be called if set to True.
    :return:

    Example:
    >>> h2o.demo("gbm")
    """
    if   func == "gbm":          gbm_demo(         interactive, echo, test)
    elif func == "deeplearning": deeplearning_demo(interactive, echo, test)
    elif func == "glm":          glm_demo(         interactive, echo, test)
    else: print("Demo for {0} has not been implemented.".format(func))

def gbm_demo(interactive, echo, test):
    h2o_data_path = system_file("prostate.csv")

    demo_description = ['\n-----------------------------------------------------------------',
                        'This is a demo of H2O\'s GBM function.',
                        'It uploads a dataset to h2o, parses it, and shows a description.',
                        'Then, it divides the dataset into training and test sets, ',
                        'builds a GBM from the training set, and predicts on the test set.',
                        'Finally, default performance metrics are displayed.',
                        '-----------------------------------------------------------------']

    demo_commands = ['# Connect to h2o',
                     '>>> h2o.init()\n',
                     '\n# Upload the prostate dataset that comes included in the h2o python package',
                     '>>> prostate = h2o.upload_file(path = ' + h2o_data_path + '))\n',
                     '\n# Print a description of the prostate data',
                     '>>> prostate.summary()\n',
                     '\n# Randomly split the dataset into ~70/30, training/test sets',
                     '>>> r = prostate[0].runif()',
                     '>>> train = prostate[r < 0.70]',
                     '>>> valid = prostate[r >= 0.30]\n',
                     '\n# Convert the response columns to factors (for binary classification problems)',
                     '>>> train["CAPSULE"] = train["CAPSULE"].asfactor()',
                     '>>> test["CAPSULE"] = test["CAPSULE"].asfactor()\n',
                     '\n# Build a (classification) GBM',
                     '>>> from h2o.estimators.gbm import H2OGradientBoostingEstimator\n',
                     '>>> prostate_gbm = H2OGradientBoostingEstimator( distribution="bernoulli", ntrees=10, max_depth=8, min_rows=10,learn_rate=0.2)\n',
                     '>>> prostate_gbm.train(x=["AGE", "RACE", "PSA", "VOL", "GLEASON"], y="CAPSULE", training_frame=train)\n'
                     '\n# Show the model',
                     '>>> prostate_gbm.show()\n',
                     '\n# Predict on the test set and show the first ten predictions',
                     '>>> predictions = prostate_gbm.predict(test)',
                     '>>> predictions.show()\n',
                     '\n# Show default performance metrics',
                     '>>> performance = prostate_gbm.model_performance(test)',
                     '>>> performance.show()\n']

    for line in demo_description: print(line)
    print()

    echo_and_interact(demo_commands, interactive, echo)
    if not test: h2o.init()

    echo_and_interact(demo_commands, interactive, echo)
    prostate = h2o.upload_file(path=h2o_data_path)

    echo_and_interact(demo_commands, interactive, echo)
    prostate.summary()

    echo_and_interact(demo_commands, interactive, echo, npop=4)
    r = prostate[0].runif()
    train = prostate[r < 0.70]
    test = prostate[r >= 0.30]

    echo_and_interact(demo_commands, interactive, echo, npop=3)
    train["CAPSULE"] = train["CAPSULE"].asfactor()
    test["CAPSULE"] = test["CAPSULE"].asfactor()

    echo_and_interact(demo_commands, interactive, echo)
    from h2o.estimators.gbm import H2OGradientBoostingEstimator
    prostate_gbm = H2OGradientBoostingEstimator(distribution="bernoulli", ntrees=10, max_depth=8, min_rows=10, learn_rate=0.2)
    prostate_gbm.train(x=["AGE", "RACE", "PSA", "VOL", "GLEASON"], y="CAPSULE", training_frame=train)

    echo_and_interact(demo_commands, interactive, echo)
    prostate_gbm.show()

    echo_and_interact(demo_commands, interactive, echo, npop=3)
    predictions = prostate_gbm.predict(test)
    predictions.show()

    echo_and_interact(demo_commands, interactive, echo, npop=3)
    performance = prostate_gbm.model_performance(test)
    performance.show()

def deeplearning_demo(interactive, echo, test):
    h2o_data_path = system_file("prostate.csv")

    demo_description = ['\n-----------------------------------------------------------------',
                        'This is a demo of H2O\'s Deeplearning function.',
                        'It uploads a dataset to h2o, parses it, and shows a description.',
                        'Then, it divides the dataset into training and test sets, ',
                        'builds a model from the training set, and predicts on the test set.',
                        'Finally, default performance metrics are displayed.',
                        '-----------------------------------------------------------------']

    demo_commands = ['# Connect to h2o',
                     '>>> h2o.init()\n',
                     '\n# Upload the prostate dataset that comes included in the h2o python package',
                     '>>> prostate = h2o.upload_file(path = ' + h2o_data_path + '))\n',
                     '\n# Print a description of the prostate data',
                     '>>> prostate.summary()\n',
                     '\n# Randomly split the dataset into ~70/30, training/test sets',
                     '>>> r = prostate[0].runif()',
                     '>>> train = prostate[r < 0.70]',
                     '>>> valid = prostate[r >= 0.30]\n',
                     '\n# Convert the response columns to factors (for binary classification problems)',
                     '>>> train["CAPSULE"] = train["CAPSULE"].asfactor()',
                     '>>> test["CAPSULE"] = test["CAPSULE"].asfactor()\n',
                     '\n# Build a (classification) Deeplearning model',
                     'from h2o.estimators.deeplearning import H2ODeepLearningEstimator\n',
                     '>>> prostate_dl = H2ODeepLearningEstimator(activation="Tanh", hidden=[10, 10, 10], epochs=10000)\n',
                     '>>> prostate_dl.train(x=list(set(prostate.col_names)-set(["ID","CAPSULE"])),y="CAPSULE", training_frame=train)\n',
                     '\n# Show the model',
                     '>>> prostate_dl.show()\n',
                     '\n# Predict on the test set and show the first ten predictions',
                     '>>> predictions = prostate_dl.predict(test)',
                     '>>> predictions.show()\n',
                     '\n# Show default performance metrics',
                     '>>> performance = prostate_dl.model_performance(test)',
                     '>>> performance.show()\n']

    for line in demo_description: print(line)
    print()

    echo_and_interact(demo_commands, interactive, echo)
    if not test: h2o.init()

    echo_and_interact(demo_commands, interactive, echo)
    prostate = h2o.upload_file(path = h2o_data_path)

    echo_and_interact(demo_commands, interactive, echo)
    prostate.summary()

    echo_and_interact(demo_commands, interactive, echo, npop=4)
    r = prostate[0].runif()
    train = prostate[r < 0.70]
    test = prostate[r >= 0.30]

    echo_and_interact(demo_commands, interactive, echo, npop=3)
    train["CAPSULE"] = train["CAPSULE"].asfactor()
    test["CAPSULE"] = test["CAPSULE"].asfactor()

    echo_and_interact(demo_commands, interactive, echo)
    from h2o.estimators.deeplearning import H2ODeepLearningEstimator
    prostate_dl = H2ODeepLearningEstimator(activation="Tanh", hidden=[10, 10, 10], epochs=10000)
    prostate_dl.train(x=list(set(prostate.col_names)-set(["ID","CAPSULE"])),y="CAPSULE", training_frame=train)

    echo_and_interact(demo_commands, interactive, echo)
    prostate_dl.show()

    echo_and_interact(demo_commands, interactive, echo, npop=3)
    predictions = prostate_dl.predict(test)
    predictions.show()

    echo_and_interact(demo_commands, interactive, echo, npop=3)
    performance = prostate_dl.model_performance(test)
    performance.show()

def glm_demo(interactive, echo, test):
    h2o_data_path = system_file("prostate.csv")

    demo_description = ['\n-----------------------------------------------------------------',
                        'This is a demo of H2O\'s GLM function.',
                        'It uploads a dataset to h2o, parses it, and shows a description.',
                        'Then, it divides the dataset into training and test sets, ',
                        'builds a GLM from the training set, and predicts on the test set.',
                        'Finally, default performance metrics are displayed.',
                        '-----------------------------------------------------------------']

    demo_commands = ['# Connect to h2o',
                     '>>> h2o.init()\n',
                     '\n# Upload the prostate dataset that comes included in the h2o python package',
                     '>>> prostate = h2o.upload_file(path = ' + h2o_data_path + '))\n',
                     '\n# Print a description of the prostate data',
                     '>>> prostate.summary()\n',
                     '\n# Randomly split the dataset into ~70/30, training/test sets',
                     '>>> r = prostate[0].runif()',
                     '>>> train = prostate[r < 0.70]',
                     '>>> valid = prostate[r >= 0.30]\n',
                     '\n# Convert the response columns to factors (for binary classification problems)',
                     '>>> train["CAPSULE"] = train["CAPSULE"].asfactor()',
                     '>>> test["CAPSULE"] = test["CAPSULE"].asfactor()\n',
                     '\n# Build a (classification) GLM\n',
                     'from h2o.estimators.glm import H2OGeneralizedLinearEstimator\n',
                     '>>> prostate_glm = H2OGeneralizedLinearEstimator(family="binomial", alpha=[0.5])\n',
                     '>>> prostate_glm.train(x=["AGE", "RACE", "PSA", "VOL", "GLEASON"], y="CAPSULE", training_frame=train)\n',
                     '\n# Show the model',
                     '>>> prostate_glm.show()\n',
                     '\n# Predict on the test set and show the first ten predictions',
                     '>>> predictions = prostate_glm.predict(test)',
                     '>>> predictions.show()\n',
                     '\n# Show default performance metrics',
                     '>>> performance = prostate_glm.model_performance(test)',
                     '>>> performance.show()\n']

    for line in demo_description: print(line)
    print()

    echo_and_interact(demo_commands, interactive, echo)
    if not test: h2o.init()

    echo_and_interact(demo_commands, interactive, echo)
    prostate = h2o.upload_file(path=h2o_data_path)

    echo_and_interact(demo_commands, interactive, echo)
    prostate.summary()

    echo_and_interact(demo_commands, interactive, echo, npop=4)
    r = prostate[0].runif()
    train = prostate[r < 0.70]
    test = prostate[r >= 0.30]

    echo_and_interact(demo_commands, interactive, echo, npop=3)
    train["CAPSULE"] = train["CAPSULE"].asfactor()
    test["CAPSULE"] = test["CAPSULE"].asfactor()

    echo_and_interact(demo_commands, interactive, echo)
    from h2o.estimators.glm import H2OGeneralizedLinearEstimator
    prostate_glm = H2OGeneralizedLinearEstimator(family="binomial", alpha=[0.5])
    prostate_glm.train(x=["AGE", "RACE", "PSA", "VOL", "GLEASON"], y="CAPSULE", training_frame=train)

    echo_and_interact(demo_commands, interactive, echo)
    prostate_glm.show()

    echo_and_interact(demo_commands, interactive, echo, npop=3)
    predictions = prostate_glm.predict(test)
    predictions.show()

    echo_and_interact(demo_commands, interactive, echo, npop=3)
    performance = prostate_glm.model_performance(test)
    performance.show()

def echo_and_interact(demo_commands, interactive, echo, npop=2):
    if demo_commands:
        if echo:
            for p in range(npop): print(demo_commands.pop(0))
        if interactive:
            raw_input('Press ENTER...\n')
