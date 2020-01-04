#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys
import os
sys.path.insert(1, os.path.join("..", "..", "..", "h2o-py"))
from tests import pyunit_utils
import h2o
from h2o.estimators import H2OXGBoostEstimator


def xgb_repro():
    name_node = pyunit_utils.hadoop_namenode()
    data = h2o.import_file(
        "hdfs://" + name_node + "/user/jenkins/bigdata/laptop/airlinesBillion_7Columns_5GB.csv",     
        na_strings=["NA"]
    )

    train, test = data.split_frame(ratios=[0.99], seed=1)
    x = data.names
    y = "C31"
    x.remove(y)

    model = H2OXGBoostEstimator(
        ntrees=5, max_depth=6,
        learn_rate=0.1, seed=12345,
        backend="CPU"
    )
    model.train(x=x, y=y, training_frame=train)
    p1 = model.predict(test)
    model.train(x=x, y=y, training_frame=train)
    p2 = model.predict(test)
    p = p1.cbind(p2)

    diff = (p[1] != p[4]).as_data_frame()
    ndiffs = 0
    for i in range(len(diff)-1):
        if diff.iat[i, 0] != 0:
            ndiffs += 1

    assert ndiffs == 0, "diffs %d out of %d rows" % (ndiffs, p1.nrows)


if __name__ == "__main__":
    pyunit_utils.standalone_test(xgb_repro)
else:
    xgb_repro()
