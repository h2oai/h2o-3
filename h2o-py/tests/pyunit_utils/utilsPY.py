# Py2 compat
from __future__ import print_function
from future import standard_library
standard_library.install_aliases()
from past.builtins import basestring

# standard lib
import copy
import datetime
from decimal import *
from functools import reduce
import imp
import json
import math
import os
import random
import re
import shutil
import string
import subprocess
from subprocess import STDOUT,PIPE
import sys
import time # needed to randomly generate time
import threading
import urllib.request, urllib.error, urllib.parse
import uuid # call uuid.uuid4() to generate unique uuid numbers

try:
    from StringIO import StringIO  # py2 (first as py2 also has io.StringIO, but without string support, only unicode)
except:
    from io import StringIO  # py3
    

try:
    from tempfile import TemporaryDirectory
except ImportError:
    import tempfile
    
    class TemporaryDirectory:

        def __init__(self):
            self.tmp_dir = None

        def __enter__(self):
            self.tmp_dir = tempfile.mkdtemp()
            return self.tmp_dir

        def __exit__(self, *args):
            shutil.rmtree(self.tmp_dir)


# 3rd parties
import numpy as np
import pandas as pd
from scipy.sparse import csr_matrix
import scipy.special

# h2o 
sys.path.insert(1, "../../")

import h2o
from h2o.model.binomial import H2OBinomialModel
from h2o.model.clustering import H2OClusteringModel
from h2o.model.multinomial import H2OMultinomialModel
from h2o.model.ordinal import H2OOrdinalModel
from h2o.model.regression import H2ORegressionModel
from h2o.estimators import H2OGradientBoostingEstimator, H2ODeepLearningEstimator, H2OGeneralizedLinearEstimator, \
    H2OGeneralizedAdditiveEstimator, H2OKMeansEstimator, H2ONaiveBayesEstimator, H2ORandomForestEstimator, \
    H2OPrincipalComponentAnalysisEstimator
from h2o.utils.typechecks import is_type
from h2o.utils.shared_utils import temp_ctr  # unused in this file  but exposed here for symmetry with rest_ctr


class Timeout:

    def __init__(self, timeout_secs, on_timeout=None):
        enabled = timeout_secs is not None and timeout_secs >= 0
        self.timer = threading.Timer(timeout_secs, on_timeout) if enabled else None

    def __enter__(self):
        if self.timer:
            self.timer.start()
        return self

    def __exit__(self, *args):
        if self.timer:
            self.timer.cancel()


class Namespace:
    """
    simplistic namespace class allowing to create bag/namespace objects that are easily extendable in a functional way
    """
    @staticmethod
    def add(namespace, **kwargs):
        namespace.__dict__.update(kwargs)
        return namespace

    def __init__(self, **kwargs):
        self.__dict__.update(**kwargs)

    def __str__(self):
        return str(self.__dict__)

    def __repr__(self):
        return repr(self.__dict__)

    def extend(self, **kwargs):
        """
        :param kwargs: attributes extending the current namespace
        :return: a new namespace containing same attributes as the original + the extended ones
        """
        clone = Namespace(**self.__dict__)
        clone.__dict__.update(**kwargs)
        return clone


def ns(**kwargs):
    return Namespace(**kwargs)


def gen_random_uuid(numberUUID):
    uuidVec = numberUUID*[None]

    for uindex in range(numberUUID):
        uuidVec[uindex] = uuid.uuid4()
    return uuidVec

def gen_random_time(numberTimes, maxtime= datetime.datetime(2080, 8,6,8,14,59), mintime=datetime.datetime(1980, 8,6,6,14,59)):
    '''
    Simple method that I shameless copied from the internet.
    :param numberTimes:
    :param maxtime:
    :param mintime:
    :return:
    '''
    mintime_ts = int(time.mktime(mintime.timetuple()))
    maxtime_ts = int(time.mktime(maxtime.timetuple()))
    randomTimes = numberTimes*[None]
    for tindex in range(numberTimes):
        temptime = random.randint(mintime_ts, maxtime_ts)
        randomTimes[tindex] = datetime.datetime.fromtimestamp(temptimes)
    return randomTimes


def check_models(model1, model2, use_cross_validation=False, op='e'):
    """
    Check that the given models are equivalent.

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
    elif isinstance(model1,H2OMultinomialModel) or isinstance(model1,H2OOrdinalModel): #   2c. Multinomial
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
    Check that the dimensions and values of the python object and H2OFrame are equivalent. Assumes that the python
    object conforms to the rules specified in the h2o frame documentation.

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
                    pval = python_obj[r]
                    if isinstance(pval, (list, tuple)): pval = pval[c]
                    hval = h2o_frame[r, c]
                    assert pval == hval or abs(pval - hval) < 1e-10, \
                        "expected H2OFrame to have the same values as the python object for row {0} " \
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

 # perform h2o predict and mojo predict.  Frames containing h2o prediction is returned and mojo predict are
# returned.

def mojo_predict(model, tmpdir, mojoname, glrmReconstruct=False, get_leaf_node_assignment=False, glrmIterNumber=-1, zipFilePath=None):
    
    """
    perform h2o predict and mojo predict.  Frames containing h2o prediction is returned and mojo predict are returned.
    It is assumed that the input data set is saved as in.csv in tmpdir directory.

    :param model: h2o model where you want to use to perform prediction
    :param tmpdir: directory where your mojo zip files are stired
    :param mojoname: name of your mojo zip file.
    :param glrmReconstruct: True to return reconstructed dataset, else return the x factor.
    :return: the h2o prediction frame and the mojo prediction frame
    """
    newTest = h2o.import_file(os.path.join(tmpdir, 'in.csv'), header=1)   # Make sure h2o and mojo use same in.csv
    predict_h2o = model.predict(newTest)

    # load mojo and have it do predict
    outFileName = os.path.join(tmpdir, 'out_mojo.csv')
    mojoZip = os.path.join(tmpdir, mojoname) + ".zip"
    if not(zipFilePath==None):
        mojoZip = zipFilePath
    genJarDir = str.split(os.path.realpath("__file__"),'/')
    genJarDir = '/'.join(genJarDir[0:genJarDir.index('h2o-py')])    # locate directory of genmodel.jar

    java_cmd = ["java", "-ea", "-cp", os.path.join(genJarDir, "h2o-assemblies/genmodel/build/libs/genmodel.jar"),
                "-Xmx12g", "-XX:MaxPermSize=2g", "-XX:ReservedCodeCacheSize=256m", "hex.genmodel.tools.PredictCsv",
                "--input", os.path.join(tmpdir, 'in.csv'), "--output",
                outFileName, "--mojo", mojoZip, "--decimal"]
    if get_leaf_node_assignment:
        java_cmd.append("--leafNodeAssignment")
        predict_h2o = model.predict_leaf_node_assignment(newTest)

    if glrmReconstruct:  # used for GLRM to grab the x coefficients (factors) instead of the predicted values
        java_cmd.append("--glrmReconstruct")
    
    if glrmIterNumber > 0:
        java_cmd.append("--glrmIterNumber")
        java_cmd.append(str(glrmIterNumber))

    p = subprocess.Popen(java_cmd, stdout=PIPE, stderr=STDOUT)
    o, e = p.communicate()
    files = os.listdir(tmpdir)
    print("listing files {1} in directory {0}".format(tmpdir, files))
    outfile = os.path.join(tmpdir, 'out_mojo.csv')
    if not os.path.exists(outfile) or os.stat(outfile).st_size == 0:
        print("MOJO SCORING FAILED:")
        print("--------------------")
        print(o.decode("utf-8"))
    print("***** importing file {0}".format(outfile))        
    pred_mojo = h2o.import_file(outfile, header=1)  # load mojo prediction in 
    # to a frame and compare
    if glrmReconstruct or ('glrm' not in model.algo):
        return predict_h2o, pred_mojo
    else:
        return newTest.frame_id, pred_mojo

# perform pojo predict.  Frame containing pojo predict is returned.
def pojo_predict(model, tmpdir, pojoname):
    h2o.download_pojo(model, path=tmpdir)
    h2o_genmodel_jar = os.path.join(tmpdir, "h2o-genmodel.jar")
    java_file = os.path.join(tmpdir, pojoname + ".java")

    in_csv = (os.path.join(tmpdir, 'in.csv'))   # import the test dataset
    print("Compiling Java Pojo")
    javac_cmd = ["javac", "-cp", h2o_genmodel_jar, "-J-Xmx12g", java_file]
    subprocess.check_call(javac_cmd)

    out_pojo_csv = os.path.join(tmpdir, "out_pojo.csv")
    cp_sep = ";" if sys.platform == "win32" else ":"
    java_cmd = ["java", "-ea", "-cp", h2o_genmodel_jar + cp_sep + tmpdir, "-Xmx12g",
            "-XX:ReservedCodeCacheSize=256m", "hex.genmodel.tools.PredictCsv",
            "--pojo", pojoname, "--input", in_csv, "--output", out_pojo_csv, "--decimal"]

    p = subprocess.Popen(java_cmd, stdout=PIPE, stderr=STDOUT)
    o, e = p.communicate()
    print("Java output: {0}".format(o))
    assert os.path.exists(out_pojo_csv), "Expected file {0} to exist, but it does not.".format(out_pojo_csv)
    predict_pojo = h2o.import_file(out_pojo_csv, header=1)
    return predict_pojo

def javapredict(algo, equality, train, test, x, y, compile_only=False, separator=",", setInvNumNA=False,**kwargs):
    print("Creating model in H2O")
    if algo == "gbm": model = H2OGradientBoostingEstimator(**kwargs)
    elif algo == "random_forest": model = H2ORandomForestEstimator(**kwargs)
    elif algo == "deeplearning": model = H2ODeepLearningEstimator(**kwargs)
    elif algo == "glm": model = H2OGeneralizedLinearEstimator(**kwargs)
    elif algo == "gam": model = H2OGeneralizedAdditiveEstimator(**kwargs)
    elif algo == "naive_bayes": model = H2ONaiveBayesEstimator(**kwargs)
    elif algo == "kmeans": model = H2OKMeansEstimator(**kwargs)
    elif algo == "pca": model = H2OPrincipalComponentAnalysisEstimator(**kwargs)
    else: raise ValueError
    if algo == "kmeans" or algo == "pca": model.train(x=x, training_frame=train)
    else: model.train(x=x, y=y, training_frame=train)
    print(model)

    # HACK: munge model._id so that it conforms to Java class name. For example, change K-means to K_means.
    # TODO: clients should extract Java class name from header.
    regex = re.compile("[+\\-* !@#$%^&()={}\\[\\]|;:'\"<>,.?/]")
    pojoname = regex.sub("_", model._id)

    print("Downloading Java prediction model code from H2O")
    tmpdir = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath(__file__)), "..", "results", pojoname))
    os.makedirs(tmpdir)
    h2o.download_pojo(model, path=tmpdir)
    h2o_genmodel_jar = os.path.join(tmpdir, "h2o-genmodel.jar")
    assert os.path.exists(h2o_genmodel_jar), "Expected file {0} to exist, but it does not.".format(h2o_genmodel_jar)
    print("h2o-genmodel.jar saved in {0}".format(h2o_genmodel_jar))
    java_file = os.path.join(tmpdir, pojoname + ".java")
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
        out_h2o_csv = os.path.join(tmpdir, "out_h2o.csv")
        h2o.download_csv(predictions, out_h2o_csv)
        assert os.path.exists(out_h2o_csv), "Expected file {0} to exist, but it does not.".format(out_h2o_csv)
        print("H2O Predictions saved in {0}".format(out_h2o_csv))

        print("Setting up for Java POJO")
        in_csv = os.path.join(tmpdir, "in.csv")
        h2o.download_csv(test[x], in_csv)

        # hack: the PredictCsv driver can't handle quoted strings, so remove them
        f = open(in_csv, "r+")
        csv = f.read()
        csv = re.sub('\"', "", csv)
        csv = re.sub(",", separator, csv)       # replace with arbitrary separator for input dataset
        f.seek(0)
        f.write(csv)
        f.truncate()
        f.close()
        assert os.path.exists(in_csv), "Expected file {0} to exist, but it does not.".format(in_csv)
        print("Input CSV to PredictCsv saved in {0}".format(in_csv))

        print("Running PredictCsv Java Program")
        out_pojo_csv = os.path.join(tmpdir, "out_pojo.csv")
        cp_sep = ";" if sys.platform == "win32" else ":"
        java_cmd = ["java", "-ea", "-cp", h2o_genmodel_jar + cp_sep + tmpdir, "-Xmx12g", "-XX:MaxPermSize=2g",
                    "-XX:ReservedCodeCacheSize=256m", "hex.genmodel.tools.PredictCsv", "--decimal",
                    "--pojo", pojoname, "--input", in_csv, "--output", out_pojo_csv, "--separator", separator]
        if setInvNumNA:
            java_cmd.append("--setConvertInvalidNum")
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
        if not(equality == "class"or equality == "numeric"):
            raise ValueError
        compare_frames_local(predictions, predictions2, prob=1, tol=1e-4) # faster frame compare


def javamunge(assembly, pojoname, test, compile_only=False):
    """
    Here's how to use:
      assembly is an already fit H2OAssembly;
      The test set should be used to compare the output here and the output of the POJO.
    """
    print("Downloading munging POJO code from H2O")
    tmpdir = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath(__file__)), "..", "results", pojoname))
    os.makedirs(tmpdir)
    assembly.to_pojo(pojoname, path=tmpdir, get_jar=True)
    h2o_genmodel_jar = os.path.join(tmpdir, "h2o-genmodel.jar")
    assert os.path.exists(h2o_genmodel_jar), "Expected file {0} to exist, but it does not.".format(h2o_genmodel_jar)
    print("h2o-genmodel.jar saved in {0}".format(h2o_genmodel_jar))
    java_file = os.path.join(tmpdir, pojoname + ".java")
    assert os.path.exists(java_file), "Expected file {0} to exist, but it does not.".format(java_file)
    print("java code saved in {0}".format(java_file))

    print("Compiling Java Pojo")
    javac_cmd = ["javac", "-cp", h2o_genmodel_jar, "-J-Xmx12g", "-J-XX:MaxPermSize=256m", java_file]
    subprocess.check_call(javac_cmd)

    if not compile_only:

        print("Setting up for Java POJO")
        in_csv = os.path.join(tmpdir, "in.csv")
        h2o.download_csv(test, in_csv)
        assert os.path.exists(in_csv), "Expected file {0} to exist, but it does not.".format(in_csv)
        print("Input CSV to mungedCSV saved in {0}".format(in_csv))

        print("Predicting in H2O")
        munged = assembly.fit(test)
        munged.head()
        out_h2o_csv = os.path.join(tmpdir, "out_h2o.csv")
        h2o.download_csv(munged, out_h2o_csv)
        assert os.path.exists(out_h2o_csv), "Expected file {0} to exist, but it does not.".format(out_h2o_csv)
        print("Munged frame saved in {0}".format(out_h2o_csv))

        print("Running PredictCsv Java Program")
        out_pojo_csv = os.path.join(tmpdir, "out_pojo.csv")
        cp_sep = ";" if sys.platform == "win32" else ":"
        java_cmd = ["java", "-ea", "-cp", h2o_genmodel_jar + cp_sep + tmpdir, "-Xmx12g", "-XX:MaxPermSize=2g",
                    "-XX:ReservedCodeCacheSize=256m", "hex.genmodel.tools.MungeCsv", "--header", "--munger", pojoname,
                    "--input", in_csv, "--output", out_pojo_csv]
        print("JAVA COMMAND: " + " ".join(java_cmd))
        p = subprocess.Popen(java_cmd, stdout=PIPE, stderr=STDOUT)
        o, e = p.communicate()
        print("Java output: {0}".format(o))
        assert os.path.exists(out_pojo_csv), "Expected file {0} to exist, but it does not.".format(out_pojo_csv)
        munged2 = h2o.upload_file(path=out_pojo_csv, col_types=test.types)
        print("Pojo predictions saved in {0}".format(out_pojo_csv))

        print("Comparing predictions between H2O and Java POJO")
        # Dimensions
        hr, hc = munged.dim
        pr, pc = munged2.dim
        assert hr == pr, "Expected the same number of rows, but got {0} and {1}".format(hr, pr)
        assert hc == pc, "Expected the same number of cols, but got {0} and {1}".format(hc, pc)

        # Value
        import math
        import numbers
        munged.show()
        munged2.show()
        for r in range(hr):
          for c in range(hc):
              hp = munged[r,c]
              pp = munged2[r,c]
              if isinstance(hp, numbers.Number):
                assert isinstance(pp, numbers.Number)
                assert (math.fabs(hp-pp) < 1e-8) or (math.isnan(hp) and math.isnan(pp)), "Expected munged rows to be the same for row {0}, but got {1}, and {2}".format(r, hp, pp)
              else:
                assert hp==pp, "Expected munged rows to be the same for row {0}, but got {1}, and {2}".format(r, hp, pp)

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
        # be an immediate subdirectory of /home/0xdiag/. Moreover, the only guaranteed subdirectories of /home/0xdiag/
        # are smalldata and bigdata.
        p = os.path.realpath(os.path.join("/home/0xdiag/", path))
        if not os.path.exists(p): raise ValueError("File not found: " + path)
        return p
    else:
        tmp_dir = os.path.realpath(os.getcwd())
        possible_result = os.path.join(tmp_dir, path)
        try:
            while (True):
                if (os.path.exists(possible_result)):
                    return possible_result

                next_tmp_dir = os.path.dirname(tmp_dir)
                if (next_tmp_dir == tmp_dir):
                    raise ValueError("File not found: " + path)

                tmp_dir = next_tmp_dir
                possible_result = os.path.join(tmp_dir, path)
        except ValueError as e:
            url = "https://h2o-public-test-data.s3.amazonaws.com/{}".format(path)
            if url_exists(url):
                return url
            raise

def url_exists(url):
    head_req = urllib.request.Request(url, method='HEAD')
    try:
        with urllib.request.urlopen(head_req) as test:
            return test.status == 200
    except urllib.error.URLError:
        return False          

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
    with open(test_name, "r") as t: 
        pyunit = t.read()
    test_path = os.path.abspath(test_name)
    pyunit_c = compile(pyunit, test_path, 'exec')
    exec(pyunit_c, dict(__name__='__main__', __file__=test_path))  # forcing module name to ensure that the test behaves the same way as when executed using `python my_test.py`

def standalone_test(test):
    if not h2o.connection() or not h2o.connection().connected:
        print("Creating connection for test %s" % test.__name__)
        h2o.init(strict_version_check=False)
        print("New session: %s" % h2o.connection().session_id)

    h2o.remove_all()

    h2o.log_and_echo("------------------------------------------------------------")
    h2o.log_and_echo("")
    h2o.log_and_echo("STARTING TEST "+test.__name__)
    h2o.log_and_echo("")
    h2o.log_and_echo("------------------------------------------------------------")
    test()

def run_tests(tests, run_in_isolation=True):
    #flatten in case of nested tests/test suites
    all_tests = reduce(lambda l, r: (l.extend(r) if isinstance(r, (list, tuple)) else l.append(r)) or l, tests, [])
    for test in all_tests:
        if not(hasattr(test, 'tag') and ('H2OANOVAGLM' in test.tag)): # exclude AnovaGLM because it does not have score function
            header = "Running {}{}".format(test.__name__, "" if not hasattr(test, 'tag') else " [{}]".format(test.tag))
            print("\n"+('='*len(header))+"\n"+header)
            if run_in_isolation:
                standalone_test(test)
            else:
                test()
            
def tag_test(test, tag):
    if tag is not None:
        test.tag = tag
    return test

def assert_warn(predicate, message):
    try:
        assert predicate, message
    except AssertionError as e:
        print("WARN: {}".format(str(e)))

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
            if random.randint(0,1): grid_space['learn_rate'] = [random.random() for _ in range(random.randint(2,3))]
            grid_space['distribution'] = random.sample(['bernoulli', 'multinomial', 'gaussian', 'poisson', 'tweedie', 'gamma'], 1)
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


def rest_ctr():
    return h2o.connection().requests_count


def write_syn_floating_point_dataset_glm(csv_training_data_filename, csv_validation_data_filename,
                                         csv_test_data_filename, csv_weight_name, row_count, col_count, data_type,
                                         max_p_value, min_p_value, max_w_value, min_w_value, noise_std, family_type,
                                         valid_row_count, test_row_count, class_number=2,
                                         class_method=('probability', 'probability', 'probability'),
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
                                  class_method=class_method[0], class_margin=class_margin[0], weightChange=True)

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
    new_col_count = col_count - enum_col + sum(enum_level_vec)+len(enum_level_vec)

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
                                        class_method=class_method[0], class_margin=class_margin[0], weightChange=True)

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
            assert False, "dataType must be 1 or 2 for now."
    elif ('binomial' in family_type.lower()) or ('multinomial' in family_type.lower()
                                                 or ('ordinal' in family_type.lower())):
        if 'binomial' in family_type.lower():  # for binomial, only need 1 set of weight
            class_number -= 1

        if class_number <= 0:
            assert False, "class_number must be >= 2!"

        if isinstance(col_count, np.ndarray):
            temp_col_count = col_count[0]
        else:
            temp_col_count = col_count

        if data_type == 1:     # generate random integer intercept/weight
            weight = np.random.random_integers(min_w_value, max_w_value, [temp_col_count+1, class_number])
        elif data_type == 2:   # generate real intercept/weights
            weight = np.random.uniform(min_w_value, max_w_value, [temp_col_count+1, class_number])
        else:
            assert False, "dataType must be 1 or 2 for now."

    # special treatment for ordinal weights
    if 'ordinal' in family_type.lower():
        num_pred = len(weight)
        for index in range(class_number):
            weight[0,index] = 0
            for indP in range(1,num_pred):
                weight[indP,index] = weight[indP,0] # make sure betas for all classes are the same

    np.savetxt(csv_weight_filename, weight.transpose(), delimiter=",")
    return weight


def generate_training_set_glm(csv_filename, row_count, col_count, min_p_value, max_p_value, data_type, family_type,
                              noise_std, weight, class_method='probability', class_margin=0.0, weightChange=False):
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
        assert False, "dataType must be 1 or 2 for now. "

    # generate the response vector to the input predictors
    response_y = generate_response_glm(weight, x_mat, noise_std, family_type,
                                       class_method=class_method, class_margin=class_margin, weightChange=weightChange)

    # for family_type = 'multinomial' or 'binomial', response_y can be -ve to indicate bad sample data.
    # need to delete this data sample before proceeding
    # if ('multinomial' in family_type.lower()) or ('binomial' in family_type.lower()) or ('ordinal' in family_type.lower()):
    #     if 'threshold' in class_method.lower():
    #         if np.any(response_y < 0):  # remove negative entries out of data set
    #             (x_mat, response_y) = remove_negative_response(x_mat, response_y)

    # write to file in csv format
    np.savetxt(csv_filename, np.concatenate((x_mat, response_y), axis=1), delimiter=",")


def generate_clusters(cluster_center_list, cluster_pt_number_list, cluster_radius_list):
    """
    This function is used to generate clusters of points around cluster_centers listed in
    cluster_center_list.  The radius of the cluster of points are specified by cluster_pt_number_list.
    The size of each cluster could be different and it is specified in cluster_radius_list.

    :param cluster_center_list: list of coordinates of cluster centers
    :param cluster_pt_number_list: number of points to generate for each cluster center
    :param cluster_radius_list: list of size of each cluster
    :return: list of sample points that belong to various clusters
    """

    k = len(cluster_pt_number_list)     # number of clusters to generate clusters for

    if (not(k == len(cluster_center_list))) or (not(k == len(cluster_radius_list))):
        assert False, "Length of list cluster_center_list, cluster_pt_number_list, cluster_radius_list must be the same!"

    training_sets = []
    for k_ind in range(k):
        new_cluster_data = generate_one_cluster(cluster_center_list[k_ind], cluster_pt_number_list[k_ind],
                                                cluster_radius_list[k_ind])
        if k_ind > 0:
            training_sets = np.concatenate((training_sets, new_cluster_data), axis=0)
        else:
            training_sets = new_cluster_data

    # want to shuffle the data samples so that the clusters are all mixed up
    map(np.random.shuffle, training_sets)

    return training_sets


def generate_one_cluster(cluster_center, cluster_number, cluster_size):
    """
    This function will generate a full cluster wither cluster_number points centered on cluster_center
    with maximum radius cluster_size

    :param cluster_center: python list denoting coordinates of cluster center
    :param cluster_number: integer denoting number of points to generate for this cluster
    :param cluster_size: float denoting radius of cluster
    :return: np matrix denoting a cluster
    """

    pt_dists = np.random.uniform(0, cluster_size, [cluster_number, 1])
    coord_pts = len(cluster_center)     # dimension of each cluster point
    one_cluster_data = np.zeros((cluster_number, coord_pts), dtype=np.float)

    for p_ind in range(cluster_number):
        coord_indices = list(range(coord_pts))
        random.shuffle(coord_indices)  # randomly determine which coordinate to generate
        left_radius = pt_dists[p_ind]

        for c_ind in range(coord_pts):
            coord_index = coord_indices[c_ind]
            one_cluster_data[p_ind, coord_index] = random.uniform(-1*left_radius+cluster_center[coord_index],
                                                                  left_radius+cluster_center[coord_index])
            left_radius = math.sqrt(pow(left_radius, 2)-pow((one_cluster_data[p_ind, coord_index]-
                                                             cluster_center[coord_index]), 2))

    return one_cluster_data


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
                                    class_number=2, class_method='probability', class_margin=0.0, weightChange=False):
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
                                    family_type, class_method=class_method, class_margin=class_margin, weightChange=weightChange)

    if len(csv_filename) > 0:
        generate_and_save_mixed_glm(csv_filename, x_mat, enum_level_vec, enum_col, False, weight, noise_std,
                                    family_type, class_method=class_method, class_margin=class_margin, weightChange=False)


def generate_and_save_mixed_glm(csv_filename, x_mat, enum_level_vec, enum_col, true_one_hot, weight, noise_std,
                                family_type, class_method='probability', class_margin=0.0, weightChange=False):
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
                                       class_method=class_method, class_margin=class_margin, weightChange=weightChange)

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

        new_temp_enum = np.zeros((num_row, enum_col_num))
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
        assert False, "enum_level must be >= 2."


def generate_response_glm(weight, x_mat, noise_std, family_type, class_method='probability',
                          class_margin=0.0, weightChange=False, even_distribution=True):
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
    temp_ones_col = np.asmatrix(np.ones(num_row)).transpose()
    x_mat = np.concatenate((temp_ones_col, x_mat), axis=1)
    response_y = x_mat * weight + noise_std * np.random.standard_normal([num_row, 1])

    if 'ordinal' in family_type.lower():
        (num_sample, num_class) = response_y.shape
        lastClass = num_class - 1
        if weightChange:
            tresp = []
            # generate the new y threshold
            for indP in range(num_sample):
                tresp.append(-response_y[indP,0])
            tresp.sort()
            num_per_class = int(len(tresp)/num_class)

            if (even_distribution):
                for indC in range(lastClass):
                    weight[0,indC] = tresp[(indC+1)*num_per_class]

            else: # do not generate evenly distributed class, generate randomly distributed classes
                splitInd = []
                lowV = 0.1
                highV = 1
                v1 = 0
                acc = 0
                for indC in range(lastClass):
                    tempf = random.uniform(lowV, highV)
                    splitInd.append(v1+int(tempf*num_per_class))
                    v1 = splitInd[indC] # from last class
                    acc += 1-tempf
                    highV = 1+acc

                for indC in range(lastClass):   # put in threshold
                    weight[0,indC] = tresp[splitInd[indC]]

            response_y = x_mat * weight + noise_std * np.random.standard_normal([num_row, 1])

        discrete_y = np.zeros((num_sample, 1), dtype=np.int)
        for indR in range(num_sample):
            discrete_y[indR, 0] = lastClass
            for indC in range(lastClass):
                if (response_y[indR, indC] >= 0):
                    discrete_y[indR, 0] = indC
                    break
        return discrete_y

    # added more to form Multinomial response
    if ('multinomial' in family_type.lower()) or ('binomial' in family_type.lower()):
        temp_mat = np.exp(response_y)   # matrix of n by K where K = 1 for binomials
        if 'binomial' in family_type.lower():
            ntemp_mat = temp_mat + 1
            btemp_mat = temp_mat / ntemp_mat
            temp_mat = np.concatenate((1-btemp_mat, btemp_mat), axis=1)    # inflate temp_mat to 2 classes

        response_y = derive_discrete_response(temp_mat, class_method, class_margin, family_type)

    return response_y


def derive_discrete_response(prob_mat, class_method, class_margin, family_type='binomial'):
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
    discrete_y =  np.argmax(prob_mat, axis=1)

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
            assert False, "Illegal action setting.  It can only be 'move' or 'copy'!"

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
    # pd_frame = pd.read_csv(old_filename, header=None)    # read in a dataset
    np_frame = np.asmatrix(np.genfromtxt(old_filename, delimiter=',', dtype=None))
    (row_count, col_count) = np_frame.shape
    random_matrix = np.random.uniform(0, 1, [row_count, col_count-1])

    for indr in range(row_count):    # for each predictor value, determine if to replace value with nan
        for indc in range(col_count-1):
            if random_matrix[indr, indc] < missing_fraction:
                np_frame[indr, indc] = np.nan

    # save new data set with missing values to new file
    np.savetxt(new_filename, np_frame, delimiter=",")
    # pd_frame.to_csv(new_filename, sep=',', header=False, index=False, na_rep='nan')


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

def assert_H2OTwoDimTable_equal_upto(table1, table2, col_header_list, tolerance=1e-6):
    '''
    This method will compare two H2OTwoDimTables that are almost of the same size.  table1 can be shorter
    than table2.  However, for whatever part of table2 table1 has, they must be the same.
    :param table1:
    :param table2:
    :param col_header_list:
    :param tolerance:
    :return:
    '''
    size1 = len(table1.cell_values)

    for cname in col_header_list:
        colindex = table1.col_header.index(cname)

        for cellind in range(size1):
            val1 = table1.cell_values[cellind][colindex]
            val2 = table2.cell_values[cellind][colindex]

            if isinstance(val1, float) and isinstance(val2, float):
                assert abs(val1-val2) < tolerance, \
                    "table 1 value {0} and table 2 value {1} in {2} differ more than tolerance of " \
                    "{3}".format(val1, val2, cname, tolerance)
            else:
                assert val1==val2, "table 1 value {0} and table 2 value {1} in {2} differ more than tolerance of " \
                                   "{3}".format(val1, val2, cname, tolerance)
    print("******* Congrats!  Test passed. ")



def extract_col_value_H2OTwoDimTable(table, col_name):
    '''
    This function given the column name will extract a list containing the value used for the column name from the
    H2OTwoDimTable.

    :param table:
    :param col_name:
    :return:
    '''

    tableList = []
    col_header = table.col_header
    colIndex = col_header.index(col_name)
    for ind in range(len(table.cell_values)):
        temp = table.cell_values[ind]
        tableList.append(temp[colIndex])

    return tableList


def assert_H2OTwoDimTable_equal_upto(table1, table2, col_header_list, tolerance=1e-6):
    '''
    This method will compare two H2OTwoDimTables that are almost of the same size.  table1 can be shorter
    than table2.  However, for whatever part of table2 table1 has, they must be the same.

    :param table1:
    :param table2:
    :param col_header_list:
    :param tolerance:
    :return:
    '''
    size1 = len(table1.cell_values)

    for cname in col_header_list:
        colindex = table1.col_header.index(cname)

        for cellind in range(size1):
            val1 = table1.cell_values[cellind][colindex]
            val2 = table2.cell_values[cellind][colindex]

            if isinstance(val1, float) and isinstance(val2, float) and not(math.isnan(val1) and math.isnan(val2)):
                    assert abs(val1-val2) < tolerance, \
                    "table 1 value {0} and table 2 value {1} in {2} differ more than tolerance of " \
                    "{3}".format(val1, val2, cname, tolerance)
            elif not(isinstance(val1, float) and isinstance(val2, float)) :
                    assert val1==val2, "table 1 value {0} and table 2 value {1} in {2} differ more than tolerance of " \
                                   "{3}".format(val1, val2, cname, tolerance)
    print("******* Congrats!  Test passed. ")

def assert_equal_scoring_history(model1, model2, col_compare_list, tolerance=1e-6):
    scoring_hist1 = model1._model_json["output"]["scoring_history"]
    scoring_hist2 = model2._model_json["output"]["scoring_history"]
    assert_H2OTwoDimTable_equal_upto(scoring_hist1, scoring_hist2, col_compare_list, tolerance=tolerance)

def assert_H2OTwoDimTable_equal(table1, table2, col_header_list, tolerance=1e-6, check_sign=False, check_all=True,
                                num_per_dim=10):
    """
    This method compares two H2OTwoDimTables and verify that their difference is less than value set in tolerance. It
    is probably an overkill for I have assumed that the order of col_header_list may not be in the same order as
    the values in the table.cell_values[ind][0].  In addition, I do not assume an order for the names in the
    table.cell_values[ind][0] either for there is no reason for an order to exist.

    To limit the test run time, we can test a randomly sampled of points instead of all points

    :param table1: H2OTwoDimTable to be compared
    :param table2: the other H2OTwoDimTable to be compared
    :param col_header_list: list of strings denote names that we want the comparison to be performed
    :param tolerance: default to 1e-6
    :param check_sign: bool, determine if the sign of values are important or not.  For eigenvectors, they are not.
    :param check_all: bool, determine if we need to compare every single element
    :param num_per_dim: integer, number of elements to sample per dimension.  We have 3 here.
    :return: None if comparison succeed and raise an error if comparison failed for whatever reason
    """
    num_comparison = len(set(col_header_list))
    size1 = len(table1.cell_values)
    size2 = len(table2.cell_values)
    worst_error = 0

    assert size1==size2, "The two H2OTwoDimTables are of different size!"
    assert num_comparison<=size1, "H2OTwoDimTable do not have all the attributes specified in col_header_list."
    flip_sign_vec = generate_sign_vec(table1, table2) if check_sign else [1]*len(table1.cell_values[0])  # correct for sign change for eigenvector comparisons
    randRange1 = generate_for_indices(len(table1.cell_values), check_all, num_per_dim, 0)
    randRange2 = generate_for_indices(len(table2.cell_values), check_all, num_per_dim, 0)


    for ind in range(num_comparison):
        col_name = col_header_list[ind]
        next_name=False

        for name_ind1 in randRange1:
            if col_name!=str(table1.cell_values[name_ind1][0]):
                continue

            for name_ind2 in randRange2:
                if not(col_name==str(table2.cell_values[name_ind2][0])):
                    continue

                # now we have the col header names, do the actual comparison
                if str(table1.cell_values[name_ind1][0])==str(table2.cell_values[name_ind2][0]):
                    randRange3 = generate_for_indices(min(len(table2.cell_values[name_ind2]), len(table1.cell_values[name_ind1])), check_all, num_per_dim,1)
                    for indC in randRange3:
                        val1 = table1.cell_values[name_ind1][indC]
                        val2 = table2.cell_values[name_ind2][indC]*flip_sign_vec[indC]

                        if isinstance(val1, float) and isinstance(val2, float):
                            compare_val_ratio = abs(val1-val2)/max(1, abs(val1), abs(val2))
                            if compare_val_ratio > tolerance:
                                print("Table entry difference is {0} at dimension {1} and eigenvector number "
                                      "{2}".format(compare_val_ratio, name_ind1, indC))
                                print("The first vector is {0} and the second vector is {1}".format(table1.cell_values[name_ind1], table2.cell_values[name_ind2]))
                                assert False, "Table entries are not equal within tolerance."

                            worst_error = max(worst_error, compare_val_ratio)
                        else:
                            assert False, "Tables contains non-numerical values.  Comparison is for numericals only!"
                    next_name=True
                    break
                else:
                    assert False, "Unknown metric names found in col_header_list."
            if next_name:   # ready to go to the next name in col_header_list
                break
    print("******* Congrats!  Test passed.  Maximum difference of your comparison is {0}".format(worst_error))

def generate_for_indices(list_size, check_all, num_per_dim, start_val):
    if check_all:
        return list(range(start_val, list_size))
    else:
        randomList = list(range(start_val, list_size))
        random.shuffle(randomList)
        return randomList[0:min(list_size, num_per_dim)]

def generate_sign_vec(table1, table2):
    sign_vec = [1]*len(table1.cell_values[0])
    for indC in range(1, len(table2.cell_values[0])):   # may need to look at other elements since some may be zero
        for indR in range(0, len(table2.cell_values)):
            if (abs(table1.cell_values[indR][indC]) > 0) and (abs(table2.cell_values[indR][indC]) > 0):
                sign_vec[indC] = int(np.sign(table1.cell_values[indR][indC]) * np.sign(table2.cell_values[indR][indC]))
                # if (np.sign(table1.cell_values[indR][indC])!=np.sign(table2.cell_values[indR][indC])):
                #     sign_vec[indC] = -1
                # else:
                #     sign_vec[indC] = 1
                break       # found what we need.  Goto next column

    return sign_vec
def equal_two_dicts(dict1, dict2, tolerance=1e-6, throwError=True):
    size1 = len(dict1)
    if (size1 == len(dict2)):   # only proceed if lengths are the same
        for key1 in dict1.keys():
            diff = abs(dict1[key1]-dict2[key1])
            if (diff > tolerance):
                if throwError:
                    assert False, "Dict 1 value {0} and Dict 2 value {1} do not agree.".format(dict1[key1], dict2[key1])
                else:
                    return False
                
                
def equal_two_arrays(array1, array2, eps=1e-6, tolerance=1e-6, throw_error=True):
    """
    This function will compare the values of two python tuples.  First, if the values are below
    eps which denotes the significance level that we care, no comparison is performed.  Next,
    False is returned if the different between any elements of the two array exceeds some tolerance.

    :param array1: numpy array containing some values of interest
    :param array2: numpy array containing some values of interest that we would like to compare it with array1
    :param eps: significance level that we care about in order to perform the comparison
    :param tolerance: threshold for which we allow the two array elements to be different by
    :param throw_error: throws error when two arrays are not equal

    :return: True if elements in array1 and array2 are close and False otherwise
    """

    size1 = len(array1)
    if size1 == len(array2):    # arrays must be the same size
        # compare two arrays
        for ind in range(size1):
            if not ((array1[ind] < eps) and (array2[ind] < eps)):
                # values to be compared are not too small, perform comparison

                # look at differences between elements of array1 and array2
                compare_val_h2o_py = abs(array1[ind] - array2[ind])

                if compare_val_h2o_py > tolerance:    # difference is too high, return false
                    if throw_error:
                        assert False, "Array 1 value {0} and array 2 value {1} do not agree.".format(array1[ind], array2[ind])
                    else:
                        return False

        return True                                     # return True, elements of two arrays are close enough
    else:
        if throw_error:
            assert False, "The two arrays are of different size!"
        else:
            return False
        

def equal_2d_tables(table1, table2, tolerance=1e-6):
    """
    This function will compare the values of two python tuples. 
    False is returned if the different between any elements of the two array exceeds some tolerance.

    :param table1: numpy array containing some values of interest
    :param table2: numpy array containing some values of interest that we would like to compare it with array1
    :param tolerance: threshold for which we allow the two array elements to be different by

    :return: True if elements in array1 and array2 are close and False otherwise
    """

    size1 = len(table1)
    if size1 == len(table2):    # arrays must be the same size
        # compare two arrays
        for ind in range(size1):
            if len(table1[ind]) == len(table2[ind]):
                for ind2 in range(len(table1[ind])):
                    if type(table1[ind][ind2]) == float:
                        if abs(table1[ind][ind2]-table2[ind][ind2]) > tolerance:
                            return False
            else:
                assert False, "The two arrays are of different size!"
        return True

    else:
        assert False, "The two arrays are of different size!"


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
        os.makedirs(syndatasets_dir)

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
            assert False, "P-values are only available to Gaussian family."

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
        return float(str(lambda_str[-2]).split(',')[0])
    elif what_param == 'confusion_matrix':
        if 'multinomial' in family_type.lower():
            return model._model_json["output"]["training_metrics"]._metric_json["cm"]["table"]
        elif 'binomial' in family_type.lower():
            return model.confusion_matrix().table
    else:
        assert False, "parameter value not found in GLM model"


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
                assert False, "action string can only be 'remove' or 'copy."


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
        assert False, "No valid template parameters are given for comparison."

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
        assert False, "Only 'multinomial' and 'binomial' distribution families are supported for " \
                      "grab_model_params_metrics function!"

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

            if type(each_param["default_value"]) == 'unicode':    # hyper-parameters cannot be unicode
                gridable_defaults.append(str(each_param["default_value"]))
            else:
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
            assert False, "column_type must be either 'fold_assignment' or 'weights_column'!"

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
                if ('int' in gridable_types[count_index]) or ('long' in gridable_types[count_index]):
                    # make sure integer values are not duplicated, using set action to remove duplicates
                    hyper_params[para_name] = list(set([random.randint(min_int_val, max_int_val) for p in
                                                        range(0, max_int_number)]))
                elif ('double' in gridable_types[count_index]) or ('float' in gridable_types[count_index]):
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


def extract_used_params_xval(a_grid_model, model_param_names, params_dict, algo="GBM"):
    """
    This function performs similar functions to function extract_used_params.  However, for max_runtime_secs,
    we need to go into each cross-valudation model and grab the max_runtime_secs and add them up in order to
    get the correct value.  In addition, we put your algo model specific parameters into params_dict.

    :param a_grid_model: list of models generated by gridsearch
    :param model_param_names: hyper-parameter names that are specified for the gridsearch.
    :param params_dict: dict containing name/value pairs specified to an algo.
    :param algo: string, optional, denoting the algo we are looking at.

    :return: params_used: a dict structure containing parameters that take on values as name/value pairs which
    will be used to build a model by hand using the same parameter setting as the model built by gridsearch.
    """
    params_used = dict()

    # need to extract the max_runtime_secs ONE cross-validation model or the base model
    if a_grid_model._is_xvalidated:
        xv_keys = a_grid_model._xval_keys

        for id in xv_keys:  # only need to get info from one model
            each_xv_model = h2o.get_model(id)   # get each model
            params_used = extract_used_params(model_param_names, each_xv_model.params, params_dict, algo)
            break
    else:
        params_used = extract_used_params(model_param_names, a_grid_model.params, params_dict, algo)

    return params_used


def extract_used_params(model_param_names, grid_model_params, params_dict, algo="GLM"):
    """
    This function is used to build a dict out of parameters used by our gridsearch to build a H2O model given
    the dict structure that describes the parameters and their values used by gridsearch to build that
    particular mode.

    :param model_param_names: list contains parameter names that we are interested in extracting
    :param grid_model_params: dict contains key as names of parameter and values as list of two values: default and
    actual.
    :param params_dict: dict containing extra parameters to add to params_used like family, e.g. 'gaussian',
    'binomial', ...

    :return: params_used: a dict structure containing parameters that take on values as name/value pairs which
    will be used to build a model by hand using the same parameter setting as the model built by gridsearch.
    """

    params_used = dict()
    grid_model_params_keys = grid_model_params.keys()

    for each_parameter in model_param_names:
        parameter_name = str(each_parameter)

        if parameter_name in grid_model_params_keys:
            params_used[parameter_name] = grid_model_params[each_parameter]['actual']

    if params_dict:
        for key, value in params_dict.items():
            params_used[key] = value    # add distribution family to parameters used list

    # only for GLM, change lambda to Lambda
    if algo =="GLM":
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
    # error_hyper_params = {k : v for k, v in hyper_params.items()}

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
        assert False, "word_length must be an integer greater than 0."


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
    # error_hyper_params = {k : v for k, v in hyper_params.items()}

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
            # randomly assign model parameter to one of the hyper-parameter values, should create error condition here
            param_value_index = random.randint(0, hyper_params_len-1)
            params_dict[param_name] = error_hyper_params[param_name][param_value_index]

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
        assert False, "error_diff_2_models: your table contains zero models."


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
            stop_now = evaluate_early_stopping(metric_list, stop_round, tolerance, bigger_is_better)

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
    if (bigger_is_better):
        metric_list.reverse()

    shortest_len = 2*stop_round
    if (isinstance(metric_list[0], float)):
        startIdx = 0
    else:
        startIdx = 1
        
    bestInLastK = 1.0*sum(metric_list[startIdx:stop_round])/stop_round
    lastBeforeK = 1.0*sum(metric_list[stop_round:shortest_len])/stop_round

    if not(np.sign(bestInLastK) == np.sign(lastBeforeK)):
        return False

    ratio = bestInLastK/lastBeforeK

    if math.isnan(ratio):
        return False

    if bigger_is_better:
        return not (ratio > 1+tolerance)
    else:
        return not (ratio < 1-tolerance)


def check_and_count_models(hyper_params, params_zero_one, params_more_than_zero, params_more_than_one,
                           params_zero_positive, max_grid_model):
    """
    This function will look at the hyper-parameter space set in hyper_params, generate a new hyper_param space that
    will contain a smaller number of grid_models.  It will determine how many models will be built from
    this new hyper_param space.  In order to arrive at the correct answer, it must discount parameter settings that
    are illegal.

    :param hyper_params: dict containing model parameter names and list of values to set it to
    :param params_zero_one: list containing model parameter names whose values must be between 0 and 1
    :param params_more_than_zero: list containing model parameter names whose values must exceed zero
    :param params_more_than_one: list containing model parameter names whose values must exceed one
    :param params_zero_positive: list containing model parameter names whose values must equal to or exceed zero
    :param max_grid_model: maximum number of grid_model that can be generated from the new hyper_params space

    :return: total model: integer denoting number of grid models that can be built from all legal parameter settings
                          in new hyper_parameter space
             final_hyper_params: dict of new hyper parameter space derived from the original hyper_params
    """

    total_model = 1
    hyper_keys = list(hyper_params)
    random.shuffle(hyper_keys)    # get all hyper_parameter names in random order
    final_hyper_params = dict()

    for param in hyper_keys:

        # this param should be > 0 and <= 2
        if param == "col_sample_rate_change_per_level":
            param_len = len([x for x in hyper_params["col_sample_rate_change_per_level"] if (x > 0)
                                 and (x <= 2)])
        elif param in params_zero_one:
            param_len = len([x for x in hyper_params[param] if (x >= 0)
                                 and (x <= 1)])
        elif param in params_more_than_zero:
            param_len = len([x for x in hyper_params[param] if (x > 0)])
        elif param in params_more_than_one:
            param_len = len([x for x in hyper_params[param] if (x > 1)])
        elif param in params_zero_positive:
            param_len = len([x for x in hyper_params[param] if (x >= 0)])
        else:
            param_len = len(hyper_params[param])

        if (param_len >= 0) and ((total_model*param_len) <= max_grid_model):
            total_model *= param_len
            final_hyper_params[param] = hyper_params[param]
        elif (total_model*param_len) > max_grid_model:
            break

    return total_model, final_hyper_params


def write_hyper_parameters_json(dir1, dir2, json_filename, hyper_parameters):
    """
    Write a json file of the hyper_parameters in directories dir1 and dir2 for debugging purposes.

    :param dir1: String containing first directory where you want to write the json file to
    :param dir2: String containing second directory where you want to write the json file to
    :param json_filename: String containing json file name
    :param hyper_parameters: dict containing hyper-parameters used
    """
    # save hyper-parameter file in test directory
    with open(os.path.join(dir1, json_filename), 'w') as test_file:
        json.dump(hyper_parameters, test_file)

    # save hyper-parameter file in sandbox
    with open(os.path.join(dir2, json_filename), 'w') as test_file:
        json.dump(hyper_parameters, test_file)


def compare_frames(frame1, frame2, numElements, tol_time=0, tol_numeric=0, strict=False, compare_NA=True,
                   custom_comparators=None):
    """
    This function will compare two H2O frames to make sure their dimension, and values in all cells are the same.
    It will not compare the column names though.

    :param frame1: H2O frame to be compared
    :param frame2: H2O frame to be compared
    :param numElements: integer to denote number of rows to compare.  Done to reduce compare time.
        Set to 0 or negative number if you want to compare all elements.
    :param tol_time: optional parameter to limit time value difference.
    :param tol_numeric: optional parameter to limit numeric value difference.
    :param strict: optional parameter to enforce strict comparison or not.  If True, column type must
        match in order to pass the test.
    :param compare_NA: optional parameter to compare NA or not.  For csv file generated from orc file, the
        NAs are represented as some other symbol but our CSV will not be able to parse it correctly as NA.
        In this case, do not compare the number of NAs.
    :param custom_comparators: dictionary specifying custom comparators for some columns. 
    :return: boolean: True, the two frames are equal and False otherwise.
    """

    # check frame dimensions
    rows1, cols1 = frame1.dim
    rows2, cols2 = frame2.dim

    assert rows1 == rows2 and cols1 == cols2, "failed dim check! frame 1 rows:{0} frame 2 rows:{1} frame 1 cols:{2} " \
                                              "frame2 cols:{3}".format(rows1, rows2, cols1, cols2)

    na_frame1 = frame1.isna().sum().sum(axis=1)[:,0]
    na_frame2 = frame2.isna().sum().sum(axis=1)[:,0]
    probVal = numElements/rows1 if numElements > 0 else 1

    if compare_NA:      # check number of missing values
        assert na_frame1.flatten() == na_frame2.flatten(), "failed numbers of NA check!  Frame 1 NA number: {0}, frame 2 " \
                                   "NA number: {1}".format(na_frame1.flatten(), na_frame2.flatten())

    # check column types are the same before proceeding to check each row content.
    for col_ind in range(cols1):

        c1_key = frame1.columns[col_ind]
        c2_key = frame2.columns[col_ind]
        c2_type = frame2.types[c2_key]
        c1_type = frame1.types[c1_key]

        print("###### Comparing column: {0} and column type is {1}.".format(col_ind, c1_type))

        if strict:  # every column type must match
            assert c1_type == c2_type, "failed column type check! frame1 col type: {0}, frame2 col type: " \
                                       "{1}".format(c1_type, c2_type)
        else:
            if str(c2_type) == 'enum':  # orc files do not have enum column type.  We convert it here
                frame1[col_ind].asfactor()

        if custom_comparators and c1_key in custom_comparators:
            custom_comparators[c1_key](frame1, frame2, col_ind, rows1, numElements)
        elif (str(c1_type) == 'string') or (str(c1_type) == 'enum'):
            # compare string
            compare_frames_local_onecolumn_NA_string(frame1[col_ind], frame2[col_ind], prob=probVal)
        else:
            if str(c2_type) == 'time':  # compare time columns
                compare_frames_local_onecolumn_NA(frame1[col_ind], frame2[col_ind], prob=probVal, tol=tol_time)
            else:
                compare_frames_local_onecolumn_NA(frame1[col_ind], frame2[col_ind], prob=probVal, tol=tol_numeric)
    return True


def catch_warnings():
    import warnings
    warnings.simplefilter("always", RuntimeWarning)
    for v in sys.modules.values():
        if getattr(v, '__warningregistry__', None):
            v.__warningregistry__ = {}
    return warnings.catch_warnings(record=True)


def contains_warning(ws, message):
    return any(issubclass(w.category, RuntimeWarning) and message in str(w.message) for w in ws)


def no_warnings(ws):
    return len(ws) == 0


def expect_warnings(filewithpath, warn_phrase="warn", warn_string_of_interest="warn", number_of_times=1, in_hdfs=False):
    """
            This function will execute a command to run and analyze the print outs of
    running the command.  The goal here is to capture any warnings that we may expect
    out of running those commands.

    :param filewithpath: name of file to be parsed with path
    :param warn_phrase: capture the warning header, sometimes it is warn or userwarn.
    :param warn_string_of_interest: specific warning message string
    :param number_of_times: number of warning lines we are expecting.
    :return: True if warning was found and False otherwise
    """

    number_warngings = 0

    buffer = StringIO()     # redirect warning messages to string buffer for later analysis
    sys.stderr = buffer
    frame = None

    if in_hdfs:
        frame = h2o.import_file(filewithpath)
    else:
        frame = h2o.import_file(path=locate(filewithpath))

    sys.stderr = sys.__stderr__     # redirect it back to stdout.
    try:        # for python 2.7
        if len(buffer.buflist) > 0:
            for index in range(len(buffer.buflist)):
                print("*** captured warning message: {0}".format(buffer.buflist[index]))
                if (warn_phrase in buffer.buflist[index]) and (warn_string_of_interest in buffer.buflist[index]):
                    number_warngings = number_warngings+1

    except:     # for python 3.
        warns = buffer.getvalue()
        print("*** captured warning message: {0}".format(warns))
        if (warn_phrase in warns) and (warn_string_of_interest in warns):
            number_warngings = number_warngings+1

    print("Number of warnings found: {0} and number of times that warnings should appear {1}.".format(number_warngings,
                                                                                                      number_of_times))
    if number_warngings >= number_of_times:
        return True
    else:
        return False


def compare_frame_summary(frame1_summary, frame2_summary, compareNames=False, compareTypes=False):
    """
        This method is written to compare the frame summary between two frames.

    :param frame1_summary:
    :param frame2_summary:
    :param compareNames:
    :param compareTypes:
    :return:
    """

    frame1_column_number = len(frame1_summary)
    frame2_column_number = len(frame2_summary)

    assert frame1_column_number == frame2_column_number, "failed column number check!  Frame 1 column number: {0}," \
                                                         "frame 2 column number: {1}".format(frame1_column_number,
                                                                                             frame2_column_number)

    for col_index in range(frame1_column_number):   # check summary for each column
        for key_val in list(frame1_summary[col_index]):

            if not(compareNames) and (str(key_val) == 'label'):
                continue

            if not(compareTypes) and (str(key_val) == 'type'):
                continue

            if str(key_val) == 'precision':     # skip comparing precision
                continue

            val1 = frame1_summary[col_index][key_val]
            val2 = frame2_summary[col_index][key_val]

            if isinstance(val1, list) or isinstance(val1, dict):
                if isinstance(val1, dict):
                    assert val1 == val2, "failed column summary comparison for column {0} and summary " \
                                         "type {1}, frame 1 value is {2}, frame 2 value is " \
                                         "{3}".format(col_index, str(key_val), val1, val2)
                else:
                    if len(val1) > 0:
                        # find if elements are float
                        float_found = False

                        for ind in range(len(val1)):
                            if isinstance(val1[ind], float):
                                float_found = True
                                break

                        if float_found:
                            for ind in range(len(val1)):
                                if not(str(val1[ind] == 'NaN')):
                                    assert abs(val1[ind]-val2[ind]) < 1e-5, "failed column summary comparison for " \
                                                                            "column {0} and summary type {1}, frame 1" \
                                                                            " value is {2}, frame 2 value is " \
                                                                            "{3}".format(col_index, str(key_val),
                                                                                         val1[ind], val2[ind])
                        else:
                            assert val1 == val2, "failed column summary comparison for column {0} and summary" \
                                                 " type {1}, frame 1 value is {2}, frame 2 value is " \
                                                 "{3}".format(col_index, str(key_val), val1, val2)
            else:
                if isinstance(val1, float):
                    assert abs(val1-val2) < 1e-5, "failed column summary comparison for column {0} and summary type " \
                                                  "{1}, frame 1 value is {2}, frame 2 value is " \
                                                  "{3}".format(col_index, str(key_val), val1, val2)
                else:
                    assert val1 == val2, "failed column summary comparison for column {0} and summary type " \
                                         "{1}, frame 1 value is {2}, frame 2 value is " \
                                         "{3}".format(col_index, str(key_val), val1, val2)


def cannaryHDFSTest(hdfs_name_node, file_name):
    """
    This function is written to detect if the hive-exec version is too old.  It will return
    True if it is too old and false otherwise.

    :param hdfs_name_node:
    :param file_name:
    :return:
    """
    url_orc = "hdfs://{0}{1}".format(hdfs_name_node, file_name)

    try:
        tempFrame = h2o.import_file(url_orc)
        h2o.remove(tempFrame)
        print("Your hive-exec version is good.  Parsing success for {0}.".format(url_orc))
        return False
    except Exception as e:
        print("Error exception is {0}".format(str(e)))

        if "NoSuchFieldError: vector" in str(e):
            return True
        else:       # exception is caused by other reasons.
            return False


def extract_scoring_history_field(aModel, fieldOfInterest, takeFirst=False):
    """
    Given a fieldOfInterest that are found in the model scoring history, this function will extract the list
    of field values for you from the model.

    :param aModel: H2O model where you want to extract a list of fields from the scoring history
    :param fieldOfInterest: string representing a field of interest.
    :return: List of field values or None if it cannot be found
    """
    return extract_from_twoDimTable(aModel._model_json["output"]["scoring_history"], fieldOfInterest, takeFirst)


def extract_from_twoDimTable(metricOfInterest, fieldOfInterest, takeFirst=False):
    """
    Given a fieldOfInterest that are found in the model scoring history, this function will extract the list
    of field values for you from the model.

    :param aModel: H2O model where you want to extract a list of fields from the scoring history
    :param fieldOfInterest: string representing a field of interest.
    :return: List of field values or None if it cannot be found
    """

    allFields = metricOfInterest._col_header
    return extract_field_from_twoDimTable(allFields, metricOfInterest.cell_values, fieldOfInterest, takeFirst=False)


def extract_field_from_twoDimTable(allFields, cell_values, fieldOfInterest, takeFirst=False):
    if fieldOfInterest in allFields:
        cellValues = []
        fieldIndex = allFields.index(fieldOfInterest)
        for eachCell in cell_values:
            cellValues.append(eachCell[fieldIndex])
            if takeFirst:  # only grab the result from the first iteration.
                break
        return cellValues
    else:
        return None

def model_run_time_sorted_by_time(model_list):
    """
    This function is written to sort the metrics that we care in the order of when the model was built.  The
    oldest model metric will be the first element.
    :param model_list: list of models built sequentially that contains metric of interest among other fields
    :return: model run time in secs sorted by order of building
    """

    model_num = len(model_list)

    model_runtime_sec_list = [None] * model_num


    for index in range(model_num):
        model_index = int(model_list[index]._id.split('_')[-1]) - 1  # model names start at 1
        model_runtime_sec_list[model_index] = \
            (model_list[index]._model_json["output"]["run_time"]/1000.0)

    return model_runtime_sec_list


def model_seed_sorted(model_list):
    """
    This function is written to find the seed used by each model in the order of when the model was built.  The
    oldest model metric will be the first element.
    :param model_list: list of models built sequentially that contains metric of interest among other fields
    :return: model seed sorted by order of building
    """

    model_num = len(model_list)

    model_seed_list = [None] * model_num


    for index in range(model_num):
        for pIndex in range(len(model_list.models[0]._model_json["parameters"])):
            if model_list.models[index]._model_json["parameters"][pIndex]["name"]=="seed":
                model_seed_list[index]=model_list.models[index]._model_json["parameters"][pIndex]["actual_value"]
                break
    model_seed_list.sort()
    return model_seed_list


def check_ignore_cols_automl(models,names,x,y):
    models = sum(models.as_data_frame().values.tolist(),[])
    for model in models:
        if "StackedEnsemble" in model:
            continue
        else:
            assert set(h2o.get_model(model).params["ignored_columns"]["actual"]) == set(names) - {y} - set(x), \
                "ignored columns are not honored for model " + model


# This method is not changed to local method using as_data_frame because the frame size is too big.
def check_sorted_2_columns(frame1, sorted_column_indices, prob=0.5, ascending=[True, True]):
    for colInd in sorted_column_indices:
        for rowInd in range(0, frame1.nrow-1):
            if (random.uniform(0.0,1.0) < prob):
                if colInd == sorted_column_indices[0]:
                    if not(math.isnan(frame1[rowInd, colInd])) and not(math.isnan(frame1[rowInd+1,colInd])):
                        if ascending[colInd]:
                            assert frame1[rowInd,colInd] <= frame1[rowInd+1,colInd], "Wrong sort order: value at row {0}: {1}, value at " \
                                                               "row {2}: {3}".format(rowInd, frame1[rowInd,colInd],
                                                                                     rowInd+1, frame1[rowInd+1,colInd])
                        else:
                            assert frame1[rowInd,colInd] >= frame1[rowInd+1,colInd], "Wrong sort order: value at row {0}: {1}, value at " \
                                                                                     "row {2}: {3}".format(rowInd, frame1[rowInd,colInd],
                                                                                                           rowInd+1, frame1[rowInd+1,colInd])
                else: # for second column
                    if not(math.isnan(frame1[rowInd, sorted_column_indices[0]])) and not(math.isnan(frame1[rowInd+1,sorted_column_indices[0]])):
                        if (frame1[rowInd,sorted_column_indices[0]]==frame1[rowInd+1, sorted_column_indices[0]]):  # meaningful to compare row entries then
                            if not(math.isnan(frame1[rowInd, colInd])) and not(math.isnan(frame1[rowInd+1,colInd])):
                                if ascending[colInd]:
                                    assert frame1[rowInd,colInd] <= frame1[rowInd+1,colInd], "Wrong sort order: value at row {0}: {1}, value at " \
                                                                           "row {2}: {3}".format(rowInd, frame1[rowInd,colInd],
                                                                                                 rowInd+1, frame1[rowInd+1,colInd])
                                else:
                                    assert frame1[rowInd,colInd] >= frame1[rowInd+1,colInd], "Wrong sort order: value at row {0}: {1}, value at " \
                                                                                             "row {2}: {3}".format(rowInd, frame1[rowInd,colInd],
                                                                                                                   rowInd+1, frame1[rowInd+1,colInd])
# This method is not changed to local method using as_data_frame because the frame size is too big.
def check_sorted_1_column(frame1, sorted_column_index, prob=0.5, ascending=True):
    totRow = frame1.nrow * prob
    skipRow = int(frame1.nrow/totRow)
    for rowInd in range(0, frame1.nrow-1, skipRow):
        if not (math.isnan(frame1[rowInd, sorted_column_index])) and not (
            math.isnan(frame1[rowInd + 1, sorted_column_index])):
            if ascending:
                assert frame1[rowInd, sorted_column_index] <= frame1[
                    rowInd + 1, sorted_column_index], "Wrong sort order: value at row {0}: {1}, value at " \
                                                      "row {2}: {3}".format(rowInd,
                                                                            frame1[rowInd, sorted_column_index],
                                                                            rowInd + 1,
                                                                            frame1[rowInd + 1, sorted_column_index])
            else:
                assert frame1[rowInd, sorted_column_index] >= frame1[
                    rowInd + 1, sorted_column_index], "Wrong sort order: value at row {0}: {1}, value at " \
                                                      "row {2}: {3}".format(rowInd,
                                                                            frame1[rowInd, sorted_column_index],
                                                                            rowInd + 1,
                                                                            frame1[rowInd + 1, sorted_column_index])

def assert_correct_frame_operation(sourceFrame, h2oResultFrame, operString):
    """
    This method checks each element of a numeric H2OFrame and throw an assert error if its value does not
    equal to the same operation carried out by python.

    :param sourceFrame: original H2OFrame.
    :param h2oResultFrame: H2OFrame after operation on original H2OFrame is carried out.
    :param operString: str representing one of 'abs', 'acos', 'acosh', 'asin', 'asinh', 'atan', 'atanh',
        'ceil', 'cos', 'cosh', 'cospi', 'cumprod', 'cumsum', 'digamma', 'exp', 'expm1', 'floor', 'round',
        'sin', 'sign', 'round', 'sinh', 'tan', 'tanh'
    :return: None.
    """
    validStrings = ['acos', 'acosh', 'asin', 'asinh', 'atan', 'atanh', 'ceil', 'cos', 'cosh',
                     'exp', 'floor', 'gamma', 'lgamma', 'log', 'log10', 'sin', 'sinh',
                    'sqrt', 'tan', 'tanh', 'trigamma', 'expm1']
    npValidStrings = ['log2', 'sign']
    nativeStrings = ['round', 'abs', 'cumsum']
    multpi = ['cospi', 'sinpi', 'tanpi']
    others = ['log1p', 'signif', 'trigamma', 'digamma', 'cumprod']
    # check for valid operString
    assert operString in validStrings+npValidStrings+nativeStrings+multpi+others, "Illegal operator " \
                                                                           "{0} specified.".format(operString)
    result_comp = lambda x:x # default method

    if operString == "log1p":
        result_comp = lambda x:math.log(x+1)
    elif operString == 'signif':
        result_comp = lambda x:round(x, 7)
    elif operString == 'trigamma':
        result_comp = lambda x:scipy.special.polygamma(1, x)
    elif operString == 'digamma':
        result_comp = lambda x:scipy.special.polygamma(0, x)
    elif operString=='cumprod':
        result_comp = lambda x:factorial(x)
       # stringOperations = 'result_val = factorial(sourceFrame[row_ind, col_ind])'
    elif operString in validStrings:
        result_comp = lambda x:getattr(math, operString)(x)
    elif operString in nativeStrings:
        result_comp =lambda x:__builtins__.get(operString)(x)
        stringOperations = 'result_val = '+operString+'(sourceFrame[row_ind, col_ind])'
    elif operString in npValidStrings:
        result_comp = lambda x:getattr(np, operString)(x)
      #  stringOperations = 'result_val = np.'+operString+'(sourceFrame[row_ind, col_ind])'
    elif operString in multpi:
        result_comp = lambda x:getattr(math, operString.split('p')[0])(x*math.pi)
        #stringOperations = 'result_val = math.'+operString.split('p')[0]+'(sourceFrame[row_ind, col_ind]*math.pi)'

    for col_ind in range(sourceFrame.ncols):
        for row_ind in range(sourceFrame.nrows):
            result_val = result_comp(sourceFrame[row_ind, col_ind])
            assert abs(h2oResultFrame[row_ind, col_ind]-result_val) <= 1e-6, \
                " command {0}({3}) is not working. Expected: {1}. Received: {2}".format(operString, result_val,
                                                                                   h2oResultFrame[row_ind, col_ind], sourceFrame[row_ind, col_ind])

def factorial(n):
    """
    Defined my own factorial just in case using python2.5 or less.

    :param n:
    :return:
    """
    if n>0 and n<2:
        return 1
    if n>=2:
        return n*factorial(n-1)

def cumop(items, op, colInd=0):   # take in one column only
    res = [None]*len(items)
    for index in range(len(items)):
        res[index] = op(res[index-1], items[index, colInd]) if index > 0 else items[index, colInd]
    return res

def compare_string_frames_local(f1, f2, prob=0.5):
    temp1 = f1.as_data_frame(use_pandas=False)
    temp2 = f2.as_data_frame(use_pandas=False)
    cname1 = temp1[0]
    cname2 = temp2[0]
    assert (f1.nrow==f2.nrow) and (f1.ncol==f2.ncol), "The two frames are of different sizes."
    for colInd in range(f1.ncol):
        name1 = cname1[colInd]
        for rowInd in range(1, f2.nrow):
            if random.uniform(0,1) < prob:
                assert temp1[rowInd][colInd]==temp2[rowInd][cname2.index(name1)], "Failed frame values check at row {2} and column {3}! " \
                                                                     "frame1 value: {0}, frame2 value: " \
                                                                     "{1}".format(temp1[rowInd][colInd], temp2[rowInd][colInd], rowInd, colInd)


def check_data_rows(f1, f2, index_list=[], num_rows=10):
    '''
        This method will compare the relationships of the data rows within each frames.  In particular, we are
        interested in the relative direction of each row vectors and the relative distances. No assertions will
        be thrown.

    :param f1:
    :param f2:
    :param index_list:
    :param num_rows:
    :return:
    '''
    temp1 = f1.as_data_frame(use_pandas=True).as_matrix()
    temp2 = f2.as_data_frame(use_pandas=True).as_matrix()
    if len(index_list)==0:
        index_list = random.sample(range(f1.nrow), num_rows)

    maxInnerProduct = 0
    maxDistance = 0

    for row_index in range(1, len(index_list)):
        r1 = np.inner(temp1[index_list[row_index-1]], temp1[index_list[row_index]])
        r2 = np.inner(temp2[index_list[row_index-1]], temp2[index_list[row_index]])
        d1 = np.linalg.norm(temp1[index_list[row_index-1]]-temp1[index_list[row_index]])
        d2 = np.linalg.norm(temp2[index_list[row_index-1]]-temp2[index_list[row_index]])

        diff1 = min(abs(r1-r2), abs(r1-r2)/max(abs(r1), abs(r2)))
        maxInnerProduct = max(maxInnerProduct, diff1)
        diff2 = min(abs(d1-d2), abs(d1-d2)/max(abs(d1), abs(d2)))
        maxDistance = max(maxDistance, diff2)

    print("Maximum inner product different is {0}.  Maximum distance difference is "
      "{1}".format(maxInnerProduct, maxDistance))


def compare_data_rows(f1, f2, index_list=[], num_rows=10, tol=1e-3):
    '''
        This method will compare the relationships of the data rows within each frames.  In particular, we are
        interested in the relative direction of each row vectors and the relative distances. An assertion will be
        thrown if they are different beyond a tolerance.

    :param f1:
    :param f2:
    :param index_list:
    :param num_rows:
    :return:
    '''
    temp1 = f1.as_data_frame(use_pandas=True).as_matrix()
    temp2 = f2.as_data_frame(use_pandas=True).as_matrix()
    if len(index_list)==0:
        index_list = random.sample(range(f1.nrow), num_rows)

    maxInnerProduct = 0
    maxDistance = 0
    for row_index in range(1, len(index_list)):
        r1 = np.inner(temp1[index_list[row_index-1]], temp1[index_list[row_index]])
        r2 = np.inner(temp2[index_list[row_index-1]], temp2[index_list[row_index]])
        d1 = np.linalg.norm(temp1[index_list[row_index-1]]-temp1[index_list[row_index]])
        d2 = np.linalg.norm(temp2[index_list[row_index-1]]-temp2[index_list[row_index]])

        diff1 = min(abs(r1-r2), abs(r1-r2)/max(abs(r1), abs(r2)))
        maxInnerProduct = max(maxInnerProduct, diff1)
        diff2 = min(abs(d1-d2), abs(d1-d2)/max(abs(d1), abs(d2)))
        maxDistance = max(maxDistance, diff2)

        assert diff1 < tol, \
            "relationship between data row {0} and data row {1} are different among the two dataframes.  Inner " \
            "product from frame 1 is {2}.  Inner product from frame 2 is {3}.  The difference between the two is" \
            " {4}".format(index_list[row_index-1], index_list[row_index], r1, r2, diff1)


        assert diff2 < tol, \
                "distance betwee data row {0} and data row {1} are different among the two dataframes.  Distance " \
                "between 2 rows from frame 1 is {2}.  Distance between 2 rows from frame 2 is {3}.  The difference" \
                " between the two is {4}".format(index_list[row_index-1], index_list[row_index], d1, d2, diff2)
    print("Maximum inner product different is {0}.  Maximum distance difference is "
          "{1}".format(maxInnerProduct, maxDistance))

def compute_frame_diff(f1, f2):
    '''
    This method will take the absolute difference two frames and sum across all elements
    :param f1:
    :param f2:
    :return:
    '''

    frameDiff = h2o.H2OFrame.sum(h2o.H2OFrame.sum(h2o.H2OFrame.abs(f1-f2)), axis=1)[0,0]
    return frameDiff

def compare_frames_local(f1, f2, prob=0.5, tol=1e-6, returnResult=False):
    '''
    Compare two h2o frames and make sure they are equal.  However, we do not compare uuid column at this point
    :param f1:
    :param f2:
    :param prob:
    :param tol:
    :param returnResult:
    :return:
    '''
    assert (f1.nrow==f2.nrow) and (f1.ncol==f2.ncol), "Frame 1 row {0}, col {1}.  Frame 2 row {2}, col {3}.  They are " \
                                                      "different.".format(f1.nrow, f1.ncol, f2.nrow, f2.ncol)
    typeDict = f1.types
    frameNames = f1.names

    for colInd in range(f1.ncol):
        if (typeDict[frameNames[colInd]]==u'enum'):
            if returnResult:
                result = compare_frames_local_onecolumn_NA_enum(f1[colInd], f2[colInd], prob=prob, tol=tol, returnResult=returnResult)
                if not(result) and returnResult:
                    return False
            else:
                result = compare_frames_local_onecolumn_NA_enum(f1[colInd], f2[colInd], prob=prob, tol=tol, returnResult=returnResult)
                if not(result) and returnResult:
                    return False
        elif (typeDict[frameNames[colInd]]==u'string'):
            if returnResult:
                result =  compare_frames_local_onecolumn_NA_string(f1[colInd], f2[colInd], prob=prob, returnResult=returnResult)
                if not(result) and returnResult:
                    return False
            else:
                compare_frames_local_onecolumn_NA_string(f1[colInd], f2[colInd], prob=prob, returnResult=returnResult)
        elif (typeDict[frameNames[colInd]]==u'uuid'):
            continue    # do nothing here
        else:
            if returnResult:
                result = compare_frames_local_onecolumn_NA(f1[colInd], f2[colInd], prob=prob, tol=tol, returnResult=returnResult)
                if not(result) and returnResult:
                    return False
            else:
                compare_frames_local_onecolumn_NA(f1[colInd], f2[colInd], prob=prob, tol=tol, returnResult=returnResult)

    if returnResult:
        return True

def compare_frames_local_svm(f1, f2, prob=0.5, tol=1e-6, returnResult=False):
    '''
    compare f1 and f2 but with f2 parsed from svmlight parser.  Here, the na's should be replaced with 0.0

    :param f1: normal h2oFrame
    :param f2: h2oFrame parsed from a svmlight parser.
    :param prob:
    :param tol:
    :param returnResult:
    :return:
    '''
    assert (f1.nrow==f2.nrow) and (f1.ncol==f2.ncol), "The two frames are of different sizes."
    temp1 = f1.as_data_frame(use_pandas=False)
    temp2 = f2.as_data_frame(use_pandas=False)
    for rowInd in range(1, f1.nrow):
        for colInd in range(f1.ncol):
            if (len(temp1[rowInd][colInd]))==0: # encounter NAs
                if returnResult:
                    if (abs(float(temp2[rowInd][colInd]))) > tol:
                        return False
                assert (abs(float(temp2[rowInd][colInd]))) <= tol, \
                    "Expected: 0.0 but received: {0} for row: {1}, col: " \
                    "{2}".format(temp2[rowInd][colInd], rowInd, colInd)
            else:
                if returnResult:
                    if abs(float(temp1[rowInd][colInd])-float(temp2[rowInd][colInd]))>tol:
                        return False
                assert abs(float(temp1[rowInd][colInd])-float(temp2[rowInd][colInd]))<=tol, \
                    "Expected: {1} but received: {0} for row: {2}, col: " \
                    "{3}".format(temp2[rowInd][colInd], temp1[rowInd][colInd], rowInd, colInd)


    if returnResult:
        return True


# frame compare with NAs in column
def compare_frames_local_onecolumn_NA(f1, f2, prob=0.5, tol=1e-6, returnResult=False, oneLessRow=False):
    if (f1.types[f1.names[0]] == u'time'):   # we have to divide by 1000 before converting back and forth between ms and time format
        tol = 10

    temp1 = f1.as_data_frame(use_pandas=False)
    temp2 = f2.as_data_frame(use_pandas=False)
    assert (f1.nrow==f2.nrow) and (f1.ncol==f2.ncol), "The two frames are of different sizes."
    if oneLessRow:
        lastF2Row = f2.nrow
    else:
        lastF2Row = f2.nrow+1
    for colInd in range(f1.ncol):
        for rowInd in range(1,lastF2Row):
            if (random.uniform(0,1) < prob):
                if len(temp1[rowInd]) == 0 or len(temp2[rowInd]) == 0:
                    if returnResult:
                        if not(len(temp1[rowInd]) == len(temp2[rowInd])):
                            return False
                    else:
                        assert len(temp1[rowInd]) == len(temp2[rowInd]), "Failed frame values check at row {2} ! " \
                                                                     "frame1 value: {0}, frame2 value: " \
                                                                     "{1}".format(temp1[rowInd], temp2[rowInd], rowInd)
                else:
                    v1 = float(temp1[rowInd][colInd])
                    v2 = float(temp2[rowInd][colInd])
                    diff = abs(v1-v2)/max(1.0, abs(v1), abs(v2))
                    if returnResult:
                        if (diff > tol):
                            return False
                    else:
                        assert diff<=tol, "Failed frame values check at row {2} and column {3}! frame1 value: {0}, column name: {4}. frame2 value: " \
                                          "{1}, column name:{5}".format(temp1[rowInd][colInd], temp2[rowInd][colInd], rowInd, colInd, f1.names[0], f2.names[0])
    if returnResult:
        return True

# frame compare with NAs in column
def compare_frames_local_onecolumn_NA_enum(f1, f2, prob=0.5, tol=1e-6, returnResult=False):
    temp1 = f1.as_data_frame(use_pandas=False)
    temp2 = f2.as_data_frame(use_pandas=False)
    assert (f1.nrow==f2.nrow) and (f1.ncol==f2.ncol), "The two frames are of different sizes."
    for colInd in range(f1.ncol):
        for rowInd in range(1,f2.nrow+1):
            if (random.uniform(0,1) < prob):
                if len(temp1[rowInd]) == 0 or len(temp2[rowInd]) == 0:
                    if returnResult:
                        if not(len(temp1[rowInd]) == len(temp2[rowInd])):
                            return False
                    else:
                        assert len(temp1[rowInd]) == len(temp2[rowInd]), "Failed frame values check at row {2} ! " \
                                                                     "frame1 value: {0}, frame2 value: " \
                                                                     "{1}".format(temp1[rowInd], temp2[rowInd], rowInd)
                else:
                    if returnResult:
                        if not(temp1[rowInd][colInd]==temp2[rowInd][colInd]):
                            return False
                    else:
                        assert temp1[rowInd][colInd]==temp2[rowInd][colInd], "Failed frame values check at row {2} and column {3}! frame1 value: {0}, column name: {4}. frame2 value: " \
                                      "{1}, column name:{5}".format(temp1[rowInd][colInd], temp2[rowInd][colInd], rowInd, colInd, f1.names[0], f2.names[0])

    if returnResult:
        return True

# frame compare with NAs in column
def compare_frames_local_onecolumn_NA_string(f1, f2, prob=0.5, returnResult=False):
    temp1 = f1.as_data_frame(use_pandas=False)
    temp2 = f2.as_data_frame(use_pandas=False)
    assert (f1.nrow==f2.nrow) and (f1.ncol==f2.ncol), "The two frames are of different sizes."
    for colInd in range(f1.ncol):
        for rowInd in range(1,f2.nrow+1):
            if (random.uniform(0,1) < prob):
                if len(temp1[rowInd]) == 0 or len(temp2[rowInd]) == 0:
                    if returnResult:
                        if not(len(temp1[rowInd]) == len(temp2[rowInd])):
                            return False
                    else:
                        assert len(temp1[rowInd]) == len(temp2[rowInd]), "Failed frame values check at row {2} ! " \
                                                                     "frame1 value: {0}, frame2 value: " \
                                                                     "{1}".format(temp1[rowInd], temp2[rowInd], rowInd)
                else:
                    if returnResult:
                        if not(temp1[rowInd][colInd]==temp2[rowInd][colInd]):
                            return False
                    else:
                        assert temp1[rowInd][colInd]==temp2[rowInd][colInd], "Failed frame values check at row {2} and column {3}! frame1 value: {0}, column name: {4}. frame2 value: " \
                                                                             "{1}, column name:{5}".format(temp1[rowInd][colInd], temp2[rowInd][colInd], rowInd, colInd, f1.names[0], f2.names[0])

    if returnResult:
        return True

def build_save_model_generic(params, x, train, respName, algoName, tmpdir):
    if algoName.lower() == "gam":
        model = H2OGeneralizedAdditiveEstimator(**params)
    elif algoName.lower() == "glm":
        model = H2OGeneralizedLinearEstimator(**params)
    elif algoName.lower() == "gbm":
        model = H2OGradientBoostingEstimator(**params)
    elif algoName.lower() == "drf":
        model = H2ORandomForestEstimator(**params)
    else:
        raise Exception("build_save_model does not support algo "+algoName+".  Please add this to build_save_model.")
    model.train(x=x, y=respName, training_frame=train)
    model.download_mojo(path=tmpdir)
    return model

# generate random dataset, copied from Pasha
def random_dataset(response_type, verbose=True, ncol_upper=25000, ncol_lower=15000, NTESTROWS=200, missing_fraction=0.0, seed=None):
    """Create and return a random dataset."""
    if verbose:
        print("\nCreating a dataset for a %s problem:" % response_type)
    random.seed(seed)
    fractions = {k + "_fraction": random.random() for k in "real categorical integer time string binary".split()}
    fractions["string_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["binary_fraction"] /= 3
    fractions["time_fraction"] /= 2

    sum_fractions = sum(fractions.values())
    for k in fractions:
        fractions[k] /= sum_fractions
    if response_type == 'binomial':
        response_factors = 2
    elif response_type == 'gaussian':
        response_factors = 1
    else:
        response_factors = random.randint(3, 10)
    df = h2o.create_frame(rows=random.randint(ncol_lower, ncol_upper) + NTESTROWS, cols=random.randint(3, 20),
                          missing_fraction=missing_fraction,
                          has_response=True, response_factors=response_factors, positive_response=True, factors=10,
                          seed=seed, **fractions)
    if verbose:
        print()
        df.show()
    return df

# generate random dataset of ncolumns of Strings, copied from Pasha
def random_dataset_strings_only(nrow, ncol, seed=None):
    """Create and return a random dataset."""
    fractions = dict()
    fractions["real_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["categorical_fraction"] = 0
    fractions["integer_fraction"] = 0
    fractions["time_fraction"] = 0
    fractions["string_fraction"] = 1  # Right now we are dropping string columns, so no point in having them.
    fractions["binary_fraction"] = 0
    return h2o.create_frame(rows=nrow, cols=ncol, missing_fraction=0, has_response=False, seed=seed, **fractions)

def random_dataset_all_types(nrow, ncol, seed=None):
    fractions=dict()
    fractions['real_fraction']=0.16,
    fractions['categorical_fraction']=0.16,
    fractions['integer_fraction']=0.16,
    fractions['binary_fraction']=0.16,
    fractions['time_fraction']=0.16,
    fractions['string_fraction']=0.2
    return h2o.create_frame(rows=nrow, cols=ncol, missing_fraction=0.1, has_response=False, seed=seed)

# generate random dataset of ncolumns of enums only, copied from Pasha
def random_dataset_enums_only(nrow, ncol, factorL=10, misFrac=0.01, randSeed=None):
    """Create and return a random dataset."""
    fractions = dict()
    fractions["real_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["categorical_fraction"] = 1
    fractions["integer_fraction"] = 0
    fractions["time_fraction"] = 0
    fractions["string_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["binary_fraction"] = 0

    df = h2o.create_frame(rows=nrow, cols=ncol, missing_fraction=misFrac, has_response=False, factors=factorL,
                          seed=randSeed, **fractions)
    return df

# generate random dataset of ncolumns of enums only, copied from Pasha
def random_dataset_int_only(nrow, ncol, rangeR=10, misFrac=0.01, randSeed=None):
    """Create and return a random dataset."""
    fractions = dict()
    fractions["real_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["categorical_fraction"] = 0
    fractions["integer_fraction"] = 1
    fractions["time_fraction"] = 0
    fractions["string_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["binary_fraction"] = 0

    df = h2o.create_frame(rows=nrow, cols=ncol, missing_fraction=misFrac, has_response=False, integer_range=rangeR,
                          seed=randSeed, **fractions)
    return df

# generate random dataset of ncolumns of integer and reals, copied from Pasha
def random_dataset_numeric_only(nrow, ncol, integerR=100, misFrac=0.01, randSeed=None):
    """Create and return a random dataset."""
    fractions = dict()
    fractions["real_fraction"] = 0.25  # Right now we are dropping string columns, so no point in having them.
    fractions["categorical_fraction"] = 0
    fractions["integer_fraction"] = 0.75
    fractions["time_fraction"] = 0
    fractions["string_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["binary_fraction"] = 0

    df = h2o.create_frame(rows=nrow, cols=ncol, missing_fraction=misFrac, has_response=False, integer_range=integerR,
                          seed=randSeed, **fractions)
    return df

# generate random dataset of ncolumns of integer and reals, copied from Pasha
def random_dataset_real_only(nrow, ncol, realR=100, misFrac=0.01, randSeed=None):
    """Create and return a random dataset."""
    fractions = dict()
    fractions["real_fraction"] = 1  # Right now we are dropping string columns, so no point in having them.
    fractions["categorical_fraction"] = 0
    fractions["integer_fraction"] = 0
    fractions["time_fraction"] = 0
    fractions["string_fraction"] = 0  # Right now we are dropping string columns, so no point in having them.
    fractions["binary_fraction"] = 0

    df = h2o.create_frame(rows=nrow, cols=ncol, missing_fraction=misFrac, has_response=False, integer_range=realR,
                          seed=randSeed, **fractions)
    return df

def getMojoName(modelID):
    regex = re.compile("[+\\-* !@#$%^&()={}\\[\\]|;:'\"<>,.?/]")
    return regex.sub("_", modelID)


def convertH2OFrameToDMatrix(h2oFrame, yresp, enumCols=[]):
    """
    This method will convert a H2OFrame containing to a DMatrix that is can be used by native XGBoost.  The
    H2OFrame can contain numerical and enum columns.  Note that H2O one-hot-encoding introduces a missing(NA)
    column. There can be NAs in any columns.

    :param h2oFrame: H2OFrame to be converted to DMatrix
    :param yresp: string denoting the response column name
    :param enumCols: list of enum column names in the H2OFrame

    :return: DMatrix
    """
    import xgboost as xgb

    pandas = __convertH2OFrameToPandas__(h2oFrame, yresp, enumCols);

    return xgb.DMatrix(data=pandas[0], label=pandas[1])

def convertH2OFrameToDMatrixSparse(h2oFrame, yresp, enumCols=[]):
    """
    This method will convert a H2OFrame containing to a DMatrix that is can be used by native XGBoost.  The
    H2OFrame can contain numerical and enum columns.  Note that H2O one-hot-encoding introduces a missing(NA)
    column. There can be NAs in any columns.

    :param h2oFrame: H2OFrame to be converted to DMatrix
    :param yresp: string denoting the response column name
    :param enumCols: list of enum column names in the H2OFrame

    :return: DMatrix
    """
    import xgboost as xgb

    pandas = __convertH2OFrameToPandas__(h2oFrame, yresp, enumCols);

    return xgb.DMatrix(data=csr_matrix(pandas[0]), label=pandas[1])


def __convertH2OFrameToPandas__(h2oFrame, yresp, enumCols=[]):
    """
    This method will convert a H2OFrame containing to a DMatrix that is can be used by native XGBoost.  The
    H2OFrame can contain numerical and enum columns.  Note that H2O one-hot-encoding introduces a missing(NA)
    column. There can be NAs in any columns.

    :param h2oFrame: H2OFrame to be converted to DMatrix
    :param yresp: string denoting the response column name
    :param enumCols: list of enum column names in the H2OFrame

    :return: DMatrix
    """
    import xgboost as xgb

    pandaFtrain = h2oFrame.as_data_frame(use_pandas=True, header=True)
    nrows = h2oFrame.nrow

    if len(enumCols) > 0:   # start with first enum column
        pandaTrainPart = generatePandaEnumCols(pandaFtrain, enumCols[0], nrows)
        pandaFtrain.drop([enumCols[0]], axis=1, inplace=True)

        for colInd in range(1, len(enumCols)):
            cname=enumCols[colInd]
            ctemp = generatePandaEnumCols(pandaFtrain, cname,  nrows)
            pandaTrainPart=pd.concat([pandaTrainPart, ctemp], axis=1)
            pandaFtrain.drop([cname], axis=1, inplace=True)

        pandaFtrain = pd.concat([pandaTrainPart, pandaFtrain], axis=1)

    c0= h2oFrame[yresp].asnumeric().as_data_frame(use_pandas=True, header=True)
    pandaFtrain.drop([yresp], axis=1, inplace=True)
    pandaF = pd.concat([c0, pandaFtrain], axis=1)
    pandaF.rename(columns={c0.columns[0]:yresp}, inplace=True)
    newX = list(pandaFtrain.columns.values)
    data = pandaF.as_matrix(newX)
    label = pandaF.as_matrix([yresp])

    return (data,label)

def generatePandaEnumCols(pandaFtrain, cname, nrows):
    """
    For a H2O Enum column, we perform one-hot-encoding here and added one more column "missing(NA)" to it.

    :param pandaFtrain:
    :param cname:
    :param nrows:
    :return:
    """
    cmissingNames=[cname+".missing(NA)"]
    tempnp = np.zeros((nrows,1), dtype=np.int)
    # check for nan and assign it correct value
    colVals = pandaFtrain[cname]
    for ind in range(nrows):
        try:
            float(colVals[ind])
            if math.isnan(colVals[ind]):
                tempnp[ind]=1
        except ValueError:
            pass
    zeroFrame = pd.DataFrame(tempnp)
    zeroFrame.columns=cmissingNames
    temp = pd.get_dummies(pandaFtrain[cname], prefix=cname, drop_first=False)
    tempNames = list(temp)  # get column names
    colLength = len(tempNames)
    newNames = ['a']*colLength
    newIndics = [0]*colLength
    if "." in tempNames[0]:
        header = tempNames[0].split('.')[0]
        for ind in range(colLength):
            newIndics[ind] = int(tempNames[ind].split('.')[1][1:])
        newIndics.sort()
        for ind in range(colLength):
            newNames[ind] = header+'.l'+str(newIndics[ind])  # generate correct order of names
        ftemp = temp[newNames]
    else:
        ftemp = temp
    ctemp = pd.concat([ftemp, zeroFrame], axis=1)
    return ctemp

def summarizeResult_binomial(h2oPredictD, nativePred, h2oTrainTimeD, nativeTrainTime, h2oPredictTimeD,
                             nativeScoreTime, tolerance=1e-6):
    '''
    This method will summarize and compare H2OXGBoost and native XGBoost results for binomial classifiers.
    This method will summarize and compare H2OXGBoost and native XGBoost results for binomial classifiers.

    :param h2oPredictD:
    :param nativePred:
    :param h2oTrainTimeD:
    :param nativeTrainTime:
    :param h2oPredictTimeD:
    :param nativeScoreTime:
    :return:
    '''
    # Result comparison in terms of time
    print("H2OXGBoost train time is {0}s.  Native XGBoost train time is {1}s.\n  H2OXGBoost scoring time is {2}s."
          "  Native XGBoost scoring time is {3}s.".format(h2oTrainTimeD/1000.0, nativeTrainTime,
                                                          h2oPredictTimeD, nativeScoreTime))
    # Result comparison in terms of actual prediction value between the two
    colnames = h2oPredictD.names
    h2oPredictD['predict'] = h2oPredictD['predict'].asnumeric()
    h2oPredictLocalD = h2oPredictD.as_data_frame(use_pandas=True, header=True)

    # compare prediction probability and they should agree if they use the same seed
    for ind in range(h2oPredictD.nrow):
        assert abs(h2oPredictLocalD[colnames[2]][ind]-nativePred[ind])<tolerance, "H2O prediction prob: {0} and native " \
                                                                         "XGBoost prediction prob: {1}.  They are " \
                                                                         "very different.".format(h2oPredictLocalD[colnames[2]][ind], nativePred[ind])


def summarize_metrics_binomial(h2o_metrics, xgboost_metrics, names, tolerance=1e-4):
    for i in range(len(h2o_metrics)):
        difference = abs(h2o_metrics[i] - xgboost_metrics[i])
        print("H2O {0} metric: {1} and native " \
              "XGBoost {0} metric: {2}. " \
              "Difference is {3}".format(names[i], h2o_metrics[i], xgboost_metrics[i], difference))
        assert difference < tolerance, "H2O {0} metric: {1} and native " \
                                       "XGBoost {0} metric: {2}.  They are " \
                                       "very different.".format(names[i], h2o_metrics[i], xgboost_metrics[i])
    

def summarizeResult_multinomial(h2oPredictD, nativePred, h2oTrainTimeD, nativeTrainTime, h2oPredictTimeD,
                                nativeScoreTime, tolerance=1e-6):
    # Result comparison in terms of time
    print("H2OXGBoost train time is {0}s.  Native XGBoost train time is {1}s.\n  H2OGBoost scoring time is {2}s."
          "  Native XGBoost scoring time is {3}s.".format(h2oTrainTimeD/1000.0, nativeTrainTime,
                                                          h2oPredictTimeD, nativeScoreTime))
    # Result comparison in terms of actual prediction value between the two
    h2oPredictD['predict'] = h2oPredictD['predict'].asnumeric()
    h2oPredictLocalD = h2oPredictD.as_data_frame(use_pandas=True, header=True)
    nclass = len(nativePred[0])
    colnames = h2oPredictD.names

    # compare prediction probability and they should agree if they use the same seed
    for ind in range(h2oPredictD.nrow):
        for col in range(nclass):
            assert abs(h2oPredictLocalD[colnames[col+1]][ind]-nativePred[ind][col])<tolerance, \
                "H2O prediction prob: {0} and native XGBoost prediction prob: {1}.  They are very " \
                "different.".format(h2oPredictLocalD[colnames[col+1]][ind], nativePred[ind][col])

def genTrainFrame(nrow, ncol, enumCols=0, enumFactors=2, responseLevel=2, miscfrac=0, randseed=None):
    if ncol>0:
        trainFrameNumerics = random_dataset_numeric_only(nrow, ncol, integerR = 1000000, misFrac=miscfrac, randSeed=randseed)
    if enumCols > 0:
        trainFrameEnums = random_dataset_enums_only(nrow, enumCols, factorL=enumFactors, misFrac=miscfrac, randSeed=randseed)

    yresponse = random_dataset_enums_only(nrow, 1, factorL=responseLevel, misFrac=0, randSeed=randseed)
    yresponse.set_name(0,'response')
    if enumCols > 0:
        if ncol > 0:    # mixed datasets
            trainFrame = trainFrameEnums.cbind(trainFrameNumerics.cbind(yresponse))
        else:   # contains enum datasets
            trainFrame = trainFrameEnums.cbind(yresponse)
    else: # contains numerical datasets
        trainFrame = trainFrameNumerics.cbind(yresponse)
    return trainFrame

def check_xgb_var_imp(h2o_train, h2o_model, xgb_train, xgb_model, tolerance=1e-6):
    column_map = dict(zip(h2o_train.names, xgb_train.feature_names))

    h2o_var_imps = h2o_model.varimp()
    h2o_var_frequencies = h2o_model._model_json["output"]["variable_importances_frequency"].cell_values
    freq_map = dict(map(lambda t: (t[0], t[1]), h2o_var_frequencies))
    

    # XGBoost reports average gain of a split
    xgb_var_imps = xgb_model.get_score(importance_type="gain")

    for h2o_var_imp in h2o_var_imps:
        frequency = freq_map[h2o_var_imp[0]]
        xgb_var_imp = xgb_var_imps[column_map[h2o_var_imp[0]]]
        abs_diff = abs(h2o_var_imp[1]/frequency - xgb_var_imp)
        norm = max(1, abs(h2o_var_imp[1]/frequency), abs(xgb_var_imp))
        assert abs_diff/norm < tolerance, "Variable importance of feature {0} is different. H2O: {1}, XGB {2}"\
            .format(h2o_var_imp[0], h2o_var_imp[1], xgb_var_imp)

def summarizeResult_regression(h2oPredictD, nativePred, h2oTrainTimeD, nativeTrainTime, h2oPredictTimeD, nativeScoreTime, tolerance=1e-6):
    # Result comparison in terms of time
    print("H2OXGBoost train time is {0}ms.  Native XGBoost train time is {1}s.\n  H2OGBoost scoring time is {2}s."
          "  Native XGBoost scoring time is {3}s.".format(h2oTrainTimeD, nativeTrainTime,
                                                          h2oPredictTimeD, nativeScoreTime))
    # Result comparison in terms of actual prediction value between the two
    h2oPredictD['predict'] = h2oPredictD['predict'].asnumeric()
    h2oPredictLocalD = h2oPredictD.as_data_frame(use_pandas=True, header=True)


    # compare prediction probability and they should agree if they use the same seed
    for ind in range(h2oPredictD.nrow):
        assert abs((h2oPredictLocalD['predict'][ind]-nativePred[ind])/max(1, abs(h2oPredictLocalD['predict'][ind]), abs(nativePred[ind])))<tolerance, \
            "H2O prediction: {0} and native XGBoost prediction: {1}.  They are very " \
            "different.".format(h2oPredictLocalD['predict'][ind], nativePred[ind])

def summarizeResult_binomial_DS(h2oPredictD, nativePred, h2oTrainTimeD, nativeTrainTime, h2oPredictTimeD,
                                nativeScoreTime, h2oPredictS, tolerance=1e-6):
    # Result comparison in terms of time
    print("H2OXGBoost train time with sparse DMatrix is {0}s.  Native XGBoost train time with dense DMtraix is {1}s.\n  H2OGBoost scoring time is {2}s."
          "  Native XGBoost scoring time with dense DMatrix is {3}s.".format(h2oTrainTimeD/1000.0, nativeTrainTime,
                                                                             h2oPredictTimeD, nativeScoreTime))
    # Result comparison in terms of actual prediction value between the two
    h2oPredictD['predict'] = h2oPredictD['predict'].asnumeric()
    h2oPredictLocalD = h2oPredictD.as_data_frame(use_pandas=True, header=True)
    h2oPredictS['predict'] = h2oPredictS['predict'].asnumeric()
    h2oPredictLocalS = h2oPredictS.as_data_frame(use_pandas=True, header=True)

    # compare prediction probability and they should agree if they use the same seed
    for ind in range(h2oPredictD.nrow):
        assert  abs(h2oPredictLocalD['c0.l1'][ind]-nativePred[ind])<tolerance  or \
                abs(h2oPredictLocalS['c0.l1'][ind]-nativePred[ind])<tolerance, \
            "H2O prediction prob: {0} and native XGBoost prediction prob: {1}.  They are very " \
            "different.".format(h2oPredictLocalD['c0.l1'][ind], nativePred[ind])


def compare_weightedStats(model, dataframe, xlist, xname, weightV, pdpTDTable, tol=1e-6):
    '''
    This method is used to test the partial dependency plots and is not meant for any other functions.
    
    :param model:
    :param dataframe:
    :param xlist:
    :param xname:
    :param weightV:
    :param pdpTDTable:
    :param tol:
    :return:
    '''
    weightStat =  manual_partial_dependence(model, dataframe, xlist, xname, weightV) # calculate theoretical weighted sts
    wMean = extract_col_value_H2OTwoDimTable(pdpTDTable, "mean_response") # stats for age predictor
    wStd = extract_col_value_H2OTwoDimTable(pdpTDTable, "stddev_response")
    wStdErr = extract_col_value_H2OTwoDimTable(pdpTDTable, "std_error_mean_response")
    equal_two_arrays(weightStat[0], wMean, tol, tol, throw_error=True)
    equal_two_arrays(weightStat[1], wStd, tol, tol, throw_error=True)
    equal_two_arrays(weightStat[2], wStdErr, tol, tol, throw_error=True)


def manual_partial_dependence(model, dataframe, xlist, xname, weightV):
    meanV = []
    stdV = []
    stderrV = []
    nRows = dataframe.nrow
    nCols = dataframe.ncol-1

    for xval in xlist:
        cons = [xval]*nRows
        if xname in dataframe.names:
            dataframe=dataframe.drop(xname)
        if not((is_type(xval, str) and xval=='NA') or (isinstance(xval, float) and math.isnan(xval))):
            dataframe = dataframe.cbind(h2o.H2OFrame(cons))
            dataframe.set_name(nCols, xname)

        pred = model.predict(dataframe).as_data_frame(use_pandas=False, header=False)
        pIndex = len(pred[0])-1
        sumEle = 0.0
        sumEleSq = 0.0
        sumWeight = 0.0
        numNonZeroWeightCount = 0.0
        m = 1.0/math.sqrt(dataframe.nrow*1.0)
        for rindex in range(len(pred)):
            val = float(pred[rindex][pIndex]);
            weight = float(weightV[rindex][0])
            if (abs(weight) > 0) and isinstance(val, float) and not(math.isnan(val)):
                temp = val*weight
                sumEle = sumEle+temp
                sumEleSq = sumEleSq+temp*val
                sumWeight = sumWeight+weight
                numNonZeroWeightCount = numNonZeroWeightCount+1
        wMean = sumEle/sumWeight
        scale = numNonZeroWeightCount*1.0/(numNonZeroWeightCount-1)
        wSTD = math.sqrt((sumEleSq/sumWeight-wMean*wMean)*scale)
        meanV.append(wMean)
        stdV.append(wSTD)
        stderrV.append(wSTD*m)

    return meanV, stdV, stderrV

def compare_frames_equal_names(frame1, frame2):
    '''
    This method will compare two frames with same column names and column types.  The current accepted column
    types are enum, int and string.

    :param frame1:
    :param frame2:
    :return:
    '''
    cnames = frame1.names
    ctypes = frame1.types
    for cind in range(0, frame1.ncol):
        name1 = cnames[cind]
        type = str(ctypes[name1])

        if (type=="enum"):
            compare_frames_local_onecolumn_NA_enum(frame1[name1], frame2[name1], prob=1, tol=0)
        elif (type=='string'):
            compare_frames_local_onecolumn_NA_string(frame1[name1], frame2[name1], prob=1)
        else:
            compare_frames_local_onecolumn_NA(frame1[name1], frame2[name1], prob=1, tol=1e-10)

def write_H2OFrame_2_SVMLight(filename, h2oFrame):
    '''
    The function will write a h2oFrame into svmlight format and save it to a file.  However, it only supports
    column types of real/integer and nothing else
    :param filename:
    :param h2oFrame:
    :return:
    '''
    fwriteFile = open(filename, 'w')
    ncol = h2oFrame.ncol
    nrow = h2oFrame.nrow
    fdataframe = h2oFrame.as_data_frame(use_pandas=False)
    for rowindex in range(1, nrow+1):
        if len(fdataframe[rowindex][0])==0:   # special treatment for response column
            writeWords = ""    # convert na response to 0.0
        else:
            writeWords = fdataframe[rowindex][0]

        for colindex in range(1, ncol):
            if not(len(fdataframe[rowindex][colindex])==0):
                writeWords = writeWords + " "+str(colindex) + ":"+fdataframe[rowindex][colindex]
        fwriteFile.write(writeWords)
        fwriteFile.write('\n')
    fwriteFile.close()

def write_H2OFrame_2_ARFF(filenameWithPath, filename, h2oFrame, uuidVecs, uuidNames):
    '''
    This function will write a H2OFrame into arff format and save it to a text file in ARFF format.
    :param filename:
    :param h2oFrame:
    :return:
    '''

    fwriteFile = open(filenameWithPath, 'w')
    nrow = h2oFrame.nrow

    # write the arff headers here
    writeWords = "@RELATION "+filename+'\n\n'
    fwriteFile.write(writeWords)

    typesDict = h2oFrame.types
    colnames = h2oFrame.names
    uuidtypes = len(uuidNames)*["UUID"]

    for cname in colnames:
        writeWords = "@ATTRIBUTE "+cname

        if typesDict[cname]==u'int':
            writeWords = writeWords + " integer"
        elif typesDict[cname]==u'time':
            writeWords = writeWords + " date"
        else:
            writeWords = writeWords + " "+typesDict[cname]
        fwriteFile.write(writeWords)
        fwriteFile.write('\n')

    for cindex in range(len(uuidNames)):
        writeWords = "@ATTRIBUTE " +uuidNames[cindex]+" uuid"
        fwriteFile.write(writeWords)
        fwriteFile.write('\n')
    fwriteFile.write("\n@DATA\n")

    # write the arff body as csv
    fdataframe = h2oFrame.as_data_frame(use_pandas=False)

    for rowindex in range(1,nrow+1):
        writeWords = ""
        for cindex in range(h2oFrame.ncol):
            if len(fdataframe[rowindex][cindex])>0:
                if typesDict[colnames[cindex]]==u'time':
                    writeWords = writeWords+\
                                 str(datetime.datetime.fromtimestamp(float(fdataframe[rowindex][cindex])/1000.0))+","
                elif typesDict[colnames[cindex]] in [u'enum', u'string']:
                    writeWords=writeWords+fdataframe[rowindex][cindex]+","
                else:
                    writeWords=writeWords+fdataframe[rowindex][cindex]+","
            else:
                writeWords = writeWords + ","

        # process the uuid ones
        for cindex in range(len(uuidVecs)-1):
            writeWords=writeWords+str(uuidVecs[cindex][rowindex-1])+","
        writeWords=writeWords+str(uuidVecs[-1][rowindex-1])+'\n'
        fwriteFile.write(writeWords)
    fwriteFile.close()

def checkCorrectSkips(originalFullFrame, csvfile, skipped_columns):
    skippedFrameUF = h2o.upload_file(csvfile, skipped_columns=skipped_columns)
    skippedFrameIF = h2o.import_file(csvfile, skipped_columns=skipped_columns)  # this two frames should be the same
    compare_frames_local(skippedFrameUF, skippedFrameIF, prob=0.5)

    skipCounter = 0
    typeDict = originalFullFrame.types
    frameNames = originalFullFrame.names
    for cindex in range(len(frameNames)):
        if cindex not in skipped_columns:
            print("Checking column {0}...".format(cindex))
            if typeDict[frameNames[cindex]] == u'enum':
                compare_frames_local_onecolumn_NA_enum(originalFullFrame[cindex],
                                                                    skippedFrameIF[skipCounter], prob=1, tol=1e-10,
                                                                    returnResult=False)
            elif typeDict[frameNames[cindex]] == u'string':
                compare_frames_local_onecolumn_NA_string(originalFullFrame[cindex],
                                                                      skippedFrameIF[skipCounter], prob=1,
                                                                      returnResult=False)
            else:
                compare_frames_local_onecolumn_NA(originalFullFrame[cindex], skippedFrameIF[skipCounter],
                                                               prob=1, tol=1e-10, returnResult=False)
            skipCounter = skipCounter + 1


def checkCorrectSkipsFolder(originalFullFrame, csvfile, skipped_columns):
    skippedFrameIF = h2o.import_file(csvfile, skipped_columns=skipped_columns)  # this two frames should be the same
    skipCounter = 0
    typeDict = originalFullFrame.types
    frameNames = originalFullFrame.names
    for cindex in range(len(frameNames)):
        if cindex not in skipped_columns:
            print("Checking column {0}...".format(cindex))
            if typeDict[frameNames[cindex]] == u'enum':
                compare_frames_local_onecolumn_NA_enum(originalFullFrame[cindex],
                                                                    skippedFrameIF[skipCounter], prob=1, tol=1e-10,
                                                                    returnResult=False)
            elif typeDict[frameNames[cindex]] == u'string':
                compare_frames_local_onecolumn_NA_string(originalFullFrame[cindex],
                                                                      skippedFrameIF[skipCounter], prob=1,
                                                                      returnResult=False)
            else:
                compare_frames_local_onecolumn_NA(originalFullFrame[cindex], skippedFrameIF[skipCounter],
                                                               prob=1, tol=1e-10, returnResult=False)
            skipCounter = skipCounter + 1

def assertModelColNamesTypesCorrect(modelNames, modelTypes, frameNames, frameTypesDict):
    fName = list(frameNames)
    mName = list(modelNames)
    assert fName.sort() == mName.sort(), "Expected column names {0}, actual column names {1} and they" \
                                                    " are different".format(frameNames, modelNames) 
    for ind in range(len(frameNames)):  
        if modelTypes[modelNames.index(frameNames[ind])].lower()=="numeric":
            assert (frameTypesDict[frameNames[ind]].lower()=='real') or \
                   (frameTypesDict[frameNames[ind]].lower()=='int'), \
                "Expected training data types for column {0} is {1}.  Actual training data types for column {2} from " \
                "model output is {3}".format(frameNames[ind], frameTypesDict[frameNames[ind]],
                                             frameNames[ind], modelTypes[modelNames.index(frameNames[ind])])
        else:
            assert modelTypes[modelNames.index(frameNames[ind])].lower()==frameTypesDict[frameNames[ind]].lower(), \
            "Expected training data types for column {0} is {1}.  Actual training data types for column {2} from " \
            "model output is {3}".format(frameNames[ind], frameTypesDict[frameNames[ind]],
                                         frameNames[ind], modelTypes[modelNames.index(frameNames[ind])])


def saveModelMojo(model):
    '''
    Given a H2O model, this function will save it in a directory off the results directory.  In addition, it will
    return the absolute path of where the mojo file is.
    
    :param model: 
    :return: 
    '''
    # save model
    regex = re.compile("[+\\-* !@#$%^&()={}\\[\\]|;:'\"<>,.?/]")
    MOJONAME = regex.sub("_", model._id)

    print("Downloading Java prediction model code from H2O")
    tmpdir = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", MOJONAME))
    os.makedirs(tmpdir)
    model.download_mojo(path=tmpdir)    # save mojo
    return tmpdir

# This file will contain functions used by GLM test only.
def assertEqualRegPaths(keys, pathList, index, onePath, tol=1e-6):
    for oneKey in keys:
        if (pathList[oneKey] != None):
            assert abs(pathList[oneKey][index]-onePath[oneKey][0]) < tol, \
                "Expected value: {0}, Actual: {1}".format(pathList[oneKey][index], onePath[oneKey][0])



def assertEqualCoeffDicts(coef1Dict, coef2Dict, tol = 1e-6):
    assert len(coef1Dict) == len(coef2Dict), "Length of first coefficient dict: {0}, length of second coefficient " \
                                             "dict: {1} and they are different.".format(len(coef1Dict, len(coef2Dict)))
    for key in coef1Dict:
        val1 = coef1Dict[key]
        val2 = coef2Dict[key]
        if (math.isnan(val1)):
            assert math.isnan(val2), "Coefficient for {0} from first dict: {1}, from second dict: {2} are different." \
                                     "".format(key, coef1Dict[key], coef2Dict[key])
        elif (math.isinf(val1)):
            assert math.isinf(val2), "Coefficient for {0} from first dict: {1}, from second dict: {2} are different." \
                                     "".format(key, coef1Dict[key], coef2Dict[key])
        else:
            assert abs(coef1Dict[key] - coef2Dict[key]) < tol, "Coefficient for {0} from first dict: {1}, from second" \
                                                               " dict: {2} and they are different.".format(key,
                                                                                                           coef1Dict[
                                                                                                               key],
                                                                                                           coef2Dict[
                                                                                                               key])

def assertEqualModelMetrics(metrics1, metrics2, tol = 1e-6,
                            keySet=["MSE", "AUC", "Gini", "null_deviance", "logloss", "RMSE",
                                    "pr_auc", "r2"]):
    # 1. Check model types
    model1_type = metrics1.__class__.__name__
    model2_type = metrics2.__class__.__name__
    assert model1_type is model2_type, "The model types differ. The first model metric is of type {0} and the second " \
                                       "model metric is of type {1}.".format(model1_type, model2_type)

    metricDict1 = metrics1._metric_json
    metricDict2 = metrics2._metric_json

    for key in keySet:
        if key in metricDict1.keys() and (isinstance(metricDict1[key], float)): # only compare floating point metrics
            assert abs(metricDict1[key]-metricDict2[key])/max(1,max(metricDict1[key],metricDict2[key])) < tol, \
                "ModelMetric {0} from model 1,  {1} from model 2 are different.".format(metricDict1[key],metricDict2[key])

# When an array of alpha and/or lambdas are given, a list of submodels are also built.  For each submodel built, only
# the coefficients, lambda/alpha/deviance values are returned.  The model metrics is calculated from the submodel
# with the best deviance.  
#
# In this test, in addition, we build separate models using just one lambda and one alpha values as when building one
# submodel.  In theory, the coefficients obtained from the separate models should equal to the submodels.  We check 
# and compare the followings:
# 1. coefficients from submodels and individual model should match when they are using the same alpha/lambda value;
# 2. training metrics from alpha array should equal to the individual model matching the alpha/lambda value;
def compareSubmodelsNindividualModels(modelWithArray, trainingData, xarray, yindex):
    best_submodel_index = modelWithArray._model_json["output"]["best_submodel_index"]
    r = H2OGeneralizedLinearEstimator.getGLMRegularizationPath(modelWithArray)  # contains all lambda/alpha values of submodels trained.
    submodel_num = len(r["lambdas"])
    regKeys = ["alphas", "lambdas", "explained_deviance_valid", "explained_deviance_train"]
    for submodIndx in range(submodel_num):  # manually build glm model and compare to those built before
        modelGLM = H2OGeneralizedLinearEstimator(family='binomial', alpha=[r["alphas"][submodIndx]], Lambda=[r["lambdas"][submodIndx]])
        modelGLM.train(training_frame=trainingData, x=xarray, y=yindex)
        # check coefficients between submodels and model trained with same parameters
        assertEqualCoeffDicts(r["coefficients"][submodIndx], modelGLM.coef())
        modelGLMr = H2OGeneralizedLinearEstimator.getGLMRegularizationPath(modelGLM) # contains one item only
        assertEqualRegPaths(regKeys, r, submodIndx, modelGLMr)
        if (best_submodel_index == submodIndx):  # check training metrics of modelGLM should equal that of m since it is the best subModel
            assertEqualModelMetrics(modelWithArray._model_json["output"]["training_metrics"],
                                    modelGLM._model_json["output"]["training_metrics"])
            assertEqualCoeffDicts(modelWithArray.coef(), modelGLM.coef()) # model coefficient should come from best submodel
        else:  # check and make sure best_submodel_index has lowest deviance
            assert modelGLM.residual_deviance() - modelWithArray.residual_deviance() >= 0, \
                "Individual model has better residual_deviance than best submodel!"

def extractNextCoeff(cs_norm, orderedCoeffNames, startVal):
    for ind in range(0, len(startVal)):
        startVal[ind] = cs_norm[orderedCoeffNames[ind]]
    return startVal

def assertEqualScoringHistoryIteration(model_long, model_short, col_list_compare, tolerance=1e-6):
    scoring_history_long = model_long._model_json["output"]["scoring_history"]
    scoring_history_short = model_short._model_json["output"]["scoring_history"]
    cv_4th_len = len(scoring_history_short.cell_values) - 1 # ignore last iteration, scoring is performed at different spots
    cv_len = len(scoring_history_long.cell_values)
    col_2D = scoring_history_short.col_header
    iterInd = col_2D.index('iterations')
    count = 0
    for index in range(cv_4th_len):
        iterInd4th = scoring_history_short.cell_values[index][iterInd]
        iterIndlong = scoring_history_long.cell_values[count][iterInd]
        while not(iterInd4th == None) and (iterInd4th > iterIndlong):
            count = count+1
            if count >= cv_len:
                break
            iterIndlong = scoring_history_long.cell_values[count][iterInd]

        if not(iterInd4th == None) and not(iterInd4th == '') and (iterInd4th == iterIndlong):
            for col_header in col_list_compare:
                ind = col_2D.index(col_header)
                val_short = scoring_history_short.cell_values[index][ind]
                val_long = scoring_history_long.cell_values[count][ind]
                if not(val_short == '' or math.isnan(val_short) or val_long == '' or math.isnan(val_long)):
                    assert abs(scoring_history_short.cell_values[index][ind]-
                               scoring_history_long.cell_values[count][ind]) < tolerance, \
                        "{0} expected: {1}, actual: {2}".format(col_header, scoring_history_short.cell_values[index][ind],
                                                                scoring_history_long.cell_values[count][ind])
        count = count+1


def assertCoefEqual(regCoeff, coeff, coeffClassSet, tol=1e-6):
    for key in regCoeff:
        temp = key.split('_')
        classInd = int(temp[1])
        val1 = regCoeff[key]
        val2 = coeff[coeffClassSet[classInd]][temp[0]]
        assert type(val1)==type(val2), "type of coeff1: {0}, type of coeff2: {1}".format(type(val1), type(val2))
        diff = abs(val1-val2)
        print("val1: {0}, val2: {1}, tol: {2}".format(val1, val2, tol))
        assert diff < tol, "diff {0} exceeds tolerance {1}.".format(diff, tol)


def assertCoefDictEqual(regCoeff, coeff, tol=1e-6):
    for key in regCoeff:
        val1 = regCoeff[key]
        val2 = coeff[key]
        assert type(val1)==type(val2), "type of coeff1: {0}, type of coeff2: {1}".format(type(val1), type(val2))
        diff = abs(val1-val2)
        assert diff < tol, "diff {0} exceeds tolerance {1}.".format(diff, tol)


def assert_equals(expected, actual, message=""):
    assert expected == actual, ("{0}\nexpected:{1}\nactual\t:{2}".format(message, expected, actual))
