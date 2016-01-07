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
import urllib.request, urllib.error, urllib.parse

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

def check_dims_values(python_obj, h2o_frame, rows, cols):
    """
    Check that the dimensions and values of the python object and H2OFrame are equivalent. Assumes that the python object
    conforms to the rules specified in the h2o frame documentation.

    :param python_obj: a (nested) list, tuple, dictionary, numpy.ndarray, ,or pandas.DataFrame
    :param h2o_frame: an H2OFrame
    :param rows: number of rows
    :param cols: number of columns
    :return: None
    """
    h2o_rows, h2o_cols = h2o_frame.dim
    assert h2o_rows == rows and h2o_cols == cols, "failed dim check! h2o_rows:{0} rows:{1} h2o_cols:{2} cols:{3}" \
                                                  "".format(h2o_rows, rows, h2o_cols, cols)
    if isinstance(python_obj, (list, tuple)):
        for c in range(cols):
            for r in range(rows):
                pval = python_obj[r][c] if rows > 1 else python_obj[c]
                hval = h2o_frame[r,c]
                assert pval == hval, "expected H2OFrame to have the same values as the python object for row {0} and column " \
                                     "{1}, but h2o got {2} and python got {3}.".format(r, c, hval, pval)
    elif isinstance(python_obj, dict):
        for r in range(rows):
            for k in list(python_obj.keys()):
                pval = python_obj[k][r] if hasattr(python_obj[k],'__iter__') else python_obj[k]
                hval = h2o_frame[r,k]
                assert pval == hval, "expected H2OFrame to have the same values as the python object for row {0} and column " \
                                     "{1}, but h2o got {2} and python got {3}.".format(r, k, hval, pval)

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
