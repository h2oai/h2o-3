import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def test_gbm_bulk_train():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()

    # model will be built for each segment
    segment_col = "RACE"
    # segment 0 is too small, will not produce a model
    bad_segment = 0

    segments = prostate[segment_col].unique()
    segments.rename({'C1':segment_col})

    params = {
        "min_rows": 2,
        "ntrees": 4,
        "seed": 42
    }
    prostate_gbm = H2OGradientBoostingEstimator(**params)
    models = prostate_gbm.bulk_train(y="CAPSULE", ignored_columns=["ID"], training_frame=prostate,
                                     segments=segments)
    models_list = models.as_frame()
    
    assert models_list.names == [u'RACE', u'Status', u'Model', u'Errors', u'Warnings']
    assert models_list.nrow == 3
    
    # Check failed models
    expected_error = 'ERRR on field: _min_rows: The dataset size is too small to split for min_rows=2.0: ' \
                     'must have at least 4.0 (weighted) rows, but have only 3.0.\n'
    assert (models_list["Errors"][models_list[segment_col] == bad_segment]).as_data_frame()["Errors"][0] == expected_error

    mp = models_list.as_data_frame()
    # Check built models
    for i in range(mp.shape[0]):
        segment = int(mp.iloc[i][segment_col])
        if segment != bad_segment:
            model_id = mp.iloc[i]["Model"]
            model = h2o.get_model(model_id)
            prostate_segment = prostate[prostate[segment_col] == segment]
            prostate_gbm_segment = H2OGradientBoostingEstimator(**params)
            prostate_gbm_segment.train(y="CAPSULE", ignored_columns=["ID"], training_frame=prostate_segment)
            pyunit_utils.check_models(model, prostate_gbm_segment)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gbm_bulk_train)
else:
    test_gbm_bulk_train()
