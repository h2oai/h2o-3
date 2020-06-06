import h2o
from tests import pyunit_utils
from h2o.frame import H2OFrame
from h2o.estimators.aggregator import H2OAggregatorEstimator
from tests import pyunit_utils


def test_aggregator_get_mapping_frame():
    winequality_df = h2o.import_file(pyunit_utils.locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))

    params = {
        "target_num_exemplars": 650,
        "rel_tol_num_exemplars": 0.25,
        "save_mapping_frame": True
    }
    agg = H2OAggregatorEstimator(ignored_columns=["quality", "type"], **params)
    agg.train(training_frame=winequality_df)

    mapping_frame = agg.mapping_frame

    assert mapping_frame.names == ["exemplar_assignment"]
    assert mapping_frame.nrows == winequality_df.nrows 


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_aggregator_get_mapping_frame)
else:
    test_aggregator_get_mapping_frame()
