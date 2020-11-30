from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..","..",".."))
import h2o
from h2o.estimators import H2OTargetEncoderEstimator
from h2o.exceptions import H2OResponseError
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


def test_all_categoricals_are_encoded_by_default():
    ds = load_dataset(incl_test=True)
    categoricals = {n for n, t in ds.train.types.items() if t == 'enum'} - {ds.target}
    assert len(categoricals) > 0
    te = H2OTargetEncoderEstimator()
    te.train(y=ds.target, training_frame=ds.train)
    encoded = te.predict(ds.test)
    assert {"{}_te".format(n) for n in categoricals} < set(encoded.names), "some categoricals haven't been encoded"
    assert set(ds.train.names) < set(encoded.names), "some original columns have been removed from predictions"


def test_columns_to_encode_can_be_specified():
    ds = load_dataset(incl_test=True)
    categoricals = {n for n, t in ds.train.types.items() if t == 'enum'} - {ds.target}
    to_encode = {c for i, c in enumerate(categoricals) if i % 2}
    assert len(to_encode) > 0
    te = H2OTargetEncoderEstimator()
    te.train(x=to_encode, y=ds.target, training_frame=ds.train)
    encoded = te.predict(ds.test)
    assert {"{}_te".format(n) for n in to_encode} < set(encoded.names), "some provided categoricals haven't been encoded"
    not_encoded = categoricals - to_encode
    assert len(not_encoded) > 0
    assert not ({"{}_te".format(n) for n in not_encoded} & set(encoded.names)), "some categoricals that were not provided to TargetEncoder have been encoded nonetheless"


def test_non_categorical_columns_are_ignored():
    ds = load_dataset(incl_test=True)
    categoricals = {n for n, t in ds.train.types.items() if t == 'enum'} - {ds.target}
    non_cat = next(n for n, t in ds.train.types.items() if t in ['int', 'real'])
    to_encode = categoricals | {non_cat}
    assert len(to_encode) > len(categoricals) > 0
    te = H2OTargetEncoderEstimator()
    te.train(x=to_encode, y=ds.target, training_frame=ds.train)
    encoded = te.predict(ds.test)
    assert {"{}_te".format(n) for n in categoricals} < set(encoded.names), "some provided categoricals haven't been encoded"
    assert "{}_te".format(non_cat) not in encoded.names, "non categorical column has been encoded (magic!)"


def test_encoding_fails_if_there_is_no_categorical_column_to_encode():
    ds = load_dataset()
    non_cat = {n for n, t in ds.train.types.items() if t in ['int', 'real']}
    to_encode = non_cat
    assert len(to_encode) > 0
    te = H2OTargetEncoderEstimator()
    try:
        te.train(x=to_encode, y=ds.target, training_frame=ds.train)
        assert False, "should have raised error"
    except H2OResponseError as e:
        assert "Training data must have at least 2 features (incl. response)" in str(e)
    

def test_fold_column_is_not_encoded():
    ds = load_dataset(incl_foldc=True)
    te = H2OTargetEncoderEstimator(data_leakage_handling="none")
    te.train(y=ds.target, training_frame=ds.train, fold_column="foldc")
    encoded = te.predict(ds.train)
    assert "foldc" in encoded.names
    assert "foldc_te" not in encoded.names
    
    te = H2OTargetEncoderEstimator(data_leakage_handling="kfold")
    te.train(y=ds.target, training_frame=ds.train, fold_column="foldc")
    encoded = te.predict(ds.train)
    assert "foldc" in encoded.names
    assert "foldc_te" not in encoded.names

    te = H2OTargetEncoderEstimator(data_leakage_handling="leave_one_out")
    te.train(y=ds.target, training_frame=ds.train, fold_column="foldc")
    encoded = te.predict(ds.train)
    assert "foldc" in encoded.names
    assert "foldc_te" not in encoded.names
    

pu.run_tests([
    test_all_categoricals_are_encoded_by_default,
    test_columns_to_encode_can_be_specified,
    test_non_categorical_columns_are_ignored,
    test_encoding_fails_if_there_is_no_categorical_column_to_encode,
    test_fold_column_is_not_encoded
])
