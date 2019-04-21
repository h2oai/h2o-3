#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Aggregator parameters test

These tests verify, that `Aggregator` can be passed all valid parameters
"""
import h2o
from tests import pyunit_utils
from h2o.frame import H2OFrame
from h2o.estimators.aggregator import H2OAggregatorEstimator


def test_all_params():
    data_path = pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip")
    df = h2o.import_file(data_path)
    params = {
        "model_id": "agg",
        "training_frame": df,
        "response_column": "IsDepDelayed",
        "ignored_columns": ["UniqueCarrier"],
        "ignore_const_cols": False,
        "target_num_exemplars": 500,
        "rel_tol_num_exemplars": 0.3,
        "transform": "standardize",
        "categorical_encoding": "eigen"
    }
    try:
        model = H2OAggregatorEstimator(**params)
        model.train(training_frame=df)
    except:
        assert False, "Should not throw error on valid parameters"


def test_transform():
    valid_values = ["none", "standardize", "normalize", "demean", "descale"]
    df = h2o.create_frame(
        rows=100,
        cols=4,
        categorical_fraction=0.4,
        integer_fraction=0,
        binary_fraction=0,
        real_range=100,
        integer_range=100,
        missing_fraction=0,
        seed=1234
    )
    model = H2OAggregatorEstimator(target_num_exemplars=5)
    try:
        for val in valid_values:
            model.transform = val
            model.train(training_frame=df)
    except:
        assert False, "Aggregator model should be able to process all valid transform values"

    # Try with invalid value
    try:
        model = H2OAggregatorEstimator(target_num_exemplars=5, transform="some_invalid_value")
        assert False, "Passing invalid value of transform should throw an error"
    except:
        pass


def test_cat_encoding():
    valid_values = [
        "auto", "enum", "one_hot_internal",
        "one_hot_explicit", "binary", "eigen",
        "label_encoder", "enum_limited",
        # "sort_by_response"    TODO: This is invalid parameter, remove it
    ]
    df = h2o.create_frame(
        rows=100,
        cols=4,
        categorical_fraction=0.4,
        integer_fraction=0,
        binary_fraction=0,
        real_range=100,
        integer_range=100,
        missing_fraction=0,
        seed=1234
    )
    model = H2OAggregatorEstimator(target_num_exemplars=5)
    try:
        for val in valid_values:
            model.categorical_encoding = val
            model.train(training_frame=df)
    except:
        assert False, "Aggregator model should be able to process all valid categorical_encoding values"

    # Try with invalid value
    try:
        model = H2OAggregatorEstimator(target_num_exemplars=5, categorical_encoding="some_invalid_value")
        assert False, "Passing invalid value of categorical_encoding should throw an error"
    except:
        pass

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_all_params)
    pyunit_utils.standalone_test(test_transform)
    pyunit_utils.standalone_test(test_cat_encoding)
else:
    test_all_params()
    test_transform()
    test_cat_encoding()
