from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


def test_glrm_indempotence():
    # df = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"), destination_frame='iris')
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/extdata/australia.csv"), destination_frame='autralia')

    estimator = H2OGeneralizedLowRankEstimator(k=2)

    print("input data before train:")
    print(df)

    estimator.train(training_frame=df)
    # estimator.show()
    print("input data after train:")
    print(df)

    preds1 = estimator.predict(df)
    print("input data after predict:")
    print(df)
    print("predictions on input data:")
    print(preds1)

    estimator.train(training_frame=df)
    preds2 = estimator.predict(df)
    print("2nd predictions on input data:")
    print(preds2)

    # assertion below should fail
    assert not (preds1 == preds2).all(), "GLRM is now indempotent, rather a good thing: this is fixed then!"



def test_glrm_predict_on_clone():
    # df = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"), destination_frame='iris')
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/extdata/australia.csv"), destination_frame='autralia')

    estimator = H2OGeneralizedLowRankEstimator(k=2)

    print("input data before train:")
    print(df)

    estimator.train(training_frame=df)
    # estimator.show()
    print("input data after train:")
    print(df)

    preds1 = estimator.predict(df)
    print("input data after predict:")
    print(df)
    print("predictions on input data:")
    print(preds1)

    preds2 = estimator.predict(df)
    print("2nd predictions on input data:")
    print(preds2)

    assert (preds1 == preds2).all(), "Consecutive predicts don't return same predictions"

    df_clone = h2o.deep_copy(df, df.frame_id+'_clone')
    print("cloned data:")
    print(df)
    preds1_clone = estimator.predict(df_clone)
    print("predictions on cloned data:")
    print(preds1_clone)

    # assertion below should fail
    assert not(preds1 == preds1_clone).all(), "GLRM now does provide similar predictions on cloned data, looks like this is fixed!"


pyunit_utils.run_tests([
    test_glrm_indempotence,
    test_glrm_predict_on_clone
])