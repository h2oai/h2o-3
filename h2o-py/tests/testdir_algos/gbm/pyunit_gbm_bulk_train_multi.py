import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def test_gbm_bulk_train_multi():
    response = "survived"
    segment_columns = ["pclass", "sex"]
    titanic = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/titanic.csv"))
    titanic[response] = titanic[response].asfactor()

    params = {
        "ntrees": 5,
        "seed": 42
    }
    titanic_gbm = H2OGradientBoostingEstimator(**params)
    models = titanic_gbm.bulk_train(y=response, ignored_columns=["name"], training_frame=titanic,
                                    segments=segment_columns)
    models_list = models.as_frame().sort(by=segment_columns)

    assert models_list.names == [u'pclass', u'sex', u'Status', u'Model', u'Errors', u'Warnings']
    assert models_list.nrow == 6

    segments = models_list[segment_columns]

    models_explicit = titanic_gbm.bulk_train(y=response, ignored_columns=["name"], training_frame=titanic,
                                             segments=segments)
    models_explicit_list = models_explicit.as_frame().sort(by=segment_columns)

    def model_comparator(frame1, frame2, col_ind, rows1, numElements):
        assert numElements == 0
        models1 = frame1[col_ind].as_data_frame()
        models2 = frame2[col_ind].as_data_frame()
        for i in range(rows1):
            model_id_1 = str(models1.iloc[i][0])
            model_1 = h2o.get_model(model_id_1)
            model_id_2 = str(models2.iloc[i][0])
            model_2 = h2o.get_model(model_id_2)
            print("###### Comparing model {0} and model {1}.".format(model_1.model_id, model_2.model_id))
            pyunit_utils.check_models(model_1, model_2)
    
    assert pyunit_utils.compare_frames(models_list, models_explicit_list, 0, 
                                       custom_comparators={"Model": model_comparator})


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gbm_bulk_train_multi)
else:
    test_gbm_bulk_train_multi()
