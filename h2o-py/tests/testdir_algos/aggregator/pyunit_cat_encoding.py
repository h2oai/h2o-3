#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Aggregator categorical encoding test

These tests verify, that `Aggregator` accepts different `categorical_encoding` values.
"""
import h2o
from tests import pyunit_utils
from h2o.frame import H2OFrame
from h2o.estimators.aggregator import H2OAggregatorEstimator


def is_consistent(train_rows, out_frame):
    """ Check if a number of observations assigned to exemplars is equal to rows in train set
    """
    return out_frame.sum(return_frame=True)["counts"].round() == train_rows


def correct_num_exemplars(out_frame, **kwargs):
    tol = kwargs.get('rel_tol_num_exemplars', 0.5)
    target_exemplars = kwargs.get('target_num_exemplars', 5000)
    return (1-tol)*target_exemplars <= out_frame.nrows <= (1+tol)*target_exemplars


def test_high_cardinality_eigen():
    df = h2o.create_frame(
        rows=10000,
        cols=10,
        categorical_fraction=0.6,
        integer_fraction=0,
        binary_fraction=0,
        real_range=100,
        integer_range=100,
        missing_fraction=0,
        factors=100,
        seed=1234
    )
    params = {
        "target_num_exemplars": 1000,
        "rel_tol_num_exemplars": 0.5,
        "categorical_encoding": "eigen"
    }
    agg = H2OAggregatorEstimator(**params)
    agg.train(training_frame=df)
    assert agg.aggregated_frame is not None, "Trained model should produce not empty aggregated frame"
    assert is_consistent(df.nrows, agg.aggregated_frame), "Exemplar counts should sum up to number of training rows"
    assert correct_num_exemplars(agg.aggregated_frame, **params), \
        "Generated number of exemplars should match target value"


def test_high_cardinality_enum():
    df = h2o.create_frame(
        rows=10000,
        cols=10,
        categorical_fraction=0.6,
        integer_fraction=0,
        binary_fraction=0,
        real_range=100,
        integer_range=100,
        missing_fraction=0,
        factors=100,
        seed=1234
    )
    params = {
        "target_num_exemplars": 2000,
        "rel_tol_num_exemplars": 0.5,
        "categorical_encoding": "enum"
    }
    agg = H2OAggregatorEstimator(**params)
    agg.train(training_frame=df)
    assert agg.aggregated_frame is not None, "Trained model should produce not empty aggregated frame"
    assert is_consistent(df.nrows, agg.aggregated_frame), "Exemplar counts should sum up to number of training rows"
    assert correct_num_exemplars(agg.aggregated_frame, **params), \
        "Generated number of exemplars should match target value"


def test_low_cardinality_enum_limited():
    raw_data = [
        "1|2|A|A",
        "1|2|A|A",
        "1|2|A|A",
        "1|2|A|A",
        "1|2|A|A",
        "2|2|A|B",
        "2|2|A|A",
        "1|4|A|A",
        "1|2|B|A",
        "1|2|B|A",
        "1|2|A|A",
        "1|2|A|A",
        "4|5|C|A",
        "4|5|D|A",
        "2|5|D|A",
        "3|5|E|A",
        "4|5|F|A",
        "4|5|G|A",
        "4|5|H|A",
        "4|5|I|A",
        "4|5|J|A",
        "4|5|K|A",
        "4|5|L|A",
        "4|5|M|A",
        "4|5|N|A",
        "4|5|O|A",
        "4|5|P|A"
    ]
    raw_data = [el.split("|") for el in raw_data]
    df = H2OFrame(raw_data)
    agg = H2OAggregatorEstimator(target_num_exemplars=5, categorical_encoding="enum_limited")
    agg.train(training_frame=df)
    assert agg.aggregated_frame is not None, "Trained model should produce not empty aggregated frame"
    assert is_consistent(df.nrows, agg.aggregated_frame), "Exemplar counts should sum up to number of training rows"
    # from AggregatorTest.java
    assert agg.aggregated_frame.nrows == 7, "Number of exemplars of this test case should be 7"


def test_binary():
    df = h2o.create_frame(
        rows=1000,
        cols=10,
        categorical_fraction=0.6,
        integer_fraction=0,
        binary_fraction=0,
        real_range=100,
        integer_range=100,
        missing_fraction=0.1,
        factors=5,
        seed=1234
    )
    params = {
        "target_num_exemplars": 100,
        "rel_tol_num_exemplars": 0.5,
        "categorical_encoding": "binary",
        "transform": "normalize"
    }
    agg = H2OAggregatorEstimator(**params)
    agg.train(training_frame=df)
    assert agg.aggregated_frame is not None, "Trained model should produce not empty aggregated frame"
    assert is_consistent(df.nrows, agg.aggregated_frame), \
        "Exemplar counts should sum up to number of training rows"
    assert correct_num_exemplars(agg.aggregated_frame, **params), \
        "Generated number of exemplars should match target value"


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_high_cardinality_eigen)
    pyunit_utils.standalone_test(test_high_cardinality_enum)
    pyunit_utils.standalone_test(test_low_cardinality_enum_limited)
    pyunit_utils.standalone_test(test_binary)
else:
    test_high_cardinality_eigen()
    test_high_cardinality_enum()
    test_low_cardinality_enum_limited()
    test_binary()
