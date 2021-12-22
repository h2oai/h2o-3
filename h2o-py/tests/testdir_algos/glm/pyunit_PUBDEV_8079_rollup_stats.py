#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from tests import pyunit_utils

# test rollup stats failures.  Thank you Tomas Fryda for providing me with this test.  This test does not need an
# assert statement at the end.  It just needs to run to completion without failure.
def test_rollup_stats():
    df = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/rollup_stat_test.csv"))
    df["RACE"] = df["RACE"].asfactor()  # this is important
    glm = H2OGeneralizedLinearEstimator(generate_scoring_history=True, score_iteration_interval=5, non_negative=True,
                                    alpha=[0.5, 1.0], standardize=False, nfolds=5, seed=7)
    glm.train(y="RACE", training_frame=df)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_rollup_stats)
else:
    test_rollup_stats()
