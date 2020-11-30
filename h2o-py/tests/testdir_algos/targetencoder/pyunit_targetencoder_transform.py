from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from h2o.estimators import H2OTargetEncoderEstimator
from tests import pyunit_utils as pu

seed = 42


def load_dataset(incl_test=False, incl_foldc=False):
    fr = h2o.import_file(pu.locate("smalldata/titanic/titanic_expanded.csv"), header=1)
    target = "survived"
    train = fr
    test = None
    if incl_test:
        fr = fr.split_frame(ratios=[.8], destination_frames=["titanic_train", "titanic_test"], seed=seed)
        train = fr[0]
        test = fr[1]
    if incl_foldc:
        train["foldc"] = train.kfold_column(3, seed)
    return pu.ns(train=train, test=test, target=target)


def test_transform_produces_the_same_result_as_predict_by_default():
    ds = load_dataset(incl_test=True)
    te = H2OTargetEncoderEstimator()
    te.train(y=ds.target, training_frame=ds.train)
    encoded = te.predict(ds.test)
    transformed = te.transform(ds.test)
    assert pu.compare_frames(encoded, transformed, 0, tol_numeric=1e-5)
    
    # now with non default params
    te_nd = H2OTargetEncoderEstimator(data_leakage_handling="leave_one_out",
                                   blending=True, inflection_point=5, smoothing=17,
                                   seed=seed, noise=0.01)
    te_nd.train(y=ds.target, training_frame=ds.train)
    encoded = te_nd.predict(ds.test)
    transformed = te_nd.transform(ds.test)
    assert pu.compare_frames(encoded, transformed, 0, tol_numeric=1e-5)


def test_transform_can_be_applied_to_training_frame_with_special_flag():
    ds = load_dataset()
    te = H2OTargetEncoderEstimator()
    te.train(y=ds.target, training_frame=ds.train)
    transformed_as_training = te.transform(ds.train, as_training=True)
    transformed = te.transform(ds.train)
    
    assert pu.compare_frames(transformed, transformed_as_training, 0, tol_numeric=1e-5)

    # now with non default params
    te_nd = H2OTargetEncoderEstimator(data_leakage_handling="leave_one_out",
                                      blending=True, inflection_point=5, smoothing=17,
                                      seed=seed, noise=0.01)
    te_nd.train(y=ds.target, training_frame=ds.train)
    transformed_as_training = te_nd.transform(ds.train, as_training=True)
    transformed = te_nd.transform(ds.train)
    try:
        assert pu.compare_frames(transformed, transformed_as_training, 0, tol_numeric=1e-5)
        assert False, "should have raised"
    except AssertionError as ae:
        assert "should have raised" not in str(ae)
    

def test_transform_can_override_noise():
    ds = load_dataset(incl_test=True)
    noise = 1e-3
    te = H2OTargetEncoderEstimator(noise=noise, seed=seed)
    te.train(y=ds.target, training_frame=ds.train)
    transformed = te.transform(ds.test)
    transformed_no_noise = te.transform(ds.test, noise=0)
    try:
        assert pu.compare_frames(transformed, transformed_no_noise, 0, tol_numeric=noise/10)
        assert False, "should have raised"
    except AssertionError as ae:
        assert "should have raised" not in str(ae)
    
    assert pu.compare_frames(transformed, transformed_no_noise, 0, tol_numeric=noise)


def test_transform_can_override_blending_parameters():
    ds = load_dataset(incl_test=True)
    te = H2OTargetEncoderEstimator(noise=0)
    te.train(y=ds.target, training_frame=ds.train)
    transformed = te.transform(ds.test)
    transformed_blending = te.transform(ds.test, blending=True)
    try:
        assert pu.compare_frames(transformed, transformed_blending, 0, tol_numeric=1e-5)
        assert False, "should have raised"
    except AssertionError as ae:
        assert "should have raised" not in str(ae)
        
    transformed_blending_custom = te.transform(ds.test, blending=True, inflection_point=3, smoothing=17)
    try:
        assert pu.compare_frames(transformed_blending_custom, transformed_blending, 0, tol_numeric=1e-5)
        assert False, "should have raised"
    except AssertionError as ae:
        assert "should have raised" not in str(ae)
    

pu.run_tests([
    test_transform_produces_the_same_result_as_predict_by_default,
    test_transform_can_be_applied_to_training_frame_with_special_flag,
    test_transform_can_override_noise,
    test_transform_can_override_blending_parameters,
])
