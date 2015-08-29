import imp
import random
import sys
sys.path.insert(1, "../../")
from h2o import H2OBinomialModel, H2ORegressionModel, H2OMultinomialModel, H2OClusteringModel, H2OFrame

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
    model1_type = type(model1)
    model2_type = type(model2)
    assert model1_type == model2_type, "The model types differ. The first model is of type {0} and the second " \
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
        for r in range(rows):
            for c in range(cols):
                pval = python_obj[r][c] if rows > 1 else python_obj[c]
                hval = h2o_frame[r,c]
                assert pval == hval, "expected H2OFrame to have the same values as the python object for row {0} and column " \
                                     "{1}, but h2o got {2} and python got {3}.".format(r, c, hval, pval)
    elif isinstance(python_obj, dict):
        for r in range(rows):
            for k in python_obj.keys():
                pval = python_obj[k][r] if hasattr(python_obj[k],'__iter__') else python_obj[k]
                hval = h2o_frame[r,k]
                assert pval == hval, "expected H2OFrame to have the same values as the python object for row {0} and column " \
                                     "{1}, but h2o got {2} and python got {3}.".format(r, k, hval, pval)

def np_comparison_check(h2o_data, np_data, num_elements):
    """
    Check values achieved by h2o against values achieved by numpy

    :param h2o_data: an H2OFrame
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
        h2o_val = h2o_data[r,c] if isinstance(h2o_data,H2OFrame) else h2o_data[r]
        np_val = np_data[r,c] if len(np_data.shape) > 1 else np_data[r]
        if isinstance(np_val, np.bool_): np_val = bool(np_val)  # numpy haz special bool type :(
        assert np.absolute(h2o_val - np_val) < 1e-6, \
            "failed comparison check! h2o computed {0} and numpy computed {1}".format(h2o_val, np_val)