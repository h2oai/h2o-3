from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
import math
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

# global dictionaries storing model answers
g_iris_setosa_sepal_len=dict()
g_iris_versicolor_sepal_wid=dict()
g_iris_virginica_petal_wid=dict()
g_iris_versicolor_petal_len_NA_ignore=dict()
g_iris_versicolor_petal_len_NA_rm=dict()

def group_by_all():
    """
    This is a comprehenisve test that will test all aggregations in the groupBy class.
    """
    generate_dict_answers() # generate answer dictionary

    # perform group-by with datasets containing no NAs. All three na mode should produce same results
    h2o_iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    result_all = perform_group_by(h2o_iris,'all')
    result_ignore = perform_group_by(h2o_iris,'ignore')
    result_rm = perform_group_by(h2o_iris, 'rm')

    # make sure return type of get_frame() is H2OFrame
    assert_is_type(result_all, H2OFrame)
    assert_is_type(result_ignore, H2OFrame)
    assert_is_type(result_rm, H2OFrame)

    # make sure the result frame contains the correct number of rows and columns
    assert result_all.shape==result_ignore.shape==result_rm.shape==(3,30), "H2O group_by() command is not working."

    # check all group by results are the same
    assert pyunit_utils.compare_frames(result_all, result_ignore, 0, 0, 1e-6, strict=True, compare_NA=False), \
        "H2O group_by() command is not working."
    assert pyunit_utils.compare_frames(result_ignore, result_rm, 0, 0, 1e-6, strict=True, compare_NA=False), \
        "H2O group_by() command is not working."

    # check group by result with known correct result
    assert_group_by_result(result_all, g_iris_setosa_sepal_len, "Iris-setosa")
    assert_group_by_result(result_rm, g_iris_versicolor_sepal_wid, "Iris-versicolor")
    assert_group_by_result(result_ignore, g_iris_virginica_petal_wid, "Iris-virginica")

    # perform group-by with datasets contain NAs.
    h2o_iris_NA = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader_NA_2.csv"))
    result_all_NA = perform_group_by(h2o_iris_NA,'all')
    result_ignore_NA = perform_group_by(h2o_iris_NA,'ignore')
    result_rm_NA = perform_group_by(h2o_iris_NA, 'rm')

    # make sure return type of get_frame() is H2OFrame
    assert_is_type(result_all_NA, H2OFrame)
    assert_is_type(result_ignore_NA, H2OFrame)
    assert_is_type(result_rm_NA, H2OFrame)

    # make sure the result frame contains the correct number of rows and columns
    assert result_all_NA.shape==result_ignore_NA.shape==result_rm_NA.shape==(3,30), \
        "H2O group_by() command is not working."

    # column petal_wid contains no NA and hence should provide same result as before independent of NA treatment
    assert pyunit_utils.compare_frames(result_all_NA[list(g_iris_virginica_petal_wid.keys())],
                                       result_rm_NA[list(g_iris_virginica_petal_wid.keys())], 0, 0, 1e-6,
                                       strict=False, compare_NA=False), "H2O group_by() command is not working."
    assert pyunit_utils.compare_frames(result_all_NA[list(g_iris_virginica_petal_wid.keys())],
                                       result_ignore_NA[list(g_iris_virginica_petal_wid.keys())], 0, 0, 1e-6,
                                       strict=False, compare_NA=False), "H2O group_by() command is not working."
    assert_group_by_result(result_all_NA, g_iris_virginica_petal_wid, "Iris-virginica")

    # check to make sure result_all_NA columns for sepal_len, sepal_wid, petal_len are all NAs for na='all'
    assert_all_NAs(result_all_NA, list(g_iris_setosa_sepal_len.keys()))  # check sepal_len
    assert_all_NAs(result_all_NA, list(g_iris_versicolor_sepal_wid.keys()))  # check sepal_wid
    assert_all_NAs(result_all_NA, list(g_iris_versicolor_petal_len_NA_ignore.keys()))  # check petal_len

    # check to make sure na="ignore", and na="rm" are calculated correctly against known answers
    assert_group_by_result(result_ignore_NA, g_iris_versicolor_petal_len_NA_ignore, "Iris-versicolor")
    assert_group_by_result(result_rm_NA, g_iris_versicolor_petal_len_NA_rm, "Iris-versicolor")

def assert_all_NAs(h2oframe, col_names):
    """
    Throw an assert error if not all columns of h2oframe with column names specified in list col_names are NAs.

    :param h2oframe:
    :param col_names:
    """
    for column_name in col_names:
        assert h2oframe[column_name].all(), "H2O group_by() command is not working."


def perform_group_by(h2oFrame, na):
    """
    Given a H2OFrame h2oFrame, and the na treatment, perform chained group by aggregation and return the
    results of aggregations in an H2OFrame.

    :param h2oFrame:
    :param na:
    :return:
    """
    grouped = h2oFrame.group_by("class")
    grouped.count(na=na).min(na=na).max(na=na).mean(na=na).var(na=na).sd(na=na).ss(na=na).sum(na=na)
    print(grouped.get_frame())
    return grouped.get_frame()

def assert_group_by_result(h2oFrame, answer_dict, row_name):
    """
    Given a result frame h2oFrame, a dictionary containing the answers to the group by operation and
    row_name denoting which groupby group to examine, this method will throw an error if result
    frame does not agree with dict values or the wrong group name is provided.

    :param h2oFrame:
    :param answer_dict:
    :param row_name:
    """
    row_ind = -1
    for ind in range(h2oFrame.nrow):
        if row_name == h2oFrame[ind, 0]:
            row_ind=ind
            break
    assert row_ind>=0, "row_name is not a valid row name in your result frame."

    for key, value in answer_dict.items():
        assert abs(value-h2oFrame[row_ind, key]) < 1e-10, "H2O group_by() command is not working."

def generate_dict_answers():
    """
    Generates dictionary containing answers that I have pre-calculated for iris dataset.
    """
    global g_iris_setosa_sepal_len
    global g_iris_versicolor_sepal_wid
    global g_iris_virginica_petal_wid
    global g_iris_versicolor_petal_len_NA_ignore
    global g_iris_versicolor_petal_len_NA_rm

    # collect pre-calculated information
    g_iris_setosa_sepal_len["mean_sepal_len"]=5.006
    g_iris_setosa_sepal_len["sum_sepal_len"]=250.3
    g_iris_setosa_sepal_len["sumSquares_sepal_len"]=1259.09
    g_iris_setosa_sepal_len["max_sepal_len"]=5.8
    g_iris_setosa_sepal_len["min_sepal_len"]=4.3
    g_iris_setosa_sepal_len["var_sepal_len"]=(g_iris_setosa_sepal_len["sumSquares_sepal_len"]-
                                              2*g_iris_setosa_sepal_len["mean_sepal_len"]*g_iris_setosa_sepal_len["sum_sepal_len"]+
                                              50*g_iris_setosa_sepal_len["mean_sepal_len"]*g_iris_setosa_sepal_len["mean_sepal_len"])/49.0
    g_iris_setosa_sepal_len["sdev_sepal_len"]=math.sqrt(g_iris_setosa_sepal_len["var_sepal_len"])

    g_iris_versicolor_sepal_wid["mean_sepal_wid"]=2.77
    g_iris_versicolor_sepal_wid["sum_sepal_wid"]=138.5
    g_iris_versicolor_sepal_wid["sumSquares_sepal_wid"]=388.47
    g_iris_versicolor_sepal_wid["max_sepal_wid"]=3.4
    g_iris_versicolor_sepal_wid["min_sepal_wid"]=2.0
    g_iris_versicolor_sepal_wid["var_sepal_wid"]=(g_iris_versicolor_sepal_wid["sumSquares_sepal_wid"]-
                                                  2*g_iris_versicolor_sepal_wid["mean_sepal_wid"]*g_iris_versicolor_sepal_wid["sum_sepal_wid"]+
                                                  50*g_iris_versicolor_sepal_wid["mean_sepal_wid"]*g_iris_versicolor_sepal_wid["mean_sepal_wid"])/49.0
    g_iris_versicolor_sepal_wid["sdev_sepal_wid"]=math.sqrt(g_iris_versicolor_sepal_wid["var_sepal_wid"])

    g_iris_virginica_petal_wid["mean_petal_wid"]=2.026
    g_iris_virginica_petal_wid["sum_petal_wid"]=101.3
    g_iris_virginica_petal_wid["sumSquares_petal_wid"]=208.93
    g_iris_virginica_petal_wid["max_petal_wid"]=2.5
    g_iris_virginica_petal_wid["min_petal_wid"]=1.4
    g_iris_virginica_petal_wid["var_petal_wid"]=(g_iris_virginica_petal_wid["sumSquares_petal_wid"]-
                                                 2*g_iris_virginica_petal_wid["mean_petal_wid"]*g_iris_virginica_petal_wid["sum_petal_wid"]+
                                                 50*g_iris_virginica_petal_wid["mean_petal_wid"]*g_iris_virginica_petal_wid["mean_petal_wid"])/49.0
    g_iris_virginica_petal_wid["sdev_petal_wid"]=math.sqrt(g_iris_virginica_petal_wid["var_petal_wid"])


    g_iris_versicolor_petal_len_NA_ignore["sum_petal_len"]=204.5
    g_iris_versicolor_petal_len_NA_ignore["sumSquares_petal_len"]=881.95
    g_iris_versicolor_petal_len_NA_ignore["mean_petal_len"]=g_iris_versicolor_petal_len_NA_ignore["sum_petal_len"]/50.0
    g_iris_versicolor_petal_len_NA_ignore["max_petal_len"]=5.1
    g_iris_versicolor_petal_len_NA_ignore["min_petal_len"]=3.0
    g_iris_versicolor_petal_len_NA_ignore["var_petal_len"]=(g_iris_versicolor_petal_len_NA_ignore["sumSquares_petal_len"]-
                                                            2*g_iris_versicolor_petal_len_NA_ignore["mean_petal_len"]*g_iris_versicolor_petal_len_NA_ignore["sum_petal_len"]+
                                                            50*g_iris_versicolor_petal_len_NA_ignore["mean_petal_len"]*g_iris_versicolor_petal_len_NA_ignore["mean_petal_len"])/49.0
    g_iris_versicolor_petal_len_NA_ignore["sdev_petal_len"]=math.sqrt(g_iris_versicolor_petal_len_NA_ignore["var_petal_len"])

    g_iris_versicolor_petal_len_NA_rm["sum_petal_len"]=204.5
    g_iris_versicolor_petal_len_NA_rm["sumSquares_petal_len"]=881.95
    g_iris_versicolor_petal_len_NA_rm["mean_petal_len"]=g_iris_versicolor_petal_len_NA_rm["sum_petal_len"]/48.0
    g_iris_versicolor_petal_len_NA_rm["max_petal_len"]=5.1
    g_iris_versicolor_petal_len_NA_rm["min_petal_len"]=3.0
    g_iris_versicolor_petal_len_NA_rm["var_petal_len"]=(g_iris_versicolor_petal_len_NA_rm["sumSquares_petal_len"]-
                                                        2*g_iris_versicolor_petal_len_NA_rm["mean_petal_len"]*g_iris_versicolor_petal_len_NA_rm["sum_petal_len"]+
                                                        48*g_iris_versicolor_petal_len_NA_rm["mean_petal_len"]*g_iris_versicolor_petal_len_NA_rm["mean_petal_len"])/47.0
    g_iris_versicolor_petal_len_NA_rm["sdev_petal_len"]=math.sqrt(g_iris_versicolor_petal_len_NA_rm["var_petal_len"])


if __name__ == "__main__":
    pyunit_utils.standalone_test(group_by_all)
else:
    group_by_all()
