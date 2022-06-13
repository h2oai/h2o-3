import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from pandas.testing import assert_frame_equal
from h2o.estimators.isolation_forest import H2OIsolationForestEstimator


def test_if_train_segments():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()

    # model will be built for each segment
    segment_col = "RACE"
    # segment 0 is too small, will not produce a model
    bad_segment = 0

    segments = prostate[segment_col].unique()
    segments.rename({'C1': segment_col})

    params = {
        "min_rows": 2,
        "ntrees": 4,
        "seed": 42,
        "sample_size": 10
    }
    prostate_if = H2OIsolationForestEstimator(**params)
    models = prostate_if.train_segments(ignored_columns=["ID"], training_frame=prostate,
                                        segments=segments)
    models_list = models.as_frame()
    print(models_list)
    
    assert models_list.names == [u'RACE', u'model', u'status', u'errors', u'warnings']
    assert models_list.nrow == 3
    
    # Check failed models
    expected_error = 'ERRR on field: _min_rows: The dataset size is too small to split for min_rows=2.0: ' \
                     'must have at least 4.0 (weighted) rows, but have only 3.0.\n'
    assert (models_list["errors"][models_list[segment_col] == bad_segment]).as_data_frame()["errors"][0] == expected_error

    mp = models_list.as_data_frame()
    # Check built models
    for i in range(mp.shape[0]):
        segment = int(mp.iloc[i][segment_col])
        status = str(mp.iloc[i]["status"])
        if segment != bad_segment:
            assert status == "SUCCEEDED"
            model_id = mp.iloc[i]["model"]
            model = h2o.get_model(model_id)
            prostate_segment = prostate[prostate[segment_col] == segment]
            prostate_if_segment = H2OIsolationForestEstimator(**params)
            prostate_if_segment.train(ignored_columns=["ID", segment_col], training_frame=prostate_segment)
            pyunit_utils.check_models(model, prostate_if_segment)
            preds_actual = model.predict(prostate_segment)
            preds_expected = prostate_if_segment.predict(prostate_segment)
            assert_frame_equal(preds_actual.as_data_frame(True), preds_expected.as_data_frame(True))
        else:
            assert status == "FAILED"


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_if_train_segments)
else:
    test_if_train_segments()
