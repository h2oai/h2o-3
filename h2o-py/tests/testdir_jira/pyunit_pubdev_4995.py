#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
from tests import pyunit_utils

from h2o.estimators.aggregator import H2OAggregatorEstimator
from h2o.exceptions import H2OResponseError


DATASET = pyunit_utils.locate("smalldata/extdata/australia.csv")


def test_4995():
    data = h2o.import_file(DATASET)

    raised = False
    agg = H2OAggregatorEstimator(model_id="aggregator")
    try:
        agg.train(training_frame=data)
    except H2OResponseError:
        raised = True

    # H2OAggregatorEstimator.train raises error, when requesting wrong API version
    assert raised is False, "Local and Server versions of AggregatorEstimator should match!"


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_4995)
else:
    test_4995()
