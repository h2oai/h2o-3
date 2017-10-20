#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Aggregator number of exemplars test

These tests verify, that `Aggregator` Model is producing correct number
of exemplars on various setup of `target_num_exemplars` and `rel_tol_num_exemplars` parameters
"""
import h2o
from tests import pyunit_utils
from h2o.frame import H2OFrame
from h2o.estimators.aggregator import H2OAggregatorEstimator


def test_num_of_exemplars(target_exemplars, tol):
    """Test whether number of generated exemplars corresponds to expected number +/- tolerance
    """
    df = h2o.create_frame(
        rows=10000,
        cols=2,
        categorical_fraction=0.1,
        integer_fraction=0.3,
        real_range=100,
        seed=1234
    )

    agg = H2OAggregatorEstimator(target_num_exemplars=target_exemplars, rel_tol_num_exemplars=tol)
    agg.train(training_frame=df)
    assert agg.aggregated_frame is not None, "Trained model should produce not empty aggregated frame"
    assert (1-tol)*target_exemplars <= agg.aggregated_frame.nrows <= (1+tol)*target_exemplars, \
        "Final number of aggregated exemplars should be in equal to target number +/- tolerance"


def test_num_of_examplars_10_95():
    test_num_of_exemplars(10, 0.95)


def test_num_of_examplars_100_5():
    test_num_of_exemplars(100, 0.5)


def test_num_of_examplars_500_3():
    test_num_of_exemplars(500, 0.3)


def test_num_of_examplars_1500_05():
    test_num_of_exemplars(1500, 0.05)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_num_of_examplars_10_95)
    pyunit_utils.standalone_test(test_num_of_examplars_100_5)
    pyunit_utils.standalone_test(test_num_of_examplars_500_3)
    pyunit_utils.standalone_test(test_num_of_examplars_1500_05)
else:
    test_num_of_examplars_10_95()
    test_num_of_examplars_100_5()
    test_num_of_examplars_500_3()
    test_num_of_examplars_1500_05()
