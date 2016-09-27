#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function
import h2o
from h2o.estimators.kmeans import H2OKMeansEstimator
from tests import pyunit_utils



def parametersKmeans():

    print("Getting data...")
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

    print("Create and and duplicate...")

    iris_km = H2OKMeansEstimator(k=3, seed=1234)
    iris_km.train(x=list(range(4)), training_frame=iris)
    parameters = iris_km._model_json['parameters']
    param_dict = {pp['name']: pp['actual_value'] for pp in parameters}
    fold_column = param_dict.pop('fold_column')
    del param_dict["model_id"]
    del param_dict['training_frame']
    del param_dict['validation_frame']
    del param_dict['max_runtime_secs']
    iris_km_again = H2OKMeansEstimator(**param_dict)  # not all parameters go here - invalid test
    iris_km_again.train(x=list(range(4)), training_frame=iris, fold_column=fold_column)

    print("wss")
    wss = iris_km.withinss().sort()
    wss_again = iris_km_again.withinss().sort()
    assert wss == wss_again, "expected wss to be equal"

    print("centers")
    centers = iris_km.centers()
    centers_again = iris_km_again.centers()
    assert centers == centers_again, "expected centers to be the same"



if __name__ == "__main__":
    pyunit_utils.standalone_test(parametersKmeans)
else:
    parametersKmeans()
