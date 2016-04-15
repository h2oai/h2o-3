from __future__ import print_function
from future import standard_library
standard_library.install_aliases()
from builtins import range
from past.builtins import basestring
import sys, os
sys.path.insert(1, "../../")
import h2o
import imp
import random
import re
import subprocess
from subprocess import STDOUT,PIPE
from h2o.utils.shared_utils import temp_ctr
from h2o.model.binomial import H2OBinomialModel
from h2o.model.clustering import H2OClusteringModel
from h2o.model.multinomial import H2OMultinomialModel
from h2o.model.regression import H2ORegressionModel
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.estimators.kmeans import H2OKMeansEstimator
from h2o.transforms.decomposition import H2OPCA
from h2o.estimators.naive_bayes import H2ONaiveBayesEstimator
from decimal import *
import urllib.request, urllib.error, urllib.parse
import numpy as np
import shutil
import string
import copy
import json


def check_models(model1, model2, use_cross_validation=False, op='e'):
    """
    Check that the given models are equivalent
    :param model1:
    :param model2:
    :param use_cross_validation: boolean. if True, use validation metrics to determine model equality. Otherwise, use
    training metrics.
    :param op: comparison operator to use. 'e':==, 'g':>, 'ge':>=
    :return: None. Throw meaningful error messages if the check fails
    """
    # 1. Check model types
    model1_type = model1.__class__.__name__
    model2_type = model1.__class__.__name__
    assert model1_type is model2_type, "The model types differ. The first model is of type {0} and the second " \
                                       "models is of type {1}.".format(model1_type, model2_type)

    # 2. Check model metrics
    if isinstance(model1,H2OBinomialModel): #   2a. Binomial
        # F1
        f1_1 = model1.F1(xval=use_cross_validation)
        f1_2 = model2.F1(xval=use_cross_validation)
        if op == 'e': assert f1_1[0][1] == f1_2[0][1], "The first model has an F1 of {0} and the second model has an F1 of " \
                                                       "{1}. Expected the first to be == to the second.".format(f1_1[0][1], f1_2[0][1])
        elif op == 'g': assert f1_1[0][1] > f1_2[0][1], "The first model has an F1 of {0} and the second model has an F1 of " \
                                                        "{1}. Expected the first to be > than the second.".format(f1_1[0][1], f1_2[0][1])
        elif op == 'ge': assert f1_1[0][1] >= f1_2[0][1], "The first model has an F1 of {0} and the second model has an F1 of " \
                                                          "{1}. Expected the first to be >= than the second.".format(f1_1[0][1], f1_2[0][1])
    elif isinstance(model1,H2ORegressionModel): #   2b. Regression
        # MSE
        mse1 = model1.mse(xval=use_cross_validation)
        mse2 = model2.mse(xval=use_cross_validation)
        if op == 'e': assert mse1 == mse2, "The first model has an MSE of {0} and the second model has an MSE of " \
                                           "{1}. Expected the first to be == to the second.".format(mse1, mse2)
        elif op == 'g': assert mse1 > mse2, "The first model has an MSE of {0} and the second model has an MSE of " \
                                            "{1}. Expected the first to be > than the second.".format(mse1, mse2)
        elif op == 'ge': assert mse1 >= mse2, "The first model has an MSE of {0} and the second model has an MSE of " \
                                              "{1}. Expected the first to be >= than the second.".format(mse1, mse2)
    elif isinstance(model1,H2OMultinomialModel): #   2c. Multinomial
        # hit-ratio
        pass
    elif isinstance(model1,H2OClusteringModel): #   2d. Clustering
        # totss
        totss1 = model1.totss(xval=use_cross_validation)
        totss2 = model2.totss(xval=use_cross_validation)
        if op == 'e': assert totss1 == totss2, "The first model has an TOTSS of {0} and the second model has an " \
                                               "TOTSS of {1}. Expected the first to be == to the second.".format(totss1,
                                                                                                                 totss2)
        elif op == 'g': assert totss1 > totss2, "The first model has an TOTSS of {0} and the second model has an " \
                                                "TOTSS of {1}. Expected the first to be > than the second.".format(totss1,
                                                                                                                   totss2)
        elif op == 'ge': assert totss1 >= totss2, "The first model has an TOTSS of {0} and the second model has an " \
                                                  "TOTSS of {1}. Expected the first to be >= than the second." \
                                                  "".format(totss1, totss2)

def check_dims_values(python_obj, h2o_frame, rows, cols, dim_only=False):
    """
    Check that the dimensions and values of the python object and H2OFrame are equivalent. Assumes that the python object
    conforms to the rules specified in the h2o frame documentation.

    :param python_obj: a (nested) list, tuple, dictionary, numpy.ndarray, ,or pandas.DataFrame
    :param h2o_frame: an H2OFrame
    :param rows: number of rows
    :param cols: number of columns
    :param dim_only: check the dimensions only
    :return: None
    """
    h2o_rows, h2o_cols = h2o_frame.dim
    assert h2o_rows == rows and h2o_cols == cols, "failed dim check! h2o_rows:{0} rows:{1} h2o_cols:{2} cols:{3}" \
                                                  "".format(h2o_rows, rows, h2o_cols, cols)
    if not dim_only:
        if isinstance(python_obj, (list, tuple)):
            for c in range(cols):
                for r in range(rows):
                    pval = python_obj[r][c] if rows > 1 else python_obj[c]
                    hval = h2o_frame[r,c]
                    assert pval == hval, "expected H2OFrame to have the same values as the python object for row {0} " \
                                         "and column {1}, but h2o got {2} and python got {3}.".format(r, c, hval, pval)
        elif isinstance(python_obj, dict):
            for r in range(rows):
                for k in list(python_obj.keys()):
                    pval = python_obj[k][r] if hasattr(python_obj[k],'__iter__') else python_obj[k]
                    hval = h2o_frame[r,k]
                    assert pval == hval, "expected H2OFrame to have the same values as the python object for row {0} " \
                                         "and column {1}, but h2o got {2} and python got {3}.".format(r, k, hval, pval)

def np_comparison_check(h2o_data, np_data, num_elements):
    """
    Check values achieved by h2o against values achieved by numpy

    :param h2o_data: an H2OFrame or H2OVec
    :param np_data: a numpy array
    :param num_elements: number of elements to compare
    :return: None
    """
    # Check for numpy
    try:
        imp.find_module('numpy')
    except ImportError:
        assert False, "failed comparison check because unable to import numpy"

    import numpy as np
    rows, cols = h2o_data.dim
    for i in range(num_elements):
        r = random.randint(0,rows-1)
        c = random.randint(0,cols-1)
        h2o_val = h2o_data[r,c]
        np_val = np_data[r,c] if len(np_data.shape) > 1 else np_data[r]
        if isinstance(np_val, np.bool_): np_val = bool(np_val)  # numpy haz special bool type :(
        assert np.absolute(h2o_val - np_val) < 1e-5, \
            "failed comparison check! h2o computed {0} and numpy computed {1}".format(h2o_val, np_val)

def javapredict(algo, equality, train, test, x, y, compile_only=False, **kwargs):
    print("Creating model in H2O")
    if algo == "gbm": model = H2OGradientBoostingEstimator(**kwargs)
    elif algo == "random_forest": model = H2ORandomForestEstimator(**kwargs)
    elif algo == "deeplearning": model = H2ODeepLearningEstimator(**kwargs)
    elif algo == "glm": model = H2OGeneralizedLinearEstimator(**kwargs)
    elif algo == "naive_bayes": model = H2ONaiveBayesEstimator(**kwargs)
    elif algo == "kmeans": model = H2OKMeansEstimator(**kwargs)
    elif algo == "pca": model = H2OPCA(**kwargs)
    else: raise ValueError
    if algo == "kmeans" or algo == "pca": model.train(x=x, training_frame=train)
    else: model.train(x=x, y=y, training_frame=train)
    print(model)

    # HACK: munge model._id so that it conforms to Java class name. For example, change K-means to K_means.
    # TODO: clients should extract Java class name from header.
    regex = re.compile("[+\\-* !@#$%^&()={}\\[\\]|;:'\"<>,.?/]")
    pojoname = regex.sub("_",model._id)

    print("Downloading Java prediction model code from H2O")
    tmpdir = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath(__file__)),"..","results",pojoname))
    os.mkdir(tmpdir)
    h2o.download_pojo(model,path=tmpdir)
    h2o_genmodel_jar = os.path.join(tmpdir,"h2o-genmodel.jar")
    assert os.path.exists(h2o_genmodel_jar), "Expected file {0} to exist, but it does not.".format(h2o_genmodel_jar)
    print("h2o-genmodel.jar saved in {0}".format(h2o_genmodel_jar))
    java_file = os.path.join(tmpdir,pojoname+".java")
    assert os.path.exists(java_file), "Expected file {0} to exist, but it does not.".format(java_file)
    print("java code saved in {0}".format(java_file))

    print("Compiling Java Pojo")
    javac_cmd = ["javac", "-cp", h2o_genmodel_jar, "-J-Xmx12g", "-J-XX:MaxPermSize=256m", java_file]
    subprocess.check_call(javac_cmd)

    if not compile_only:
        print("Predicting in H2O")
        predictions = model.predict(test)
        predictions.summary()
        predictions.head()
        out_h2o_csv = os.path.join(tmpdir,"out_h2o.csv")
        h2o.download_csv(predictions, out_h2o_csv)
        assert os.path.exists(out_h2o_csv), "Expected file {0} to exist, but it does not.".format(out_h2o_csv)
        print("H2O Predictions saved in {0}".format(out_h2o_csv))

        print("Setting up for Java POJO")
        in_csv = os.path.join(tmpdir,"in.csv")
        h2o.download_csv(test[x], in_csv)

        # hack: the PredictCsv driver can't handle quoted strings, so remove them
        f = open(in_csv, 'r+')
        csv = f.read()
        csv = re.sub('\"', '', csv)
        f.seek(0)
        f.write(csv)
        f.truncate()
        f.close()
        assert os.path.exists(in_csv), "Expected file {0} to exist, but it does not.".format(in_csv)
        print("Input CSV to PredictCsv saved in {0}".format(in_csv))

        print("Running PredictCsv Java Program")
        out_pojo_csv = os.path.join(tmpdir,"out_pojo.csv")
        cp_sep = ";" if sys.platform == "win32" else ":"
        java_cmd = ["java", "-ea", "-cp", h2o_genmodel_jar + cp_sep + tmpdir, "-Xmx12g", "-XX:MaxPermSize=2g",
                    "-XX:ReservedCodeCacheSize=256m", "hex.genmodel.tools.PredictCsv", "--header", "--model", pojoname,
                    "--input", in_csv, "--output", out_pojo_csv]
        p = subprocess.Popen(java_cmd, stdout=PIPE, stderr=STDOUT)
        o, e = p.communicate()
        print("Java output: {0}".format(o))
        assert os.path.exists(out_pojo_csv), "Expected file {0} to exist, but it does not.".format(out_pojo_csv)
        predictions2 = h2o.upload_file(path=out_pojo_csv)
        print("Pojo predictions saved in {0}".format(out_pojo_csv))

        print("Comparing predictions between H2O and Java POJO")
        # Dimensions
        hr, hc = predictions.dim
        pr, pc = predictions2.dim
        assert hr == pr, "Expected the same number of rows, but got {0} and {1}".format(hr, pr)
        assert hc == pc, "Expected the same number of cols, but got {0} and {1}".format(hc, pc)

        # Value
        for r in range(hr):
            hp = predictions[r,0]
            if equality == "numeric":
                pp = float.fromhex(predictions2[r,0])
                assert abs(hp - pp) < 1e-4, "Expected predictions to be the same (within 1e-4) for row {0}, but got {1} and {2}".format(r,hp, pp)
            elif equality == "class":
                pp = predictions2[r,0]
                assert hp == pp, "Expected predictions to be the same for row {0}, but got {1} and {2}".format(r,hp, pp)
            else:
                raise ValueError


def javamunge(assembly, pojoname, test, compile_only=False):
    """
    Here's how to use:
      assembly is an already fit H2OAssembly;
      The test set should be used to compare the output here and the output of the POJO.
    """
    print("Downloading munging POJO code from H2O")
    tmpdir = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath(__file__)),"..","results", pojoname))
    os.mkdir(tmpdir)
    assembly.to_pojo(pojoname, path=tmpdir, get_jar=True)
    h2o_genmodel_jar = os.path.join(tmpdir,"h2o-genmodel.jar")
    assert os.path.exists(h2o_genmodel_jar), "Expected file {0} to exist, but it does not.".format(h2o_genmodel_jar)
    print("h2o-genmodel.jar saved in {0}".format(h2o_genmodel_jar))
    java_file = os.path.join(tmpdir,pojoname+".java")
    assert os.path.exists(java_file), "Expected file {0} to exist, but it does not.".format(java_file)
    print("java code saved in {0}".format(java_file))

    print("Compiling Java Pojo")
    javac_cmd = ["javac", "-cp", h2o_genmodel_jar, "-J-Xmx12g", "-J-XX:MaxPermSize=256m", java_file]
    subprocess.check_call(javac_cmd)

    if not compile_only:

        print("Setting up for Java POJO")
        in_csv = os.path.join(tmpdir,"in.csv")
        h2o.download_csv(test, in_csv)
        assert os.path.exists(in_csv), "Expected file {0} to exist, but it does not.".format(in_csv)
        print("Input CSV to mungedCSV saved in {0}".format(in_csv))

        print("Predicting in H2O")
        munged = assembly.fit(test)
        munged.head()
        out_h2o_csv = os.path.join(tmpdir,"out_h2o.csv")
        h2o.download_csv(munged, out_h2o_csv)
        assert os.path.exists(out_h2o_csv), "Expected file {0} to exist, but it does not.".format(out_h2o_csv)
        print("Munged frame saved in {0}".format(out_h2o_csv))

        print("Running PredictCsv Java Program")
        out_pojo_csv = os.path.join(tmpdir,"out_pojo.csv")
        cp_sep = ";" if sys.platform == "win32" else ":"
        java_cmd = ["java", "-ea", "-cp", h2o_genmodel_jar + cp_sep + tmpdir, "-Xmx12g", "-XX:MaxPermSize=2g",
                    "-XX:ReservedCodeCacheSize=256m", "hex.genmodel.tools.MungeCsv", "--header", "--munger", pojoname,
                    "--input", in_csv, "--output", out_pojo_csv]
        print("JAVA COMMAND: " + " ".join(java_cmd))
        p = subprocess.Popen(java_cmd, stdout=PIPE, stderr=STDOUT)
        o, e = p.communicate()
        print("Java output: {0}".format(o))
        assert os.path.exists(out_pojo_csv), "Expected file {0} to exist, but it does not.".format(out_pojo_csv)
        munged2 = h2o.upload_file(path=out_pojo_csv)
        print("Pojo predictions saved in {0}".format(out_pojo_csv))

        print("Comparing predictions between H2O and Java POJO")
        # Dimensions
        hr, hc = munged.dim
        pr, pc = munged2.dim
        assert hr == pr, "Expected the same number of rows, but got {0} and {1}".format(hr, pr)
        assert hc == pc, "Expected the same number of cols, but got {0} and {1}".format(hc, pc)

        # Value
        import math
        munged.show()
        munged2.show()
        for r in range(hr):
          for c in range(hc):
              hp = munged[r,c]
              pp = munged2[r,c]
              if isinstance(hp, float):
                assert isinstance(pp, float)
                assert (math.fabs(hp-pp) < 1e-8) or (math.isnan(hp) and math.isnan(pp)), "Expected munged rows to be the same for row {0}, but got {1}, and {2}".format(r, hp, pp)
              else:
                assert hp == pp, "Expected munged rows to be the same for row {0}, but got {1}, and {2}".format(r, hp, pp)

def locate(path):
    """
    Search for a relative path and turn it into an absolute path.
    This is handy when hunting for data files to be passed into h2o and used by import file.
    Note: This function is for unit testing purposes only.

    Parameters
    ----------
    path : str
      Path to search for

    :return: Absolute path if it is found.  None otherwise.
    """
    if (test_is_on_hadoop()):
       # Jenkins jobs create symbolic links to smalldata and bigdata on the machine that starts the test. However,
       # in an h2o multinode hadoop cluster scenario, the clustered machines don't know about the symbolic link.
       # Consequently, `locate` needs to return the actual path to the data on the clustered machines. ALL jenkins
       # machines store smalldata and bigdata in /home/0xdiag/. If ON.HADOOP is set by the run.py, the path arg MUST
       # be an immediate subdirectory of /home/0xdiag/. Moreover, the only guaranteed subdirectories of /home/0xdiag/ are
       # smalldata and bigdata.
       p = os.path.realpath(os.path.join("/home/0xdiag/",path))
       if not os.path.exists(p): raise ValueError("File not found: " + path)
       return p
    else:
        tmp_dir = os.path.realpath(os.getcwd())
        possible_result = os.path.join(tmp_dir, path)
        while (True):
            if (os.path.exists(possible_result)):
                return possible_result

            next_tmp_dir = os.path.dirname(tmp_dir)
            if (next_tmp_dir == tmp_dir):
                raise ValueError("File not found: " + path)

            tmp_dir = next_tmp_dir
            possible_result = os.path.join(tmp_dir, path)

def hadoop_namenode_is_accessible():
    url = "http://{0}:50070".format(hadoop_namenode())
    try:
        urllib.urlopen(url)
        internal = True
    except:
        internal = False
    return internal

def test_is_on_hadoop():
    if hasattr(sys.modules["tests.pyunit_utils"], '__on_hadoop__'):
        return sys.modules["tests.pyunit_utils"].__on_hadoop__
    return False

def hadoop_namenode():
    if os.getenv("NAME_NODE"):
        return os.getenv("NAME_NODE").split(".")[0]
    elif hasattr(sys.modules["tests.pyunit_utils"], '__hadoop_namenode__'):
        return sys.modules["tests.pyunit_utils"].__hadoop_namenode__
    return None

def pyunit_exec(test_name):
    with open (test_name, "r") as t: pyunit = t.read()
    pyunit_c = compile(pyunit, '<string>', 'exec')
    p = {}
    exec(pyunit_c, p)

def standalone_test(test):
    h2o.init(strict_version_check=False)

    h2o.remove_all()

    h2o.log_and_echo("------------------------------------------------------------")
    h2o.log_and_echo("")
    h2o.log_and_echo("STARTING TEST")
    h2o.log_and_echo("")
    h2o.log_and_echo("------------------------------------------------------------")
    test()

def make_random_grid_space(algo, ncols=None, nrows=None):
    """
    Construct a dictionary of the form {gbm_parameter:list_of_values, ...}, which will eventually be passed to
    H2OGridSearch to build a grid object. The gbm parameters, and their associated values, are randomly selected.
    :param algo: a string {"gbm", "rf", "dl", "km", "glm"} representing the algo dimension of the grid space
    :param ncols: Used for mtries selection or k (pca)
    :param nrows: Used for k (pca)
    :return: a dictionary of parameter_name:list_of_values
    """
    grid_space = {}
    if algo in ["gbm", "rf"]:
        if random.randint(0,1): grid_space['ntrees'] = random.sample(list(range(1,6)),random.randint(2,3))
        if random.randint(0,1): grid_space['max_depth'] = random.sample(list(range(1,6)),random.randint(2,3))
        if random.randint(0,1): grid_space['min_rows'] = random.sample(list(range(1,11)),random.randint(2,3))
        if random.randint(0,1): grid_space['nbins'] = random.sample(list(range(2,21)),random.randint(2,3))
        if random.randint(0,1): grid_space['nbins_cats'] = random.sample(list(range(2,1025)),random.randint(2,3))

        if algo == "gbm":
            if random.randint(0,1): grid_space['learn_rate'] = [random.random() for r in range(random.randint(2,3))]
            grid_space['distribution'] = random.sample(['bernoulli','multinomial','gaussian','poisson','tweedie','gamma'], 1)
        if algo == "rf":
            if random.randint(0,1): grid_space['mtries'] = random.sample(list(range(1,ncols+1)),random.randint(2,3))
            if random.randint(0,1): grid_space['sample_rate'] = [random.random() for r in range(random.randint(2,3))]
    elif algo == "km":
        grid_space['k'] = random.sample(list(range(1,10)),random.randint(2,3))
        if random.randint(0,1): grid_space['max_iterations'] = random.sample(list(range(1,1000)),random.randint(2,3))
        if random.randint(0,1): grid_space['standardize'] = [True, False]
        if random.randint(0,1): grid_space['seed'] = random.sample(list(range(1,1000)),random.randint(2,3))
        if random.randint(0,1): grid_space['init'] = random.sample(['Random','PlusPlus','Furthest'],random.randint(2,3))
    elif algo == "glm":
        if random.randint(0,1): grid_space['alpha'] = [random.random() for r in range(random.randint(2,3))]
        grid_space['family'] = random.sample(['binomial','gaussian','poisson','tweedie','gamma'], 1)
        if grid_space['family'] == "tweedie":
            if random.randint(0,1):
                grid_space['tweedie_variance_power'] = [round(random.random()+1,6) for r in range(random.randint(2,3))]
                grid_space['tweedie_link_power'] = 1 - grid_space['tweedie_variance_power']
    elif algo == "dl":
        if random.randint(0,1): grid_space['activation'] = \
            random.sample(["Rectifier", "Tanh", "TanhWithDropout", "RectifierWithDropout", "MaxoutWithDropout"],
                          random.randint(2,3))
        if random.randint(0,1): grid_space['l2'] = [0.001*random.random() for r in range(random.randint(2,3))]
        grid_space['distribution'] = random.sample(['bernoulli','multinomial','gaussian','poisson','tweedie','gamma'],1)
        return grid_space
    elif algo == "naiveBayes":
        grid_space['laplace'] = 0
        if random.randint(0,1): grid_space['laplace'] = [round(random.random() + r, 6) for r in random.sample(list(range(0,11)), random.randint(2,3))]
        if random.randint(0,1): grid_space['min_sdev'] = [round(random.random(),6) for r in range(random.randint(2,3))]
        if random.randint(0,1): grid_space['eps_sdev'] = [round(random.random(),6) for r in range(random.randint(2,3))]
    elif algo == "pca":
        if random.randint(0,1): grid_space['max_iterations'] = random.sample(list(range(1,1000)),random.randint(2,3))
        if random.randint(0,1): grid_space['transform'] = random.sample(["NONE","STANDARDIZE","NORMALIZE","DEMEAN","DESCALE"], random.randint(2,3))
        grid_space['k'] = random.sample(list(range(1,min(ncols,nrows))),random.randint(2,3))
    else:
        raise ValueError
    return grid_space

# Validate given models' parameters against expected values
def expect_model_param(models, attribute_name, expected_values):
    print("param: {0}".format(attribute_name))
    actual_values = list(set([m.params[attribute_name]['actual'] \
                                  if type(m.params[attribute_name]['actual']) != list
                                  else m.params[attribute_name]['actual'][0] for m in models.models]))
                                  # possible for actual to be a list (GLM)
    if type(expected_values) != list:
        expected_values = [expected_values]
    # limit precision. Rounding happens in some models like RF
    actual_values = [x if isinstance(x,basestring) else round(float(x),5) for x in actual_values]
    expected_values = [x if isinstance(x,basestring) else round(float(x),5) for x in expected_values]
    print("actual values: {0}".format(actual_values))
    print("expected values: {0}".format(expected_values))
    actual_values_len = len(actual_values)
    expected_values_len = len(expected_values)
    assert actual_values_len == expected_values_len, "Expected values len: {0}. Actual values len: " \
                                                     "{1}".format(expected_values_len, actual_values_len)
    actual_values = sorted(actual_values)
    expected_values = sorted(expected_values)
    for i in range(len(actual_values)):
        if isinstance(actual_values[i], float):
            assert abs(actual_values[i]-expected_values[i]) < 1.1e-5, "Too large of a difference betewen actual and " \
                                                                "expected value. Actual value: {}. Expected value: {}"\
                                                                .format(actual_values[i], expected_values[i])
        else:
            assert actual_values[i] == expected_values[i], "Expected: {}. Actual: {}"\
                                                            .format(expected_values[i], actual_values[i])


def rest_ctr():  return h2o.H2OConnection.rest_ctr()


def write_syn_floating_point_dataset_glm(csv_training_data_filename, csv_validation_data_filename,
                                         csv_test_data_filename, csv_weight_name, row_count, col_count, data_type,
                                         max_p_value, min_p_value, max_w_value, min_w_value, noise_std, family_type,
                                         valid_row_count, test_row_count, class_number=2,
                                         class_method=['probability', 'probability', 'probability'],
                                         class_margin=[0.0, 0.0, 0.0]):
    """
    Generate random data sets to test the GLM algo using the following steps:
    1. randomly generate the intercept and weight vector;
    2. generate a set of predictors X;
    3. generate the corresponding response y using the formula: y = w^T x+b+e where T is transpose, e is a random
     Gaussian noise added.  For the Binomial family, the relationship between the response Y and predictor vector X
     is assumed to be Prob(Y = 1|X) = exp(W^T * X + e)/(1+exp(W^T * X + e)).  For the Multinomial family, the
     relationship between the response Y (K possible classes) and predictor vector X is assumed to be
     Prob(Y = c|X) = exp(Wc^T * X + e)/(sum k=0 to K-1 (ep(Wk^T *X+e))

    :param csv_training_data_filename: string representing full path filename to store training data set.  Set to
    null string if no training data set is to be generated.
    :param csv_validation_data_filename: string representing full path filename to store validation data set.  Set to
    null string if no validation data set is to be generated.
    :param csv_test_data_filename: string representing full path filename to store test data set.  Set to null string if
        no test data set is to be generated.
    :param csv_weight_name: string representing full path filename to store intercept and weight used to generate
    all data sets.
    :param row_count: integer representing number of samples (predictor, response) in training data set
    :param col_count: integer representing the number of predictors in the data set
    :param data_type: integer representing the type of predictors or weights (1: integers, 2: real)
    :param max_p_value: integer representing maximum predictor values
    :param min_p_value: integer representing minimum predictor values
    :param max_w_value: integer representing maximum intercept/weight values
    :param min_w_value: integer representing minimum intercept/weight values
    :param noise_std: Gaussian noise standard deviation used to generate noise e to add to response
    :param family_type: string represents the various distribution families (gaussian, multinomial, binomial) supported
    by our GLM algo
    :param valid_row_count: integer representing number of samples (predictor, response) in validation data set
    :param test_row_count: integer representing number of samples (predictor, response) in test data set
    :param class_number: integer, optional, representing number of classes for binomial and multinomial
    :param class_method: string tuple, optional, describing how we derive the final response from the class
    probabilities generated for binomial and multinomial family_type for training/validation/test data set respectively.
    If set to 'probability', response y is generated randomly according to the class probabilities calculated.  If set
    to 'threshold', response y is set to the class with the maximum class probability if the maximum class probability
    exceeds the second highest class probability by the value set in margin.  If the maximum class probability fails
    to be greater by the margin than the second highest class probability, the data sample is discarded.
    :param class_margin: float tuple, optional, denotes the threshold by how much the maximum class probability has to
     exceed the second highest class probability in order for us to keep the data sample for
     training/validation/test data set respectively.  This field is only meaningful if class_method is set to
    'threshold'

    :return: None
    """

    # generate bias b and weight as a column vector
    weights = generate_weights_glm(csv_weight_name, col_count, data_type, min_w_value, max_w_value,
                                   family_type=family_type, class_number=class_number)

    # generate training data set
    if len(csv_training_data_filename) > 0:
        generate_training_set_glm(csv_training_data_filename, row_count, col_count, min_p_value, max_p_value, data_type,
                                  family_type, noise_std, weights,
                                  class_method=class_method[0], class_margin=class_margin[0])

    # generate validation data set
    if len(csv_validation_data_filename) > 0:
        generate_training_set_glm(csv_validation_data_filename, valid_row_count, col_count, min_p_value, max_p_value,
                                  data_type, family_type, noise_std, weights,
                                  class_method=class_method[1], class_margin=class_margin[1])
    # generate test data set
    if len(csv_test_data_filename) > 0:
        generate_training_set_glm(csv_test_data_filename, test_row_count, col_count, min_p_value, max_p_value,
                                  data_type, family_type, noise_std, weights,
                                  class_method=class_method[2], class_margin=class_margin[2])


def write_syn_mixed_dataset_glm(csv_training_data_filename, csv_training_data_filename_true_one_hot,
                                csv_validation_data_filename, csv_validation_filename_true_one_hot,
                                csv_test_data_filename, csv_test_filename_true_one_hot, csv_weight_filename, row_count,
                                col_count, max_p_value, min_p_value, max_w_value, min_w_value, noise_std, family_type,
                                valid_row_count, test_row_count, enum_col, enum_level_vec, class_number=2,
                                class_method=['probability', 'probability', 'probability'],
                                class_margin=[0.0, 0.0, 0.0]):
    """
    This function differs from write_syn_floating_point_dataset_glm in one small point.  The predictors in this case
    contains categorical data as well as real data.

    Generate random data sets to test the GLM algo using the following steps:
    1. randomly generate the intercept and weight vector;
    2. generate a set of predictors X;
    3. generate the corresponding response y using the formula: y = w^T x+b+e where T is transpose, e is a random
     Gaussian noise added.  For the Binomial family, the relationship between the response Y and predictor vector X
     is assumed to be Prob(Y = 1|X) = exp(W^T * X + e)/(1+exp(W^T * X + e)).  For the Multinomial family, the
     relationship between the response Y (K possible classes) and predictor vector X is assumed to be
     Prob(Y = c|X) = exp(Wc^T * X + e)/(sum k=0 to K-1 (ep(Wk^T *X+e))


    :param csv_training_data_filename: string representing full path filename to store training data set.  Set to null
     string if no training data set is to be generated.
    :param csv_training_data_filename_true_one_hot: string representing full path filename to store training data set
    with true one-hot encoding.  Set to null string if no training data set is to be generated.
    :param csv_validation_data_filename: string representing full path filename to store validation data set.  Set to
     null string if no validation data set is to be generated.
    :param csv_validation_filename_true_one_hot: string representing full path filename to store validation data set
     with true one-hot.  Set to null string if no validation data set is to be generated.
    :param csv_test_data_filename: string representing full path filename to store test data set.  Set to null
    string if no test data set is to be generated.
    :param csv_test_filename_true_one_hot: string representing full path filename to store test data set with true
     one-hot encoding.  Set to null string if no test data set is to be generated.
    :param csv_weight_filename: string representing full path filename to store intercept and weight used to generate
     all data sets.
    :param row_count: integer representing number of samples (predictor, response) in training data set
    :param col_count: integer representing the number of predictors in the data set
    :param max_p_value: integer representing maximum predictor values
    :param min_p_value: integer representing minimum predictor values
    :param max_w_value: integer representing maximum intercept/weight values
    :param min_w_value: integer representing minimum intercept/weight values
    :param noise_std: Gaussian noise standard deviation used to generate noise e to add to response
    :param family_type: string represents the various distribution families (gaussian, multinomial, binomial) supported
     by our GLM algo
    :param valid_row_count: integer representing number of samples (predictor, response) in validation data set
    :param test_row_count: integer representing number of samples (predictor, response) in test data set
    :param enum_col: integer representing actual number of categorical columns in data set
    :param enum_level_vec: vector containing maximum integer value for each categorical column
    :param class_number: integer, optional, representing number classes for binomial and multinomial
    :param class_method: string tuple, optional, describing how we derive the final response from the class
    probabilities generated for binomial and multinomial family_type for training/validation/test data set respectively.
    If set to 'probability', response y is generated randomly according to the class probabilities calculated.  If set
    to 'threshold', response y is set to the class with the maximum class probability if the maximum class probability
    exceeds the second highest class probability by the value set in margin.  If the maximum class probability fails
    to be greater by margin than the second highest class probability, the data sample is discarded.
    :param class_margin: float tuple, optional, denotes the threshold by how much the maximum class probability has to
     exceed the second highest class probability by in order for us to keep the data sample for
     training/validation/test data set respectively.  This field is only meaningful if class_method is set to
    'threshold'

    :return: None
    """
    # add column count of encoded categorical predictors, if maximum value for enum is 3, it has 4 levels.
    # hence 4 bits are used to encode it with true one hot encoding.  That is why we are adding 1 bit per
    # categorical columns added to our predictors
    new_col_count = col_count - enum_col + sum(enum_level_vec) + enum_level_vec.shape[0]

    # generate the weights to be applied to the training/validation/test data sets
    # this is for true one hot encoding.  For reference+one hot encoding, will skip
    # few extra weights
    weights = generate_weights_glm(csv_weight_filename, new_col_count, 2, min_w_value, max_w_value,
                                   family_type=family_type, class_number=class_number)

    # generate training data set
    if len(csv_training_data_filename) > 0:
        generate_training_set_mixed_glm(csv_training_data_filename, csv_training_data_filename_true_one_hot, row_count,
                                        col_count, min_p_value, max_p_value, family_type, noise_std, weights, enum_col,
                                        enum_level_vec, class_number=class_number,
                                        class_method=class_method[0], class_margin=class_margin[0])

    # generate validation data set
    if len(csv_validation_data_filename) > 0:
        generate_training_set_mixed_glm(csv_validation_data_filename, csv_validation_filename_true_one_hot,
                                        valid_row_count, col_count, min_p_value, max_p_value, family_type, noise_std,
                                        weights, enum_col, enum_level_vec, class_number=class_number,
                                        class_method=class_method[1], class_margin=class_margin[1])
    # generate test data set
    if len(csv_test_data_filename) > 0:
        generate_training_set_mixed_glm(csv_test_data_filename, csv_test_filename_true_one_hot, test_row_count,
                                        col_count, min_p_value, max_p_value, family_type, noise_std, weights, enum_col,
                                        enum_level_vec, class_number=class_number,
                                        class_method=class_method[2], class_margin=class_margin[2])


def generate_weights_glm(csv_weight_filename, col_count, data_type, min_w_value, max_w_value, family_type='gaussian',
                         class_number=2):
    """
    Generate random intercept and weight vectors (integer or real) for GLM algo and save
    the values in a file specified by csv_weight_filename.

    :param csv_weight_filename: string representing full path filename to store intercept and weight used to generate
    all data set
    :param col_count: integer representing the number of predictors in the data set
    :param data_type: integer representing the type of predictors or weights (1: integers, 2: real)
    :param max_w_value: integer representing maximum intercept/weight values
    :param min_w_value: integer representing minimum intercept/weight values
    :param family_type: string ,optional, represents the various distribution families (gaussian, multinomial, binomial)
        supported by our GLM algo
    :param class_number: integer, optional, representing number classes for binomial and multinomial

    :return: column vector of size 1+colCount representing intercept and weight or matrix of size
        1+colCount by class_number
    """

    # first generate random intercept and weight
    if 'gaussian' in family_type.lower():
        if data_type == 1:     # generate random integer intercept/weight
            weight = np.random.random_integers(min_w_value, max_w_value, [col_count+1, 1])
        elif data_type == 2:   # generate real intercept/weights
            weight = np.random.uniform(min_w_value, max_w_value, [col_count+1, 1])
        else:
            print("dataType must be 1 or 2 for now.")
            sys.exit(1)
    elif ('binomial' in family_type.lower()) or ('multinomial' in family_type.lower()):
        if 'binomial' in family_type.lower():  # for binomial, only need 1 set of weight
            class_number -= 1

        if class_number <= 0:
            print("class_number must be >= 2!")
            sys.exit(1)

        if data_type == 1:     # generate random integer intercept/weight
            weight = np.random.random_integers(min_w_value, max_w_value, [col_count+1, class_number])
        elif data_type == 2:   # generate real intercept/weights
            weight = np.random.uniform(min_w_value, max_w_value, [col_count+1, class_number])
        else:
            print("dataType must be 1 or 2 for now.")
            sys.exit(1)

    # save the generated intercept and weight
    np.savetxt(csv_weight_filename, weight.transpose(), delimiter=",")
    return weight


def generate_training_set_glm(csv_filename, row_count, col_count, min_p_value, max_p_value, data_type, family_type,
                              noise_std, weight, class_method='probability', class_margin=0.0):
    """
    Generate supervised data set given weights for the GLM algo.  First randomly generate the predictors, then
    call function generate_response_glm to generate the corresponding response y using the formula: y = w^T x+b+e
    where T is transpose, e is a random Gaussian noise added.  For the Binomial family, the relationship between
    the response Y and predictor vector X is assumed to be Prob(Y = 1|X) = exp(W^T * X + e)/(1+exp(W^T * X + e)).
    For the Multinomial family, the relationship between the response Y (K possible classes) and predictor vector
    X is assumed to be Prob(Y = c|X) = exp(Wc^T * X + e)/(sum k=0 to K-1 (ep(Wk^T *X+e)).  The predictors and
    responses are saved in a file specified by csv_filename.

    :param csv_filename: string representing full path filename to store supervised data set
    :param row_count: integer representing the number of training samples in the data set
    :param col_count: integer representing the number of predictors in the data set
    :param max_p_value: integer representing maximum predictor values
    :param min_p_value: integer representing minimum predictor values
    :param data_type: integer representing the type of predictors or weights (1: integers, 2: real)
    :param family_type: string represents the various distribution families (gaussian, multinomial, binomial) supported
    by our GLM algo
    :param noise_std: Gaussian noise standard deviation used to generate noise e to add to response
    :param weight: vector representing w in our formula to generate the response.
    :param class_method: string tuple, optional, describing how we derive the final response from the class
    probabilities generated for binomial and multinomial family-type for training/validation/test data set respectively.
    If set to 'probability', response y is generated randomly according to the class probabilities calculated.  If set
    to 'threshold', response y is set to the class with the maximum class probability if the maximum class probability
    exceeds the second highest class probability by the value set in the margin.  If the maximum class probability fails
    to be greater by the margin than the second highest class probability, the data sample is discarded.
    :param class_margin: float tuple, optional, denotes the threshold by how much the maximum class probability has to
     exceed the second highest class probability in order for us to keep the data sample for
     training/validation/test data set respectively.  This field is only meaningful if class_method is set to
    'threshold'

    :return: None
    """

    if data_type == 1:      # generate random integers
        x_mat = np.random.random_integers(min_p_value, max_p_value, [row_count, col_count])
    elif data_type == 2:   # generate random real numbers
        x_mat = np.random.uniform(min_p_value, max_p_value, [row_count, col_count])
    else:
        print("dataType must be 1 or 2 for now. ")
        sys.exit(1)

    # generate the response vector to the input predictors
    response_y = generate_response_glm(weight, x_mat, noise_std, family_type,
                                       class_method=class_method, class_margin=class_margin)

    # for family_type = 'multinomial' or 'binomial', response_y can be -ve to indicate bad sample data.
    # need to delete this data sample before proceeding
    if ('multinomial' in family_type.lower()) or ('binomial' in family_type.lower()):
        if 'threshold' in class_method.lower():
            if np.any(response_y < 0):  # remove negative entries out of data set
                (x_mat, response_y) = remove_negative_response(x_mat, response_y)

    # write to file in csv format
    np.savetxt(csv_filename, np.concatenate((x_mat, response_y), axis=1), delimiter=",")


def remove_negative_response(x_mat, response_y):
    """
    Recall that when the user chooses to generate a data set for multinomial or binomial using the 'threshold' method,
    response y is set to the class with the maximum class probability if the maximum class probability
    exceeds the second highest class probability by the value set in margin.  If the maximum class probability fails
    to be greater by margin than the second highest class probability, the data sample is discarded.  However, when we
    generate the data set, we keep all samples.  For data sample with maximum class probability that fails to be
    greater by margin than the second highest class probability, the response is set to be -1.  This function will
    remove all data samples (predictors and responses) with response set to -1.

    :param x_mat: predictor matrix containing all predictor values
    :param response_y: response that can be negative if that data sample is to be removed

    :return: tuple containing x_mat, response_y with negative data samples removed.
    """
    y_response_negative = np.where(response_y < 0)    # matrix of True or False
    x_mat = np.delete(x_mat,y_response_negative[0].transpose(),axis=0)  # remove predictor row with negative response

    # remove rows with negative response
    response_y = response_y[response_y >= 0]

    return x_mat,response_y.transpose()


def generate_training_set_mixed_glm(csv_filename, csv_filename_true_one_hot, row_count, col_count, min_p_value,
                                    max_p_value, family_type, noise_std, weight, enum_col, enum_level_vec,
                                    class_number=2, class_method='probability', class_margin=0.0):
    """
    Generate supervised data set given weights for the GLM algo with mixed categorical and real value
    predictors.  First randomly generate the predictors, then call function generate_response_glm to generate the
    corresponding response y using the formula: y = w^T x+b+e where T is transpose, e is a random Gaussian noise
    added.  For the Binomial family, the relationship between the response Y and predictor vector X is assumed to
    be Prob(Y = 1|X) = exp(W^T * X + e)/(1+exp(W^T * X + e)).  For the Multinomial family, the relationship between
    the response Y (K possible classes) and predictor vector X is assumed to be
    Prob(Y = c|X) = exp(Wc^T * X + e)/(sum k=0 to K-1 (ep(Wk^T *X+e)) e is the random Gaussian noise added to the
    response.  The predictors and responses are saved in a file specified by csv_filename.

    :param csv_filename: string representing full path filename to store supervised data set
    :param csv_filename_true_one_hot: string representing full path filename to store data set with true one-hot
        encoding.
    :param row_count: integer representing the number of training samples in the data set
    :param col_count: integer representing the number of predictors in the data set
    :param max_p_value: integer representing maximum predictor values
    :param min_p_value: integer representing minimum predictor values
    :param family_type: string represents the various distribution families (gaussian, multinomial, binomial)
    supported by our GLM algo
    :param noise_std: Gaussian noise standard deviation used to generate noise e to add to response
    :param weight: vector representing w in our formula to generate the response.
    :param enum_col: integer representing actual number of categorical columns in data set
    :param enum_level_vec: vector containing maximum integer value for each categorical column
    :param class_number: integer, optional, representing number classes for binomial and multinomial
    :param class_method: string, optional, describing how we derive the final response from the class probabilities
    generated for binomial and multinomial family_type.  If set to 'probability', response y is generated randomly
    according to the class probabilities calculated.  If set to 'threshold', response y is set to the class with
    the maximum class probability if the maximum class probability exceeds the second highest class probability by
    the value set in margin.  If the maximum class probability fails to be greater by margin than the second highest
    class probability, the data sample is discarded.
    :param class_margin: float, optional, denotes the threshold by how much the maximum class probability has to
    exceed the second highest class probability by in order for us to keep the data set sample.  This field is only
    meaningful if class_method is set to 'threshold'

    :return: None
    """
    # generate the random training data sets
    enum_dataset = np.zeros((row_count, enum_col), dtype=np.int)   # generate the categorical predictors

    # generate categorical data columns
    for indc in range(enum_col):
        enum_dataset[:, indc] = np.random.random_integers(0, enum_level_vec[indc], row_count)

    # generate real data columns
    x_mat = np.random.uniform(min_p_value, max_p_value, [row_count, col_count-enum_col])
    x_mat = np.concatenate((enum_dataset, x_mat), axis=1)   # concatenate categorical and real predictor columns

    if len(csv_filename_true_one_hot) > 0:
        generate_and_save_mixed_glm(csv_filename_true_one_hot, x_mat, enum_level_vec, enum_col, True, weight, noise_std,
                                    family_type, class_method=class_method, class_margin=class_margin)

    if len(csv_filename) > 0:
        generate_and_save_mixed_glm(csv_filename, x_mat, enum_level_vec, enum_col, False, weight, noise_std,
                                    family_type, class_method=class_method, class_margin=class_margin)


def generate_and_save_mixed_glm(csv_filename, x_mat, enum_level_vec, enum_col, true_one_hot, weight, noise_std,
                                family_type, class_method='probability', class_margin=0.0):
    """
    Given the weights and input data matrix with mixed categorical and real value predictors, this function will
      generate a supervised data set and save the input data and response in a csv format file specified by
      csv_filename.  It will first encode the enums without using one hot encoding with or without a reference
      level first before generating a response Y.

    :param csv_filename: string representing full path filename to store supervised data set with reference level
        plus true one-hot encoding.
    :param x_mat: predictor matrix with mixed columns (categorical/real values)
    :param enum_level_vec: vector containing maximum integer value for each categorical column
    :param enum_col: integer representing actual number of categorical columns in data set
    :param true_one_hot: bool indicating whether we are using true one hot encoding or reference level plus
        one hot encoding
    :param weight: vector representing w in our formula to generate the response
    :param noise_std: Gaussian noise standard deviation used to generate noise e to add to response
    :param family_type: string represents the various distribution families (gaussian, multinomial, binomial) supported
    by our GLM algo
    :param class_method: string, optional, describing how we derive the final response from the class probabilities
    generated for binomial and multinomial family_type.  If set to 'probability', response y is generated randomly
    according to the class probabilities calculated.  If set to 'threshold', response y is set to the class with the
    maximum class probability if the maximum class probability exceeds the second highest class probability by the
    value set in the margin.  If the maximum class probability fails to be greater by margin than the second highest
    class probability, the data sample is discarded.
    :param class_margin: float, optional, denotes the threshold by how much the maximum class probability has to exceed
    the second highest class probability in order for us to keep the data sample.  This field is only meaningful if
    class_method is set to 'threshold'

    :return: None
    """
    # encode the enums
    x_mat_encoded = encode_enum_dataset(x_mat, enum_level_vec, enum_col, true_one_hot, False)

    # extract the correct weight dimension for the data set
    if not true_one_hot:
        (num_row, num_col) = x_mat_encoded.shape
        weight = weight[0:num_col+1]    # +1 to take care of the intercept term

    # generate the corresponding response vector given the weight and encoded input predictors
    response_y = generate_response_glm(weight, x_mat_encoded, noise_std, family_type,
                                       class_method=class_method, class_margin=class_margin)

    # for familyType = 'multinomial' or 'binomial', response_y can be -ve to indicate bad sample data.
    # need to delete this before proceeding
    if ('multinomial' in family_type.lower()) or ('binomial' in family_type.lower()):
        if 'threshold' in class_method.lower():
            (x_mat,response_y) = remove_negative_response(x_mat, response_y)

    # write generated data set to file in csv format
    np.savetxt(csv_filename, np.concatenate((x_mat, response_y), axis=1), delimiter=",")


def encode_enum_dataset(dataset, enum_level_vec, enum_col, true_one_hot, include_nans):
    """
    Given 2-d numpy array of predictors with categorical and real columns, this function will
    encode the enum columns with 1-hot encoding or with reference plus one hot encoding

    :param dataset: 2-d numpy array of predictors with both categorical and real columns
    :param enum_level_vec: vector containing maximum level for each categorical column
    :param enum_col: number of categorical columns in the data set
    :param true_one_hot: bool indicating if we are using true one hot encoding or with one reference level + one hot
     encoding
    :param include_nans: bool indicating if we have nans in categorical columns

    :return: data set with categorical columns encoded with 1-hot encoding or 1-hot encoding plus reference
    """
    (num_row, num_col) = dataset.shape

    # split the data set into categorical and real parts
    enum_arrays = dataset[:, 0:enum_col]
    new_enum_arrays = []

    # perform the encoding for each element of categorical part
    for indc in range(enum_col):
        enum_col_num = enum_level_vec[indc]+1
        if not true_one_hot:
            enum_col_num -= 1

        if include_nans and np.any(enum_arrays[:, indc]):
            enum_col_num += 1

        new_temp_enum = np.zeros((num_row, enum_col_num), dtype=np.int)
        one_hot_matrix = one_hot_encoding(enum_col_num)
        last_col_index = enum_col_num-1

        # encode each enum using 1-hot encoding or plus reference value
        for indr in range(num_row):
            enum_val = enum_arrays[indr, indc]
            if true_one_hot:  # not using true one hot
                new_temp_enum[indr, :] = replace_with_encoded_bits(one_hot_matrix, enum_val, 0, last_col_index)
            else:
                if enum_val:
                    new_temp_enum[indr, :] = replace_with_encoded_bits(one_hot_matrix, enum_val, 1, last_col_index)

        if indc == 0:
            new_enum_arrays = new_temp_enum
        else:
            new_enum_arrays = np.concatenate((new_enum_arrays, new_temp_enum), axis=1)

    return np.concatenate((new_enum_arrays, dataset[:, enum_col:num_col]), axis=1)


def replace_with_encoded_bits(one_hot_matrix, enum_val, add_value, last_col_index):
    """
    Generate encoded bits for a categorical data value using one hot encoding.

    :param one_hot_matrix: matrix representing the encoding of categorical data value to 1-hot encoding
    :param enum_val: categorical data value, could be np.nan
    :param add_value: set to 1 if a reference value is needed in addition to 1-hot encoding
    :param last_col_index: index into encoding for np.nan if exists

    :return: vector representing the encoded values for a enum value
    """
    if np.isnan(enum_val):  # if data value is np.nan
        return one_hot_matrix[last_col_index]
    else:
        return one_hot_matrix[int(enum_val-add_value)]


def one_hot_encoding(enum_level):
    """
    Generate the one_hot_encoding matrix given the number of enum_level.

    :param enum_level: generate the actual one-hot encoding matrix

    :return: numpy array for the enum_level specified.  Note, enum_level <= 6
    """

    if enum_level >= 2:
        base_array = np.array([[0, 1], [1, 0]])     # for 2 enum levels

        for enum_index in range(3, enum_level+1):   # loop to build encoding for enum levels > 2
            (num_row, num_col) = base_array.shape
            col_zeros =  np.asmatrix(np.zeros(num_row)).transpose()     # column of zero matrix
            base_array = np.concatenate((col_zeros, base_array), axis=1)   # add column of zero
            row_zeros = np.asmatrix(np.zeros(num_row+1))    # add row of zeros
            row_zeros[0, 0] = 1                                # set first element to 1
            base_array = np.concatenate((base_array, row_zeros), axis=0)


        return base_array
    else:
        print ("enum_level must be >= 2.")
        sys.exit(1)


def generate_response_glm(weight, x_mat, noise_std, family_type, class_method='probability',
                          class_margin=0.0):
    """
    Generate response vector given weight matrix, predictors matrix for the GLM algo.

    :param weight: vector representing w in our formula to generate the response
    :param x_mat: random numpy matrix (2-D ndarray) containing the predictors
    :param noise_std: Gaussian noise standard deviation used to generate noise e to add to response
    :param family_type: string represents the various distribution families (Gaussian, multinomial, binomial)
    supported by our GLM algo
    :param class_method: string, optional, describing how we derive the final response from the class probabilities
    generated for binomial and multinomial familyType.  If set to 'probability', response y is generated randomly
    according to the class probabilities calculated.  If set to 'threshold', response y is set to the class with the
    maximum class probability if the maximum class probability exceeds the second highest class probability by the
    value set in the margin.  If the maximum class probability fails to be greater by margin than the second highest
    class probability, the data sample is discarded.
    :param class_margin: float, optional, denotes the threshold by how much the maximum class probability has to exceed
    the second highest class probability in order for us to keep the data set sample.  This field is only meaningful if
    class_method is set to 'threshold'

    :return: vector representing the response
    """
    (num_row, num_col) = x_mat.shape

    # add a column of 1's to x_mat
    temp_ones_col = np.asmatrix(np.ones(num_row)).transpose()
    x_mat = np.concatenate((temp_ones_col, x_mat), axis=1)

    # generate response given predictor and weight and add noise vector, default behavior
    response_y = x_mat * weight + noise_std * np.random.standard_normal([num_row, 1])

    # added more to form Multinomial response
    if ('multinomial' in family_type.lower()) or ('binomial' in family_type.lower()):
        temp_mat = np.exp(response_y)   # matrix of n by K where K = 1 for binomials

        if 'binomial' in family_type.lower():
            ntemp_mat = temp_mat + 1
            btemp_mat = temp_mat / ntemp_mat
            temp_mat = np.concatenate((1-btemp_mat, btemp_mat), axis=1)    # inflate temp_mat to 2 classes

        response_y = derive_discrete_response(temp_mat, class_method, class_margin)

    return response_y


def derive_discrete_response(prob_mat, class_method, class_margin):
    """
    This function is written to generate the final class response given the probabilities (Prob(y=k)).  There are
    two methods that we use and is specified by the class_method.  If class_method is set to 'probability',
    response y is generated randomly according to the class probabilities calculated.  If set to 'threshold',
    response y is set to the class with the maximum class probability if the maximum class probability exceeds the
    second highest class probability by the value set in margin.  If the maximum class probability fails to be
    greater by margin than the second highest class probability, the data sample will be discarded later by
    marking the final response as -1.

    :param prob_mat: probability matrix specifying the probability that y=k where k is a class
    :param class_method: string set to 'probability' or 'threshold'
    :param class_margin: if class_method='threshold', class_margin is the margin used to determine if a response is to
    be kept or discarded.

    :return: response vector representing class of y or -1 if an data sample is to be discarded.
    """

    (num_sample, num_class) = prob_mat.shape
    prob_mat = normalize_matrix(prob_mat)
    discrete_y = np.zeros((num_sample, 1), dtype=np.int)

    if 'probability' in class_method.lower():
        prob_mat = np.cumsum(prob_mat, axis=1)
        random_v = np.random.uniform(0, 1, [num_sample, 1])

        # choose the class that final response y belongs to according to the
        # probability prob(y=k)
        class_bool = random_v < prob_mat

        for indR in range(num_sample):
            for indC in range(num_class):
                if class_bool[indR, indC]:
                    discrete_y[indR, 0] = indC
                    break

    elif 'threshold' in class_method.lower():
        discrete_y = np.argmax(prob_mat, axis=1)

        temp_mat = np.diff(np.sort(prob_mat, axis=1), axis=1)

        # check if max value exceeds second one by at least margin
        mat_diff = temp_mat[:, num_class-2]
        mat_bool = mat_diff < class_margin

        discrete_y[mat_bool] = -1
    else:
        print('class_method should be set to "probability" or "threshold" only!')
        sys.exit(1)

    return discrete_y


def normalize_matrix(mat):
    """
    This function will normalize a matrix across each row such that the row sum is 1.

    :param mat: matrix containing prob(y=k)

    :return: normalized matrix containing prob(y=k)
    """
    (n, K) = mat.shape
    kronmat = np.ones((1, K), dtype=float)
    row_sum = np.sum(mat, axis=1)

    row_sum_mat = np.kron(row_sum, kronmat)
    return mat/row_sum_mat


def move_files(dir_path, old_name, new_file, action='move'):
    """
    Simple function to move or copy a data set (old_name) to a special directory (dir_path)
    with new name (new_file) so that we will be able to re-run the tests if we
    have found something wrong with the algorithm under test with the data set.
    This is done to avoid losing the data set.

    :param dir_path: string representing full directory path where a file is to be moved to
    :param old_name: string representing file (filename with full directory path) to be moved to new directory.
    :param new_file: string representing the file name of the moved in the new directory
    :param action: string, optional, represent the action 'move' or 'copy' file

    :return: None
    """
    new_name = os.path.join(dir_path, new_file) # generate new filename including directory path

    if os.path.isfile(old_name):    # only move/copy file old_name if it actually exists
        if 'move' in action:
            motion = 'mv '
        elif 'copy' in action:
            motion = 'cp '
        else:
            print("Illegal action setting.  It can only be 'move' or 'copy'!")
            sys.exit(1)

        cmd = motion+old_name+' '+new_name           # generate cmd line string to move the file

        subprocess.call(cmd, shell=True)


def remove_files(filename):
    """
    Simple function to remove data set saved in filename if the dynamic test is completed with no
    error.  Some data sets we use can be rather big.  This is performed to save space.

    :param filename: string representing the file to be removed.  Full path is included.

    :return: None
    """
    cmd = 'rm ' + filename
    subprocess.call(cmd, shell=True)


def random_col_duplication(num_cols, duplication_threshold, max_number, to_scale, max_scale_factor):
    """
    This function will randomly determine for each column if it should be duplicated.
    If it is to be duplicated, how many times, the duplication should be.  In addition, a
    scaling factor will be randomly applied to each duplicated column if enabled.

    :param num_cols: integer representing number of predictors used
    :param duplication_threshold: threshold to determine if a column is to be duplicated.  Set
    this number to be low if you want to encourage column duplication and vice versa
    :param max_number: maximum number of times a column is to be duplicated
    :param to_scale: bool indicating if a duplicated column is to be scaled
    :param max_scale_factor: real representing maximum scale value for repeated columns

    :return: a tuple containing two vectors: col_return, col_scale_return.
    col_return: vector indicating the column indices of the original data matrix that will be included
        in the new data matrix with duplicated columns
    col_scale_return: vector indicating for each new column in the new data matrix with duplicated columns,
        what scale should be applied to that column.
    """

    col_indices = list(range(num_cols))     # contains column indices of predictors in original data set
    col_scales = [1]*num_cols               # scaling factor for original data set, all ones.

    for ind in range(num_cols):         # determine for each column if to duplicate it
        temp = random.uniform(0, 1)     # generate random number from 0 to 1
        if temp > duplication_threshold:    # duplicate column if random number generated exceeds duplication_threshold
            rep_num = random.randint(1, max_number)  # randomly determine how many times to repeat a column

            more_col_indices = [ind]*rep_num
            col_indices.extend(more_col_indices)
            temp_scale = []

            for ind in range(rep_num):
                if to_scale:        # for each duplicated column, determine a scaling factor to multiply the column with
                    temp_scale.append(random.uniform(0, max_scale_factor))
                else:
                    temp_scale.append(1)

            col_scales.extend(temp_scale)

    # randomly shuffle the predictor column orders and the corresponding scaling factors
    new_col_indices = list(range(len(col_indices)))
    random.shuffle(new_col_indices)
    col_return = [col_indices[i] for i in new_col_indices]
    col_scale_return = [col_scales[i] for i in new_col_indices]

    return col_return, col_scale_return


def duplicate_scale_cols(col_indices, col_scale, old_filename, new_filename):
    """
    This function actually performs the column duplication with scaling giving the column
    indices and scaling factors for each column.  It will first load the original data set
    from old_filename.  After performing column duplication and scaling, the new data set
    will be written to file with new_filename.

    :param col_indices: vector indicating the column indices of the original data matrix that will be included
        in the new data matrix with duplicated columns
    :param col_scale: vector indicating for each new column in the new data matrix with duplicated columns,
        what scale should be applied to that column
    :param old_filename: string representing full directory path and filename where data set is stored
    :param new_filename: string representing full directory path and filename where new data set is to be stored

    :return: None
    """
    # pd_frame = pd.read_csv(old_filename, header=None)   # read in original data set
    #
    # pd_frame_new = pd.DataFrame()                       # new empty data frame
    #
    # for ind in range(len(col_indices)):     # for each column
    #     tempc = pd_frame.ix[:, col_indices[ind]]*col_scale[ind]  # extract a column from old data frame and scale it
    #     pd_frame_new = pd.concat([pd_frame_new, tempc], axis=1)   # add it to the new data frame

    np_frame = np.asmatrix(np.genfromtxt(old_filename, delimiter=',', dtype=None))
    (num_row, num_col) = np_frame.shape
    np_frame_new = np.asmatrix(np.zeros((num_row, len(col_indices)), dtype=np.float))

    for ind in range(len(col_indices)):
        np_frame_new[:, ind] = np_frame[:, col_indices[ind]]*col_scale[ind]

    # done changing the data frame.  Save it in a new file
    np.savetxt(new_filename, np_frame_new, delimiter=",")


def insert_nan_in_data(old_filename, new_filename, missing_fraction):
    """
    Give the filename of a data set stored in old_filename, this function will randomly determine
    for each predictor to replace its value with nan or not with probability missing_frac.  The
    new data set will be stored in filename new_filename.

    :param old_filename: string representing full directory path and filename where data set is stored
    :param new_filename: string representing full directory path and filename where new data set with missing
        values is to be stored
    :param missing_fraction: real value representing the probability of replacing a predictor with nan.


    :return: None
    """
#    pd_frame = pd.read_csv(old_filename, header=None)    # read in a dataset
    np_frame = np.asmatrix(np.genfromtxt(old_filename, delimiter=',', dtype=None))
    (row_count, col_count) = np_frame.shape
    random_matrix = np.random.uniform(0, 1, [row_count, col_count-1])

    for indr in range(row_count):    # for each predictor value, determine if to replace value with nan
        for indc in range(col_count-1):
            if random_matrix[indr, indc] < missing_fraction:
                np_frame[indr, indc] = np.nan

    # save new data set with missing values to new file
    np.savetxt(new_filename, np_frame, delimiter=",")
#    pd_frame.to_csv(new_filename, sep=',', header=False, index=False, na_rep='nan')


def print_message_values(start_string, nump_array):
    """
    This function prints the value of a nump_array with a string message in front of it.

    :param start_string: string representing message to be printed
    :param nump_array: array storing something

    :return: None
    """
    print(start_string)
    print(nump_array)


def show_test_results(test_name, curr_test_val, new_test_val):
    """
    This function prints the test execution results which can be passed or failed.  A message will be printed on
    screen to warn user of the test result.

    :param test_name: string representing test name
    :param curr_test_val: integer representing number of tests failed so far before the test specified in test_name
    is executed
    :param new_test_val: integer representing number of tests failed after the test specified in test_name is
    executed

    :return: integer: 0 if test passed and 1 if test faild.
    """
    failed_string = "Ooops, " + test_name + " failed.  I am sorry..."
    pass_string = "Yeah, " + test_name + " passed!"

    if (curr_test_val < new_test_val):   # this test has failed
        print(failed_string)

        return 1
    else:
        print(pass_string)
        return 0


def equal_two_arrays(array1, array2, eps, tolerance):
    """
    This function will compare the values of two python tuples.  First, if the values are below
    eps which denotes the significance level that we care, no comparison is performed.  Next,
    False is returned if the different between any elements of the two array exceeds some tolerance.

    :param array1: numpy array containing some values of interest
    :param array2: numpy array containing some values of interest that we would like to compare it with array1
    :param eps: significance level that we care about in order to perform the comparison
    :param tolerance: threshold for which we allow the two array elements to be different by

    :return: True if elements in array1 and array2 are close and False otherwise
    """

    size1 = len(array1)
    if size1 == len(array2):    # arrays must be the same size
        # compare two arrays
        for ind in range(size1):
            if not ((array1[ind] < eps) and (array2[ind] < eps)):
                # values to be compared are not too small, perform comparison

                # look at differences between elements of array1 and array2
                compare_val_h2o_Py = abs(array1[ind] - array2[ind])

                if compare_val_h2o_Py > tolerance:    # difference is too high, return false
                    return False

        return True                                     # return True, elements of two arrays are close enough
    else:
        print("The two arrays are of different size!")
        sys.exit(1)


def compare_two_arrays(array1, array2, eps, tolerance, comparison_string, array1_string, array2_string, error_string,
                       success_string, template_is_better, just_print=False):
    """
    This function is written to print out the performance comparison results for various values that
    we care about.  It will return 1 if the values of the two arrays exceed threshold specified in tolerance.
    The actual comparison is performed by calling function equal_two_array.

    :param array1: numpy array containing some values of interest
    :param array2: numpy array containing some values of interest that we would like to compare it with array1
    :param eps: significance level that we care about in order to perform the comparison
    :param tolerance: threshold for which we allow the two array elements to be different by
    :param comparison_string: string stating what the comparison is about, e.g. "Comparing p-values ...."
    :param array1_string: string stating what is the array1 attribute of interest, e.g. "H2O p-values: "
    :param array2_string: string stating what is the array2 attribute of interest, e.g. "Theoretical p-values: "
    :param error_string: string stating what you want to say if the difference between array1 and array2
    exceeds tolerance, e.g "P-values are not equal!"
    :param success_string: string stating what you want to say if the difference between array1 and array2 does not
        exceed tolerance   "P-values are close enough!"
    :param template_is_better: bool, True, will return 1 if difference among elements of array1 and array2 exceeds
    tolerance.  False, will always return 0 even if difference among elements of array1 and array2 exceeds tolerance.
    In this case, the system under test actually performs better than the template.
    :param just_print: bool if True will print attribute values without doing comparison.  False will print
    attribute values and perform comparison

    :return: if template_is_better = True, return 0 if elements in array1 and array2 are close and 1 otherwise;
             if template_is_better = False, will always return 0 since system under tests performs better than
             template system.
    """

    # display array1, array2 with proper description
    print(comparison_string)
    print(array1_string, array1)
    print(array2_string, array2)

    if just_print:   # just print the two values and do no comparison
        return 0
    else:   # may need to actually perform comparison
        if template_is_better:
            try:
                assert equal_two_arrays(array1, array2, eps, tolerance), error_string
                print(success_string)
                sys.stdout.flush()
                return 0
            except:
                sys.stdout.flush()
                return 1
        else:
            print("Test result is actually better than comparison template!")
            return 0


def make_Rsandbox_dir(base_dir, test_name, make_dir):
    """
    This function will remove directory "Rsandbox/test_name" off directory base_dir and contents if it exists.
    If make_dir is True, it will create a clean directory "Rsandbox/test_name" off directory base_dir.

    :param base_dir: string contains directory path where we want to build our Rsandbox/test_name off from
    :param test_name: string contains unit test name that the Rsandbox is created for
    :param make_dir: bool, True: will create directory baseDir/Rsandbox/test_name, False: will not create
        directory.

    :return: syndatasets_dir: string containing the full path of the directory name specified by base_dir, test_name
    """

    # create the Rsandbox directory path for the test.
    syndatasets_dir = os.path.join(base_dir, "Rsandbox_" + test_name)
    if os.path.exists(syndatasets_dir):     # remove Rsandbox directory if it exists
        shutil.rmtree(syndatasets_dir)

    if make_dir:    # create Rsandbox directory if make_dir is True
        os.mkdir(syndatasets_dir)

    return syndatasets_dir


def get_train_glm_params(model, what_param, family_type='gaussian'):
    """
    This function will grab the various attributes (like coefficients, p-values, and others) off a GLM
    model that has been built.

    :param model: GLM model that we want to extract information from
    :param what_param: string indicating the model attribute of interest like 'p-value','weights',...
    :param family_type: string, optional, represents the various distribution families (gaussian, multinomial, binomial)
    supported by our GLM algo

    :return: attribute value of interest
    """
    coeff_pvalues = model._model_json["output"]["coefficients_table"].cell_values
    if what_param == 'p-values':
        if 'gaussian' in family_type.lower():
            p_value_h2o = []

            for ind in range(len(coeff_pvalues)):
                p_value_h2o.append(coeff_pvalues[ind][-1])
            return p_value_h2o

        else:
            print("P-values are only available to Gaussian family.")
            sys.exit(1)

    elif what_param == 'weights':
        if 'gaussian' in family_type.lower():
            weights = []

            for ind in range(len(coeff_pvalues)):
                weights.append(coeff_pvalues[ind][1])
            return weights
        elif ('multinomial' in family_type.lower()) or ('binomial' in family_type.lower()):
            # for multinomial, the coefficients are organized as features by number of classes for
            # nonstandardized and then standardized weights.  Need to grab the correct matrix as
            # number of classes by n_features matrix
            num_feature = len(coeff_pvalues)
            num_class = (len(coeff_pvalues[0])-1)/2

            coeffs = np.zeros((num_class,num_feature), dtype=np.float)

            end_index = int(num_class+1)
            for col_index in range(len(coeff_pvalues)):
                coeffs[:, col_index] = coeff_pvalues[col_index][1:end_index]

            return coeffs
    elif what_param == 'best_lambda':
        lambda_str = model._model_json["output"]["model_summary"].cell_values[0][4].split('=')
        return float(lambda_str[-1])
    elif what_param == 'confusion_matrix':
        if 'multinomial' in family_type.lower():
            return model._model_json["output"]["training_metrics"]._metric_json["cm"]["table"]
        elif 'binomial' in family_type.lower():
            return model.confusion_matrix().table
    else:
        print("parameter value not found in GLM model")
        sys.exit(1)


def less_than(val1, val2):
    """
    Simple function that returns True if val1 <= val2 and False otherwise.

    :param val1: first value of interest
    :param val2: second value of interest

    :return: bool: True if val1 <= val2 and False otherwise
    """
    if round(val1, 3) <= round(val2, 3):     # only care to the 3rd position after decimal point
        return True
    else:
        return False


def replace_nan_with_mean(data_with_nans, nans_row_col_indices, col_means):
    """
    Given a data set with nans, row and column indices of where the nans are and the col_means, this
    function will replace the nans with the corresponding col_means.

    :param data_with_nans: data set matrix with nans
    :param nans_row_col_indices: matrix containing the row and column indices of where the nans are
    :param col_means: vector containing the column means of data_with_NAs

    :return: data_with_NAs: data set with nans replaced with column means
    """
    num_NAs = len(nans_row_col_indices[0])

    for ind in range(num_NAs):
        data_with_nans[nans_row_col_indices[0][ind], nans_row_col_indices[1][ind]] = \
            col_means[nans_row_col_indices[1][ind]]

    return data_with_nans


def remove_csv_files(dir_path, suffix=".csv", action='remove', new_dir_path=""):
    """
    Given a directory, this function will gather all function ending with string specified
    in suffix.  Next, it is going to delete those files if action is set to 'remove'.  If
    action is set to 'copy', a new_dir_path must be specified where the files ending with suffix
    will be moved to this new directory instead.

    :param dir_path: string representing full path to directory of interest
    :param suffix: string representing suffix of filename that are to be found and deleted
    :param action: string, optional, denote the action to perform on files, 'remove' or 'move'
    :param new_dir_path: string, optional, representing full path to new directory

    :return: None
    """
    filenames = os.listdir(dir_path)    # list all files in directory

    # only collect files with filename ending with suffix
    to_remove = [filename for filename in filenames if filename.endswith(suffix)]

    # delete files ending with suffix
    for fn in to_remove:
        temp_fn = os.path.join(dir_path, fn)

        # only remove if file actually exists.
        if os.path.isfile(temp_fn):
            if 'remove' in action:
                remove_files(temp_fn)
            elif 'copy' in action:
                move_files(new_dir_path, temp_fn, fn, action=action)
            else:
                print("action string can only be 'remove' or 'copy.")
                sys.exit(1)


def extract_comparison_attributes_and_print(model_h2o, h2o_model_test_metrics, end_test_str, want_p_values,
                                            attr1_bool, attr2_bool, att1_template, att2_template, att3_template,
                                            att4_template, compare_att1_str, h2o_att1_str, template_att1_str,
                                            att1_str_fail, att1_str_success, compare_att2_str, h2o_att2_str,
                                            template_att2_str, att2_str_fail, att2_str_success, compare_att3_str,
                                            h2o_att3_str, template_att3_str, att3_str_fail, att3_str_success,
                                            compare_att4_str, h2o_att4_str, template_att4_str, att4_str_fail,
                                            att4_str_success, failed_test_number, ignored_eps, allowed_diff,
                                            noise_var, template_must_be_better, attr3_bool=True, attr4_bool=True):
    """
    This function basically will compare four attributes (weight, p-values, training data MSE, test data MSE) of a test
    with a template model.  If the difference of comparison exceeds a certain threshold, the test will be determined as
    failed and vice versa.  There are times when we do not care about p-values and/or weight comparisons but mainly
    concerned with MSEs.  We can set the input parameters to indicate if this is the case.

    :param model_h2o:  H2O model that we want to evaluate
    :param h2o_model_test_metrics: test performance of H2O model under evaluation
    :param end_test_str: string representing end test banner to be printed
    :param want_p_values: bool True if we want to care about p-values and False if we don't
    :param attr1_bool: bool True if we want to compare weight difference between H2O model and template model
        and False otherwise.
    :param attr2_bool: bool True if we want to compare p-value difference between H2O model and template model
        and False otherwise.
    :param att1_template: value of first template attribute, the weight vector
    :param att2_template: value of second template attribute, the p-value vector
    :param att3_template: value of third template attribute, the training data set MSE
    :param att4_template: value of fourth template attribute, the test data set MSE
    :param compare_att1_str: string describing the comparison of first attribute, e.g. "Comparing intercept and
    weights ...."
    :param h2o_att1_str: string describing H2O model first attribute values, e.g. "H2O intercept and weights: "
    :param template_att1_str: string describing template first attribute values, e.g. "Theoretical intercept and
    weights: "
    :param att1_str_fail: string describing message to print out if difference exceeds threshold, e.g.
    "Intercept and weights are not equal!"
    :param att1_str_success: string describing message to print out if difference < threshold, e.g.
    "Intercept and weights are close enough!"
    :param compare_att2_str: string describing the comparison of first attribute, e.g. "Comparing p-values ...."
    :param h2o_att2_str: string describing H2O model first attribute values, e.g. "H2O p-values: "
    :param template_att2_str: string describing template first attribute values, e.g. "Theoretical p-values: "
    :param att2_str_fail: string describing message to print out if difference exceeds threshold, e.g.
    "P-values are not equal!"
    :param att2_str_success: string describing message to print out if difference < threshold, e.g.
    "P-values are close enough!"
    :param compare_att3_str: string describing the comparison of first attribute, e.g. "Comparing training MSEs ...."
    :param h2o_att3_str: string describing H2O model first attribute values, e.g. "H2O training MSE: "
    :param template_att3_str: string describing template first attribute values, e.g. "Theoretical train MSE: "
    :param att3_str_fail: string describing message to print out if difference exceeds threshold, e.g.
    "Training MSEs are not equal!"
    :param att3_str_success: string describing message to print out if difference < threshold, e.g.
    "Training MSEs are close enough!"
    :param compare_att4_str: string describing the comparison of first attribute, e.g. "Comparing test MSEs ...."
    :param h2o_att4_str: string describing H2O model first attribute values, e.g. "H2O test MSE: "
    :param template_att4_str: string describing template first attribute values, e.g. "Theoretical test MSE: "
    :param att4_str_fail: string describing message to print out if difference exceeds threshold, e.g.
    "Test MSEs are not equal!"
    :param att4_str_success: string describing message to print out if difference < threshold, e.g.
    "Test MSEs are close enough!"
    :param failed_test_number: integer denote the number of tests failed
    :param ignored_eps: if value < than this value, no comparison is performed
    :param allowed_diff: threshold if exceeded will fail a test
    :param noise_var: Gaussian noise variance used to generate data set
    :param template_must_be_better: bool: True: template value must be lower, False: don't care
    :param attr3_bool: bool denoting if we should compare attribute 3 values
    :param attr4_bool: bool denoting if we should compare attribute 4 values


    :return: a tuple containing test h2o model training and test performance metrics that include: weight, pValues,
    mse_train, r2_train, mse_test, r2_test
    """

    # grab weight from h2o model
    test1_weight = get_train_glm_params(model_h2o, 'weights')

    # grab p-values from h2o model
    test1_p_values = []
    if want_p_values:
        test1_p_values = get_train_glm_params(model_h2o, 'p-values')

    # grab other performance metrics
    test1_mse_train = model_h2o.mse()
    test1_r2_train = model_h2o.r2()
    test1_mse_test = h2o_model_test_metrics.mse()
    test1_r2_test = h2o_model_test_metrics.r2()

    # compare performances of template and h2o model weights
    failed_test_number += compare_two_arrays(test1_weight, att1_template, ignored_eps, allowed_diff*100, compare_att1_str,
                                             h2o_att1_str, template_att1_str, att1_str_fail, att1_str_success,
                                             attr1_bool)

    # p-values
    if want_p_values:
        if np.isnan(np.asarray(test1_p_values)).any():  # p-values contain nan
            failed_test_number += 1

        failed_test_number += compare_two_arrays(test1_p_values, att2_template, ignored_eps, allowed_diff,
                                                 compare_att2_str, h2o_att2_str, template_att2_str, att2_str_fail,
                                                 att2_str_success, attr2_bool)

    # Training MSE
    need_to_compare = less_than(att3_template, test1_mse_train)

    # in some cases, template value should always be better.  Training data MSE should always
    # be better without regularization than with regularization
    if (not need_to_compare) and template_must_be_better:
        failed_test_number += 1

    failed_test_number += compare_two_arrays([test1_mse_train], [att3_template], ignored_eps, noise_var,
                                             compare_att3_str, h2o_att3_str,
                                             template_att3_str, att3_str_fail, att3_str_success, attr3_bool)

    # Test MSE
    need_to_compare = less_than(att4_template, test1_mse_test)
    failed_test_number += compare_two_arrays([test1_mse_test], [att4_template], ignored_eps, noise_var,
                                             compare_att4_str, h2o_att4_str, template_att4_str, att4_str_fail,
                                             att4_str_success, need_to_compare, attr4_bool)

    # print end test banner
    print(end_test_str)
    print("*******************************************************************************************")

    sys.stdout.flush()

    return test1_weight, test1_p_values, test1_mse_train, test1_r2_train, test1_mse_test,\
           test1_r2_test, failed_test_number


def extract_comparison_attributes_and_print_multinomial(model_h2o, h2o_model_test_metrics, family_type, end_test_str,
                                                        compare_att_str=["", "", "", "", "", "", ""],
                                                        h2o_att_str=["", "", "", "", "", "", ""],
                                                        template_att_str=["", "", "", "", "", "", ""],
                                                        att_str_fail=["", "", "", "", "", "", ""],
                                                        att_str_success=["", "", "", "", "", "", ""],
                                                        test_model=None, test_model_metric=None, template_params=None,
                                                        can_be_better_than_template=[
                                                            False, False, False, False, False, False],
                                                        just_print=[True, True, True, True, True, True],
                                                        ignored_eps=1e-15, allowed_diff=1e-5, failed_test_number=0):
    """
    This function basically will compare and print out six performance metrics of a test with a
    template model.  If the difference of comparison exceeds a certain threshold, the test will be determined as
    failed and vice versa.  There are times when we do not care about comparisons but mainly concerned with
    logloss/prediction accuracy in determining if a test shall fail.  We can set the input parameters to indicate
    if this is the case.

    :param model_h2o: H2O model that we want to evaluate
    :param h2o_model_test_metrics: test performance of H2O model under evaluation
    :param family_type: string represents the various distribution families (gaussian, multinomial, binomial)
    supported by our GLM algo
    :param end_test_str: string to be printed at the end of a test
    :param compare_att_str: array of strings describing what we are trying to compare
    :param h2o_att_str: array of strings describing each H2O attribute of interest
    :param template_att_str: array of strings describing template attribute of interest
    :param att_str_fail: array of strings to be printed if the comparison failed
    :param att_str_success: array of strings to be printed if comparison succeeded
    :param test_model: template model whose attributes we want to compare our H2O model with
    :param test_model_metric: performance on test data set of template model
    :param template_params: array containing template attribute values that we want to compare our H2O model with
    :param can_be_better_than_template: array of bool: True: template value must be lower, False: don't care
    :param just_print: array of bool for each attribute if True, no comparison is performed, just print the attributes
    and if False, will compare the attributes and print the attributes as well
    :param ignored_eps: if value < than this value, no comparison is performed
    :param allowed_diff: threshold if exceeded will fail a test
    :param failed_test_number: integer denote the number of tests failed so far

    :return: accumulated number of tests that have failed so far
    """

    # grab performance metrics from h2o model
    (h2o_weight, h2o_logloss_train, h2o_confusion_matrix_train, h2o_accuracy_train, h2o_logloss_test,
     h2o_confusion_matrix_test, h2o_accuracy_test) = grab_model_params_metrics(model_h2o, h2o_model_test_metrics,
                                                                               family_type)
    # grab performance metrics from template model
    if test_model and test_model_metric:
        (template_weight, template_logloss_train, template_confusion_matrix_train, template_accuracy_train,
         template_logloss_test, template_confusion_matrix_test, template_accuracy_test) = \
            grab_model_params_metrics(test_model, test_model_metric, family_type)
    elif template_params:
        # grab template comparison values from somewhere else

        (template_weight, template_logloss_train, template_confusion_matrix_train, template_accuracy_train,
         template_logloss_test, template_confusion_matrix_test, template_accuracy_test) = template_params
    else:
        print("No valid template parameters are given for comparison.")
        sys.exit(1)

    # print and/or compare the weights between template and H2O
    compare_index = 0
    failed_test_number += compare_two_arrays(h2o_weight, template_weight, ignored_eps, allowed_diff,
                                             compare_att_str[compare_index], h2o_att_str[compare_index],
                                             template_att_str[compare_index], att_str_fail[compare_index],
                                             att_str_success[compare_index], True, just_print[compare_index])
    compare_index += 1
    # this is logloss from training data set,
    if not(just_print[compare_index]) and not(can_be_better_than_template[compare_index]):
        if (h2o_logloss_train < template_logloss_train) and \
                (abs(h2o_logloss_train-template_logloss_train) > 1e-5):

            # H2O performed better than template which is not allowed
            failed_test_number += 1     # increment failed_test_number and just print the results
            compare_two_arrays([h2o_logloss_train], [template_logloss_train], ignored_eps, allowed_diff,
                               compare_att_str[compare_index], h2o_att_str[compare_index],
                               template_att_str[compare_index], att_str_fail[compare_index],
                               att_str_success[compare_index], True, True)
        else:
            failed_test_number += compare_two_arrays([h2o_logloss_train], [template_logloss_train], ignored_eps,
                                                     allowed_diff, compare_att_str[compare_index],
                                                     h2o_att_str[compare_index], template_att_str[compare_index],
                                                     att_str_fail[compare_index], att_str_success[compare_index], True,
                                                     False)

    else:
        template_better = is_template_better(just_print[compare_index], can_be_better_than_template[compare_index],
                                             h2o_logloss_train, template_logloss_train, False)
        # print and compare the logloss between template and H2O for training data
        failed_test_number += compare_two_arrays([h2o_logloss_train], [template_logloss_train], ignored_eps,
                                                 allowed_diff, compare_att_str[compare_index],
                                                 h2o_att_str[compare_index], template_att_str[compare_index],
                                                 att_str_fail[compare_index], att_str_success[compare_index],
                                                 template_better, just_print[compare_index])
    compare_index += 1
    template_better = is_template_better(just_print[compare_index], can_be_better_than_template[compare_index],
                                         h2o_logloss_test, template_logloss_test, False)
    # print and compare the logloss between template and H2O for test data
    failed_test_number += compare_two_arrays([h2o_logloss_test], [template_logloss_test], ignored_eps, allowed_diff,
                                             compare_att_str[compare_index], h2o_att_str[compare_index],
                                             template_att_str[compare_index], att_str_fail[compare_index],
                                             att_str_success[compare_index], template_better, just_print[compare_index])
    compare_index += 1
    # print the confusion matrix from training data
    failed_test_number += compare_two_arrays(h2o_confusion_matrix_train, template_confusion_matrix_train, ignored_eps,
                                             allowed_diff, compare_att_str[compare_index], h2o_att_str[compare_index],
                                             template_att_str[compare_index], att_str_fail[compare_index],
                                             att_str_success[compare_index], True, just_print[compare_index])
    compare_index += 1
    # print the confusion matrix from test data
    failed_test_number += compare_two_arrays(h2o_confusion_matrix_test, template_confusion_matrix_test, ignored_eps,
                                             allowed_diff, compare_att_str[compare_index], h2o_att_str[compare_index],
                                             template_att_str[compare_index], att_str_fail[compare_index],
                                             att_str_success[compare_index], True, just_print[compare_index])
    compare_index += 1
    template_better = is_template_better(just_print[compare_index], can_be_better_than_template[compare_index],
                                         h2o_accuracy_train, template_accuracy_train, True)
    # print accuracy from training dataset
    failed_test_number += compare_two_arrays([h2o_accuracy_train], [template_accuracy_train], ignored_eps, allowed_diff,
                                             compare_att_str[compare_index], h2o_att_str[compare_index],
                                             template_att_str[compare_index], att_str_fail[compare_index],
                                             att_str_success[compare_index], template_better, just_print[compare_index])
    compare_index += 1
    # print accuracy from test dataset
    template_better = is_template_better(just_print[compare_index], can_be_better_than_template[compare_index],
                                         h2o_accuracy_test, template_accuracy_test, True)
    failed_test_number += compare_two_arrays([h2o_accuracy_test], [template_accuracy_test], ignored_eps, allowed_diff,
                                             compare_att_str[compare_index], h2o_att_str[compare_index],
                                             template_att_str[compare_index], att_str_fail[compare_index],
                                             att_str_success[compare_index], template_better, just_print[compare_index])
    # print end test banner
    print(end_test_str)
    print("*******************************************************************************************")
    sys.stdout.flush()

    return failed_test_number


def is_template_better(just_print, can_be_better_than_template, h2o_att, template_att, bigger_is_better):
    """
    This function is written to determine if the system under test performs better than the template model
    performance.

    :param just_print: bool representing if we are just interested in printing the attribute values
    :param can_be_better_than_template: bool stating that it is okay in this case for the system under test to perform
    better than the template system.
    :param h2o_att: number representing the h2o attribute under test
    :param template_att: number representing the template attribute
    :param bigger_is_better: bool representing if metric is perceived to be better if its value is higher
    :return: bool indicating if the template attribute is better.
    """

    if just_print:      # not interested in comparison, just want to print attribute values
        return True     # does not matter what we return here
    else:
        if bigger_is_better:    # metric is better if it is greater
            return not(h2o_att > template_att)
        else:                   # metric is better if it is less
            return not(h2o_att < template_att)


def grab_model_params_metrics(model_h2o, h2o_model_test_metrics, family_type):
    """
    This function will extract and return the various metrics from a H2O GLM model and the corresponding H2O model
    test metrics.

    :param model_h2o: GLM H2O model
    :param h2o_model_test_metrics: performance on test data set from H2O GLM model
    :param family_type: string representing 'gaussian', 'binomial' or 'multinomial'

    :return: tuple containing weight, logloss/confusion matrix/prediction accuracy calculated from training data set
    and test data set respectively
    """

    # grab weight from h2o model
    h2o_weight = get_train_glm_params(model_h2o, 'weights', family_type=family_type)

    # grab other performance metrics
    h2o_logloss_train = model_h2o.logloss()
    h2o_confusion_matrix_train = get_train_glm_params(model_h2o, 'confusion_matrix', family_type=family_type)
    last_index = len(h2o_confusion_matrix_train.cell_values)-1

    h2o_logloss_test = h2o_model_test_metrics.logloss()

    if 'multinomial' in family_type.lower():
        h2o_confusion_matrix_test = h2o_model_test_metrics.confusion_matrix()
        h2o_accuracy_train = 1-h2o_confusion_matrix_train.cell_values[last_index][last_index]
        h2o_accuracy_test = 1-h2o_confusion_matrix_test.cell_values[last_index][last_index]
    elif 'binomial' in family_type.lower():
        h2o_confusion_matrix_test = h2o_model_test_metrics.confusion_matrix().table
        real_last_index = last_index+1
        h2o_accuracy_train = 1-float(h2o_confusion_matrix_train.cell_values[last_index][real_last_index])
        h2o_accuracy_test = 1-float(h2o_confusion_matrix_test.cell_values[last_index][real_last_index])
    else:
        print("Only 'multinomial' and 'binomial' distribution families are supported for grab_model_params_metrics "
              "function!")
        sys.exit(1)

    return h2o_weight, h2o_logloss_train, h2o_confusion_matrix_train, h2o_accuracy_train, h2o_logloss_test,\
           h2o_confusion_matrix_test, h2o_accuracy_test


def prepare_data_sklearn_multinomial(training_data_xy):
    """
    Sklearn model requires that the input matrix should contain a column of ones in order for
    it to generate the intercept term.  In addition, it wants the response vector to be in a
    certain format as well.

    :param training_data_xy: matrix containing both the predictors and response column

    :return: tuple containing the predictor columns with a column of ones as the first column and
    the response vector in the format that Sklearn wants.
    """
    (num_row, num_col) = training_data_xy.shape

    # change response to be enum and not real
    y_ind = num_col-1
    training_data_xy[y_ind] = training_data_xy[y_ind].astype(int)

    # prepare response column for sklearn logistic regression
    response_y = training_data_xy[:, y_ind]
    response_y = np.ravel(response_y)

    training_data = training_data_xy[:, range(0, y_ind)]

    # added column of ones into data matrix X_MAT
    temp_ones = np.asmatrix(np.ones(num_row)).transpose()
    x_mat = np.concatenate((temp_ones, training_data), axis=1)

    return response_y, x_mat

def get_gridables(params_in_json):
    """
    This function is written to walk through all parameters of a model and grab the parameters, its type  and
    its default values as three lists of all the gridable parameters.

    :param params_in_json: a list of parameters associated with a H2O model.  Each list is a dict containing fields
    of interest like name, type, gridable, default values, ....

    :return: three lists: gridable_params, gridable_types and gridable_defaults containing the names of the parameter,
    its associated type like int, float, unicode, bool and default parameter values
    """

    # grab all gridable parameters and its type
    gridable_parameters = []
    gridable_types = []
    gridable_defaults = []

    for each_param in params_in_json:
        if each_param['gridable']:
            gridable_parameters.append(str(each_param["name"]))
            gridable_types.append(each_param["type"])
            gridable_defaults.append(each_param["default_value"])

    return gridable_parameters, gridable_types, gridable_defaults


def add_fold_weights_offset_columns(h2o_frame, nfold_max_weight_offset, column_names, column_type='fold_assignment'):
    """
    Add fold_columns to H2O training frame specified in h2o_frame according to nfold.  The new added
    columns should use the names in column_names.  Returns a h2o_frame with newly added fold_columns.
    Copied from Eric's code.

    :param h2o_frame: H2O frame containing training data
    :param nfold_max_weight_offset: integer, number of fold in the cross-validation or maximum weight scale or offset
    :param column_names: list of strings denoting the column names for the new fold columns
    :param column_type: optional string denoting whether we are trying to generate fold_assignment or
    weights_column or offset_column

    :return: H2O frame with added fold column assignments
    """

    number_row = h2o_frame.nrow

    # copied this part from Eric's code
    for index in range(len(column_names)):
        if 'fold_assignment' in column_type:
            temp_a = np.random.random_integers(0, nfold_max_weight_offset - 1, [number_row, 1])     # inclusive
        elif 'weights_column' in column_type:
            temp_a = np.random.uniform(0, nfold_max_weight_offset, [number_row, 1])
        elif 'offset_column' in column_type:
            temp_a = random.uniform(0, nfold_max_weight_offset)*np.asmatrix(np.ones(number_row)).transpose()
        else:
            print("column_type must be either 'fold_assignment' or 'weights_column'!")
            sys.exit(1)

        fold_assignments = h2o.H2OFrame(temp_a)
        fold_assignments.set_names([column_names[index]])
        h2o_frame = h2o_frame.cbind(fold_assignments)

    return h2o_frame


def gen_grid_search(model_params, hyper_params, exclude_parameters, gridable_parameters, gridable_types,
                    gridable_defaults, max_int_number, max_int_val, min_int_val, max_real_number, max_real_val,
                    min_real_val, quantize_level='1.00000000'):
    """
    This function is written to randomly generate griddable parameters for a gridsearch.  For parameters already
    found in hyper_params, no random list will be generated.  In addition, we will check to make sure that the
    griddable parameters are actually used by the model before adding them to the hyper_params dict.

    :param model_params: list of string containing names of argument to the model
    :param hyper_params: dict structure containing a list of gridable parameters names with their list
    :param exclude_parameters: list containing parameter names not to be added to hyper_params
    :param gridable_parameters: list of gridable parameter names
    :param gridable_types: list of gridable parameter types
    :param gridable_defaults: list of gridable parameter default values
    :param max_int_number: integer, size of integer gridable parameter list
    :param max_int_val: integer, maximum integer value for integer gridable parameter
    :param min_int_val: integer, minimum integer value for integer gridable parameter
    :param max_real_number: integer, size of real gridable parameter list
    :param max_real_val: float, maximum real value for real gridable parameter
    :param min_real_val: float, minimum real value for real gridable parameter
    :param quantize_level: string representing the quantization level of floating point values generated randomly.

    :return: a tuple of hyper_params: dict of hyper parameters for gridsearch, true_gridable_parameters:
    a list of string containing names of truely gridable parameters, true_gridable_types: a list of string
    denoting parameter types and true_gridable_defaults: default values of those truly gridable parameters
    """
    count_index = 0
    true_gridable_parameters = []
    true_gridable_types = []
    true_gridable_defaults = []

    for para_name in gridable_parameters:
        # parameter must not in exclusion list
        if (para_name in model_params) and (para_name not in exclude_parameters):
            true_gridable_parameters.append(para_name)
            true_gridable_types.append(gridable_types[count_index])
            true_gridable_defaults.append(gridable_defaults[count_index])

            if para_name not in hyper_params.keys():    # add default value to user defined parameter list
                 # gridable parameter not seen before.  Randomly generate values for it
                if 'int' in gridable_types[count_index]:
                    # make sure integer values are not duplicated, using set action to remove duplicates
                    hyper_params[para_name] = list(set(np.random.random_integers(min_int_val, max_int_val,
                                                                                 max_int_number)))
                elif 'double' in gridable_types[count_index]:
                    hyper_params[para_name] = fix_float_precision(list(np.random.uniform(min_real_val, max_real_val,
                                                                     max_real_number)), quantize_level=quantize_level)

        count_index += 1

    return hyper_params, true_gridable_parameters, true_gridable_types, true_gridable_defaults


def fix_float_precision(float_list, quantize_level='1.00000000'):
    """
    This function takes in a floating point tuple and attempt to change it to floating point number with fixed
    precision.

    :param float_list: tuple/list of floating point numbers
    :param quantize_level: string, optional, represent the number of fix points we care

    :return: tuple of floats to the exact precision specified in quantize_level
    """
    fixed_float = []
    for num in float_list:
        fixed_float.append(float(Decimal(num).quantize(Decimal(quantize_level))))

    return list(set(fixed_float))


def extract_used_params(model_param_names, grid_model_params, params_dict):
    """
    This function is used to build a dict out of parameters used by our gridsearch to build a H2O model given
    the dict structure that describes the parameters and their values used by gridsearch to build that
    particular mode.

    :param model_param_names: list contains parameter names that we are interested in extracting
    :param grid_model_params: dict contains key as names of parameter and values as list of two values: default and
    actual.
    :param params_dict: dict containing extrac parameters to add to params_used like family, e.g. 'gaussian',
    'binomial', ...

    :return: params_used: a dict structure containing only parameters that take on non-default values as
    name/value pairs
    """

    params_used = {}

    for each_parameter in grid_model_params.keys():
        parameter_name = str(each_parameter)
        if parameter_name in model_param_names:
            if not (grid_model_params[each_parameter]['default'] == grid_model_params[each_parameter]['actual']):
                params_used[parameter_name] = grid_model_params[each_parameter]['actual']
    if params_dict:
        for key, value in params_dict.items():
            params_used[key] = value    # add distribution family to parameters used list

    # for GLM, change lambda to Lambda
    if 'lambda' in params_used.keys():
        params_used['Lambda'] = params_used['lambda']
        del params_used['lambda']

    return params_used


def insert_error_grid_search(hyper_params, gridable_parameters, gridable_types, error_number):
    """
    This function will randomly introduce errors into a copy of hyper_params.  Depending on the random number
    error_number generated, the following errors can be introduced:

    error_number = 0: randomly alter the name of a hyper-parameter name;
    error_number = 1: randomly choose a hyper-parameter and remove all elements in its list
    error_number = 2: add randomly generated new hyper-parameter names with random list
    error_number other: randomly choose a hyper-parameter and insert an illegal type into it

    :param hyper_params: dict containing all legal hyper-parameters for our grid search
    :param gridable_parameters: name of griddable parameters (some may not be griddable)
    :param gridable_types: type of griddable parameters
    :param error_number: integer representing which errors to introduce into the gridsearch hyper-parameters

    :return: new dict with errors in either parameter names or parameter values
    """
    error_hyper_params = copy.deepcopy(hyper_params)
#    error_hyper_params = {k : v for k, v in hyper_params.items()}

    param_index = random.randint(0, len(hyper_params)-1)
    param_name = list(hyper_params)[param_index]
    param_type = gridable_types[gridable_parameters.index(param_name)]

    if error_number == 0:   # grab a hyper-param randomly and copy its name twice
        new_name = param_name+param_name
        error_hyper_params[new_name] = error_hyper_params[param_name]
        del error_hyper_params[param_name]
    elif error_number == 1:
        error_hyper_params[param_name] = []
    elif error_number == 2:
        new_param = generate_random_words(random.randint(20,100))
        error_hyper_params[new_param] = error_hyper_params[param_name]
    else:
        error_hyper_params = insert_bad_value(error_hyper_params, param_name, param_type)

    return error_hyper_params


def insert_bad_value(error_hyper_params, param_name, param_type):
    """
    This function is written to insert a value that is of a different type into an array than the one
    its other elements are for.

    :param error_hyper_params: dict containing all hyper-parameters for a grid search
    :param param_name:  string denoting the hyper-parameter we want to insert bad element to
    :param param_type: string denoting hyper-parameter type

    :return: dict containing new inserted error value
    """
    if 'int' in param_type:     # insert a real number into integer
        error_hyper_params[param_name].append(random.uniform(-10,10))
    elif 'enum' in param_type:  # insert an float into enums
        error_hyper_params[param_name].append(random.uniform(-10,10))
    elif 'double' in param_type:  # insert an enum into float
        error_hyper_params[param_name].append(random.uniform(0,1) > 0.5)
    else:       # insert a random string for all other cases
        error_hyper_params[param_name].append(generate_random_words(random.randint(20,100)))

    return error_hyper_params


def generate_random_words(word_length):
    """
    This function will generate a random word consisting of letters, numbers and
    punctuation given the word_length.

    :param word_length: integer denoting length of the word

    :return: string representing the random word
    """

    if word_length > 0:
        all_chars = string.ascii_letters + string.digits + string.punctuation

        return ''.join((random.choice(all_chars)) for index in range(int(word_length)))
    else:
        print("word_length must be an integer greater than 0.")
        sys.exit(1)


def generate_redundant_parameters(hyper_params, gridable_parameters, gridable_defaults, error_number):
    """
    This function will randomly choose a set of hyper_params and make a dict out of it so we can
    duplicate the parameter specification in both the model and grid search.

    :param hyper_params: dict containing all griddable parameters as hyper_param to grid search
    :param gridable_parameters: list of gridable parameters (not truly)
    :param gridable_defaults: list of default values for gridable parameters
    :param error_number: int, indicate ways to change the model parameter and the hyper-parameter

    Here are the actions performed on the model parameter and hyper-parameters.
    error_number = 0: set model parameter to be  a value out of the hyper-parameter value list, should not
    generate error;
    error_number = 1: set model parameter to be default value, should not generate error in this case;
    error_number = 3: make sure model parameter is not set to default and choose a value not in the
    hyper-parameter value list.

    :return: 2 dicts containing duplicated parameters with specification, new hyperparameter specification
    """
    error_hyper_params = copy.deepcopy(hyper_params)
#    error_hyper_params = {k : v for k, v in hyper_params.items()}

    params_dict = {}
    num_params = random.randint(1, len(error_hyper_params))
    params_list = list(error_hyper_params)

    # remove default values out of hyper_params
    for key in params_list:
        default_value = gridable_defaults[gridable_parameters.index(key )]

        if default_value in error_hyper_params[key]:
            error_hyper_params[key].remove(default_value)

    for index in range(num_params):
        param_name = params_list[index]

        hyper_params_len = len(error_hyper_params[param_name])

        if error_number == 0:
            # randomly assigned the parameter to take one value out of the list
            param_value_index = random.randint(0, len(error_hyper_params[param_name])-1)
            params_dict[param_name] = error_hyper_params[param_name][param_value_index]
        elif error_number == 1:
            param_value_index = gridable_parameters.index(param_name)
            params_dict[param_name] = gridable_defaults[param_value_index]
        else:
            # randomly assign model parameter to one of the hyper-parameter values, delete that value from
            # hyper-parameter lists next, should create error condition here
            if hyper_params_len >= 2:  # only add parameter to model parameter if it is long enough
                param_value_index = random.randint(0, hyper_params_len-1)
                params_dict[param_name] = error_hyper_params[param_name][param_value_index]

                error_hyper_params[param_name].remove(params_dict[param_name])

    # final check to make sure lambda is Lambda
    if 'lambda' in list(params_dict):
        params_dict["Lambda"] = params_dict['lambda']
        del params_dict["lambda"]

    return params_dict, error_hyper_params


def count_models(hyper_params):
    """
    Given a hyper_params dict, this function will return the maximum number of models that can be built out of all
    the combination of hyper-parameters.

    :param hyper_params: dict containing parameter name and a list of values to iterate over
    :return: max_model_number: int representing maximum number of models built
    """
    max_model_number = 1

    for key in list(hyper_params):
        max_model_number *= len(hyper_params[key])

    return max_model_number


def error_diff_2_models(grid_table1, grid_table2, metric_name):
    """
    This function will take two models generated by gridsearch and calculate the mean absolute differences of
     the metric values specified by the metric_name in the two model.  It will return the mean differences.

    :param grid_table1: first H2OTwoDimTable generated by gridsearch
    :param grid_table2: second H2OTwoDimTable generated by gridsearch
    :param metric_name: string, name of the metric of interest

    :return: real number which is the mean absolute metric difference between the two models
    """
    num_model = len(grid_table1.cell_values)
    metric_diff = 0

    for model_index in range(num_model):
        metric_diff += abs(grid_table1.cell_values[model_index][-1] - grid_table2.cell_values[model_index][-1])

    if (num_model > 0):
        return metric_diff/num_model
    else:
        print("error_diff_2_models: your table contains zero models.")
        sys.exit(1)


def find_grid_runtime(model_list):
    """
    This function given a grid_model built by gridsearch will go into the model and calculate the total amount of
    time it took to actually build all the models in second

    :param model_list: list of model built by gridsearch, cartesian or randomized with cross-validation
                       enabled.
    :return: total_time_sec: total number of time in seconds in building all the models
    """
    total_time_sec = 0

    for each_model in model_list:
        total_time_sec += each_model._model_json["output"]["run_time"]  # time in ms

        # if cross validation is used, need to add those run time in here too
        if each_model._is_xvalidated:
            xv_keys = each_model._xval_keys

            for id in xv_keys:
                each_xv_model = h2o.get_model(id)
                total_time_sec += each_xv_model._model_json["output"]["run_time"]

    return total_time_sec/1000.0        # return total run time in seconds


def evaluate_metrics_stopping(model_list, metric_name, bigger_is_better, search_criteria, possible_model_number):
    """
    This function given a list of dict that contains the value of metric_name will manually go through the
    early stopping condition and see if the randomized grid search will give us the correct number of models
    generated.  Note that you cannot assume the model_list is in the order of when a model is built.  It actually
    already come sorted which we do not want....

    :param model_list: list of models built sequentially that contains metric of interest among other fields
    :param metric_name: string representing name of metric that we want to based our stopping condition on
    :param bigger_is_better: bool indicating if the metric is optimized by getting bigger if True and vice versa
    :param search_criteria: dict structure containing the search criteria for randomized gridsearch
    :param possible_model_number: integer, represent the absolute possible number of models built based on the
    hyper-parameter size

    :return: bool indicating if the early topping condition is justified
    """

    tolerance = search_criteria["stopping_tolerance"]
    stop_round = search_criteria["stopping_rounds"]

    min_list_len = 2*stop_round     # minimum length of metrics needed before we start early stopping evaluation

    metric_list = []    # store metric of optimization
    stop_now = False

    # provide metric list sorted by time.  Oldest model appear first.
    metric_list_time_ordered = sort_model_by_time(model_list, metric_name)

    for metric_value in metric_list_time_ordered:
        metric_list.append(metric_value)

        if len(metric_list) > min_list_len:     # start early stopping evaluation now
            stop_now, metric_list = evaluate_early_stopping(metric_list, stop_round, tolerance, bigger_is_better)

        if stop_now:
            if len(metric_list) < len(model_list):  # could have stopped early in randomized gridsearch
                return False
            else:       # randomized gridsearch stopped at the correct condition
                return True

    if len(metric_list) == possible_model_number:   # never meet early stopping condition at end of random gridsearch
        return True     # if max number of model built, still ok
    else:
        return False    # early stopping condition never met but random gridsearch did not build all models, bad!


def sort_model_by_time(model_list, metric_name):
    """
    This function is written to sort the metrics that we care in the order of when the model was built.  The
    oldest model metric will be the first element.

    :param model_list: list of models built sequentially that contains metric of interest among other fields
    :param metric_name: string representing name of metric that we want to based our stopping condition on
    :return: model_metric_list sorted by time
    """

    model_num = len(model_list)

    model_metric_list = [None] * model_num

    for index in range(model_num):
        model_index = int(model_list[index]._id.split('_')[-1])
        model_metric_list[model_index] = \
            model_list[index]._model_json["output"]["cross_validation_metrics"]._metric_json[metric_name]

    return model_metric_list


def evaluate_early_stopping(metric_list, stop_round, tolerance, bigger_is_better):
    """
    This function mimics the early stopping function as implemented in ScoreKeeper.java.  Please see the Java file
    comment to see the explanation of how the early stopping works.

    :param metric_list: list containing the optimization metric under consideration for gridsearch model
    :param stop_round:  integer, determine averaging length
    :param tolerance:   real, tolerance to see if the grid search model has improved enough to keep going
    :param bigger_is_better:    bool: True if metric is optimized as it gets bigger and vice versa
    :return:    bool indicating if we should stop early and sorted metric_list
    """

    if bigger_is_better:
        metric_list.sort()
    else:
        metric_list.sort(reverse=True)

    metric_len = len(metric_list)

    start_index = metric_len - 2*stop_round     # start index for reference
    all_moving_values = []

    # this part is purely used to make sure we agree with ScoreKeeper.java implementation, not efficient at all
    for index in range(stop_round+1):
        index_start = start_index+index
        all_moving_values.append(sum(metric_list[index_start:index_start+stop_round]))

    if ((min(all_moving_values) > 0) and (max(all_moving_values) > 0)) or ((min(all_moving_values) < 0)
                                                                           and (max(all_moving_values) < 0)):

        reference_value = all_moving_values[0]
        last_value = all_moving_values[-1]

        if ((reference_value > 0) and (last_value > 0)) or ((reference_value < 0) and (last_value < 0)):
            ratio = last_value / reference_value

            if bigger_is_better:
                return not (ratio > 1+tolerance), metric_list
            else:
                return not (ratio < 1-tolerance), metric_list

        else:   # zero in reference metric, or sign of metrics differ, marked as not yet converge
            return False, metric_list
    else:
        return False, metric_list


def write_hyper_parameters_json(dir1, dir2, json_filename, hyper_parameters):
    """
    This function will write a json file of the hyper_parameters in directories dir1 and dir2
    for debugging purposes.

    :param dir1: String containing first directory where you want to write the json file to
    :param dir2: String containing second directory where you want to write the json file to
    :param json_filename: String containing json file name
    :param hyper_parameters: dict containing hyper-parameters used

    :return: None.
    """

    # save hyper-parameter file in test directory
    with open(os.path.join(dir1, json_filename), 'w') as test_file:
        json.dump(hyper_parameters, test_file)

    # save hyper-parameter file in sandbox
    with open(os.path.join(dir2, json_filename), 'w') as test_file:
        json.dump(hyper_parameters, test_file)