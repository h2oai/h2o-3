from __future__ import print_function
from builtins import range
import math
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def random_grid_model_seeds_failing_case():
    air_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"),
                              destination_frame="air.hex")
    myX = ["Year","Month","CRSDepTime","UniqueCarrier","Origin","Dest"]

    # ignoredCols = ['CRSElapsedTime', 'CarrierDelay', 'DepDelay', 'DayofMonth', 'Distance', 'LateAircraftDelay', 'AirTime',
    #                'DayOfWeek', 'SecurityDelay', 'ArrTime', 'TaxiOut', 'CancellationCode', 'IsArrDelayed', 'TailNum',
    #                'TaxiIn', 'WeatherDelay', 'NASDelay', 'ActualElapsedTime', 'FlightNum', 'Diverted', 'CRSArrTime', 'DepTime', 'Cancelled', 'ArrDelay']

    gbm1 = H2OGradientBoostingEstimator(
        nfolds = 0,
        keep_cross_validation_models = True,
        keep_cross_validation_predictions = False,
        keep_cross_validation_fold_assignment = False,
        score_each_iteration = False,
        score_tree_interval = 1,
        # response_column = 'isDepDelayed',
        # ignored_columns = ignoredCols,
        ignore_const_cols = True,
        balance_classes = False,
        max_after_balance_size = 5.0,
        max_confusion_matrix_size = 20,
        max_hit_ratio_k = 0,
        ntrees = 50,
        max_depth = 10,
        min_rows = 2.0,
        nbins = 16,
        nbins_top_level = 1024,
        nbins_cats = 64,
        r2_stopping = 1.7976931348623157e+308,
        stopping_rounds = 0,
        stopping_tolerance = 0.001,
        max_runtime_secs = 1.7976931348623157e+308,
        #check why 1249 and not 1242
        seed = 1249,
        build_tree_one_node = False,
        learn_rate = 0.1,
        learn_rate_annealing = 1.0,
        distribution = 'bernoulli',
        quantile_alpha = 0.5,
        tweedie_power = 1.5,
        huber_alpha = 0.9,
        sample_rate = 0.75,
        col_sample_rate = 0.31,
        col_sample_rate_change_per_level = 0.94,
        col_sample_rate_per_tree = 0.65,
        min_split_improvement = 0.0001,
        histogram_type = 'QuantilesGlobal',
        max_abs_leafnode_pred = 1.7976931348623157e+308,
        pred_noise_bandwidth = 0.0,
        calibrate_model = False,
        check_constant_response = True,
        #      evaluate_auto = False#True
    )
    gbm1.train(x=myX, y="IsDepDelayed", training_frame=air_hex)

    gbm2 = H2OGradientBoostingEstimator(
        nfolds = 0,
        keep_cross_validation_models = True,
        keep_cross_validation_predictions = False,
        keep_cross_validation_fold_assignment = False,
        score_each_iteration = False,
        score_tree_interval = 1,
        # response_column = 'isDepDelayed',
        #ignored_columns = ignoredCols,
        ignore_const_cols = True,
        balance_classes = False,
        max_after_balance_size = 5.0,
        max_confusion_matrix_size = 20,
        max_hit_ratio_k = 0,
        ntrees = 50,
        max_depth = 10,
        min_rows = 2.0,
        nbins = 16,
        nbins_top_level = 1024,
        nbins_cats = 64,
        r2_stopping = 1.7976931348623157e+308,
        stopping_rounds = 0,
        stopping_tolerance = 0.001,
        max_runtime_secs = 1.7976931348623157e+308,
        #check why 1249 and not 1242
        seed = 1249,
        build_tree_one_node = False,
        learn_rate = 0.1,
        learn_rate_annealing = 1.0,
        distribution = 'bernoulli',
        quantile_alpha = 0.5,
        tweedie_power = 1.5,
        huber_alpha = 0.9,
        sample_rate = 0.75,
        col_sample_rate = 0.31,
        col_sample_rate_change_per_level = 0.94,
        col_sample_rate_per_tree = 0.65,
        min_split_improvement = 0.0001,
        histogram_type = 'QuantilesGlobal',
        max_abs_leafnode_pred = 1.7976931348623157e+308,
        pred_noise_bandwidth = 0.0,
        calibrate_model = False,
        check_constant_response = True,
        # evaluate_auto = False#True
    )
    gbm2.train(x=myX, y="IsDepDelayed", training_frame=air_hex)

    rmse1 = extract_from_twoDimTable(gbm1._model_json["output"]["scoring_history"], "training_rmse", False)
    rmse2 = extract_from_twoDimTable(gbm2._model_json["output"]["scoring_history"], "training_rmse", False)
    print(rmse1)
    print(rmse2)

    assert equal_two_arrays(rmse1, rmse2, 1e-5, 1e-6, False), \
        "Training_rmse are different between the two grid search models.  Tests are supposed to be repeatable in " \
        "this case.  Make sure model seeds are actually set correctly in the Java backend."

def equal_two_arrays(array1, array2, eps, tolerance, throwError=True):
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
                    if throwError:
                        assert False, "Array 1 value {0} and array 2 value {1} do not agree.".format(array1[ind], array2[ind])
                    else:
                        return False

        return True                                     # return True, elements of two arrays are close enough
    else:
        if throwError:
            assert False, "The two arrays are of different size!"
        else:
            return False

def extract_from_twoDimTable(metricOfInterest, fieldOfInterest, takeFirst=False):
    """
    Given a fieldOfInterest that are found in the model scoring history, this function will extract the list
    of field values for you from the model.

    :param aModel: H2O model where you want to extract a list of fields from the scoring history
    :param fieldOfInterest: string representing a field of interest.
    :return: List of field values or None if it cannot be found
    """

    allFields = metricOfInterest._col_header
    if fieldOfInterest in allFields:
        cellValues = []
        fieldIndex = allFields.index(fieldOfInterest)
        for eachCell in metricOfInterest.cell_values:
            cellValues.append(eachCell[fieldIndex])
            if takeFirst:   # only grab the result from the first iteration.
                break
        return cellValues
    else:
        return None

def extract_scoring_history_field(aModel, fieldOfInterest, takeFirst=False):
    """
    Given a fieldOfInterest that are found in the model scoring history, this function will extract the list
    of field values for you from the model.

    :param aModel: H2O model where you want to extract a list of fields from the scoring history
    :param fieldOfInterest: string representing a field of interest.
    :return: List of field values or None if it cannot be found
    """
    return extract_from_twoDimTable(aModel._model_json["output"]["scoring_history"], fieldOfInterest, takeFirst)


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
    print("model seed list before sort: ")
    model_seed_list_string = ','.join(str(x) for x in model_seed_list[0:model_num])
    print(model_seed_list_string)
    model_seed_list.sort()
    print("model seed list after sort: ")
    model_seed_list_string = ','.join(str(x) for x in model_seed_list[0:model_num])
    print(model_seed_list_string)
    return model_seed_list


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

if __name__ == "__main__":
    standalone_test(random_grid_model_seeds_failing_case)
else:
    random_grid_model_seeds_failing_case()
