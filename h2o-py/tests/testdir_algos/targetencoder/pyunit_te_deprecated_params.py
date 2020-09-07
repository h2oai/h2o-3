from __future__ import print_function
import os
import sys
import warnings

sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from h2o.estimators import H2OTargetEncoderEstimator
from h2o.exceptions import H2ODeprecationWarning
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


def test_deprecated_k_param_is_alias_for_inflection_point():
    ds = load_dataset(incl_test=True)
    te = H2OTargetEncoderEstimator(noise=0)
    te.train(y=ds.target, training_frame=ds.train)
    encoded = te.predict(ds.test)
    # print(encoded)
    with warnings.catch_warnings(record=True) as w:
        warnings.simplefilter("always")
        te_k = H2OTargetEncoderEstimator(noise=0, k=5, blending=True)
        assert len(w) == 1
        assert issubclass(w[0].category, H2ODeprecationWarning)
        assert "``k`` is deprecated" in str(w[0].message)
        
    te_k.train(y=ds.target, training_frame=ds.train)
    encoded_k = te_k.predict(ds.test)
    # print(encoded_k)
    te_ip = H2OTargetEncoderEstimator(noise=0, inflection_point=5, blending=True)
    te_ip.train(y=ds.target, training_frame=ds.train)
    encoded_ip = te_ip.predict(ds.test)
    # print(encoded_ip)
    try:
        pu.compare_frames(encoded_k, encoded, 0, tol_numeric=1e-5)
        assert False, "should have raised"
    except AssertionError as ae:
        assert "should have raised" not in str(ae)
    assert pu.compare_frames(encoded_k, encoded_ip, 0, tol_numeric=1e-5)


def test_deprecated_f_param_is_alias_for_smoothing():
    ds = load_dataset(incl_test=True)
    te = H2OTargetEncoderEstimator(noise=0)
    te.train(y=ds.target, training_frame=ds.train)
    encoded = te.predict(ds.test)
    # print(encoded)
    with warnings.catch_warnings(record=True) as w:
        warnings.simplefilter("always")
        te_f = H2OTargetEncoderEstimator(noise=0, f=25, blending=True)
        assert len(w) == 1
        assert issubclass(w[0].category, H2ODeprecationWarning)
        assert "``f`` is deprecated" in str(w[0].message)
        
    te_f.train(y=ds.target, training_frame=ds.train)
    encoded_f = te_f.predict(ds.test)
    # print(encoded_f)
    te_s = H2OTargetEncoderEstimator(noise=0, smoothing=25, blending=True)
    te_s.train(y=ds.target, training_frame=ds.train)
    encoded_s = te_s.predict(ds.test)
    # print(encoded_s)
    try:
        pu.compare_frames(encoded_f, encoded, 0, tol_numeric=1e-5)
        assert False, "should have raised"
    except AssertionError as ae:
        assert "should have raised" not in str(ae)
    assert pu.compare_frames(encoded_f, encoded_s, 0, tol_numeric=1e-5)


def test_deprecated_noise_level_param_is_alias_for_noise():
    ds = load_dataset(incl_test=True)
    te = H2OTargetEncoderEstimator()
    te.train(y=ds.target, training_frame=ds.train)
    encoded = te.predict(ds.test)
    # print(encoded)
    with warnings.catch_warnings(record=True) as w:
        warnings.simplefilter("always")
        te_nl = H2OTargetEncoderEstimator(noise_level=0)
        assert len(w) == 1
        assert issubclass(w[0].category, H2ODeprecationWarning)
        assert "``noise_level`` is deprecated" in str(w[0].message)

    te_nl.train(y=ds.target, training_frame=ds.train)
    encoded_nl = te_nl.predict(ds.test)
    # print(encoded_nl)
    te_n = H2OTargetEncoderEstimator(noise=0)
    te_n.train(y=ds.target, training_frame=ds.train)
    encoded_n = te_n.predict(ds.test)
    # print(encoded_n)
    try:
        pu.compare_frames(encoded_nl, encoded, 0, tol_numeric=1e-5)
        assert False, "should have raised"
    except AssertionError as ae:
        assert "should have raised" not in str(ae)
    assert pu.compare_frames(encoded_nl, encoded_n, 0, tol_numeric=1e-5)


def test_transform_seed_param_raise_warning():
    ds = load_dataset(incl_test=True)
    te = H2OTargetEncoderEstimator(seed=42)
    te.train(y=ds.target, training_frame=ds.train)
    encoded = te.predict(ds.test)
    
    transformed_1 = te.transform(ds.test)
    with warnings.catch_warnings(record=True) as w:
        warnings.simplefilter("always")
        transformed_2 = te.transform(ds.test, seed=24)
        assert len(w) == 1
        assert issubclass(w[0].category, H2ODeprecationWarning)
        assert "`seed` is deprecated in `transform` method and will be ignored" in str(w[0].message)

    assert pu.compare_frames(encoded, transformed_1, 0, tol_numeric=1e-5)
    assert pu.compare_frames(encoded, transformed_2, 0, tol_numeric=1e-5)


def test_transform_data_leakage_handling_param_raise_warning():
    ds = load_dataset(incl_test=True)
    te = H2OTargetEncoderEstimator(data_leakage_handling="leave_one_out", seed=42)
    te.train(y=ds.target, training_frame=ds.train)
    encoded = te.predict(ds.test)
    encoded_as_training = te.transform(ds.test, as_training=True)

    transformed_1 = te.transform(ds.test)
    with warnings.catch_warnings(record=True) as w:
        warnings.simplefilter("always")
        transformed_2 = te.transform(ds.test, data_leakage_handling="none")
        assert len(w) == 1
        assert issubclass(w[0].category, H2ODeprecationWarning)
        assert "`data_leakage_handling` is deprecated in `transform` method and will be ignored" in str(w[0].message)

    # if data_leakage_handling is specified and not "none", this is interpreted as `as_training=True`
    with warnings.catch_warnings(record=True) as w:
        warnings.simplefilter("always")
        transformed_3 = te.transform(ds.test, data_leakage_handling="leave_one_out")
        assert len(w) == 2
        assert issubclass(w[1].category, H2ODeprecationWarning)
        assert "as_training=True" in str(w[1].message)
        
    assert pu.compare_frames(encoded, transformed_1, 0, tol_numeric=1e-5)
    assert pu.compare_frames(encoded, transformed_2, 0, tol_numeric=1e-5)
    assert pu.compare_frames(encoded_as_training, transformed_3, 0, tol_numeric=1e-5)
    try:
        pu.compare_frames(encoded, transformed_3, 0, tol_numeric=1e-5)
        assert False, "should have raised"
    except AssertionError as ae:
        assert "should have raised" not in str(ae)


pu.run_tests([
    test_deprecated_k_param_is_alias_for_inflection_point,
    test_deprecated_f_param_is_alias_for_smoothing,
    test_deprecated_noise_level_param_is_alias_for_noise,
    test_transform_seed_param_raise_warning,
    test_transform_data_leakage_handling_param_raise_warning,
])
