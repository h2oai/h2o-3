#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
from h2o.estimators.kmeans import H2OKMeansEstimator
from h2o.exceptions import H2OTypeError
from tests import pyunit_utils

import random


def init_err_casesKmeans():
    # Log.info("Importing benign.csv data...\n")
    benign_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/benign.csv"))
    #benign_h2o.summary()
    numcol = benign_h2o.ncol
    numrow = benign_h2o.nrow


    # Log.info("Non-numeric entry that isn't 'Random', 'PlusPlus', or 'Furthest'")
    try:
        H2OKMeansEstimator(k=5, init="Test123").train(x=list(range(numcol)), training_frame=benign_h2o)
        assert False
    except H2OTypeError:
        pass

    # Log.info("Empty list, tuple, or dictionary")
    try:
        H2OKMeansEstimator(k=0, user_points=[]).train(x=list(range(numcol)), training_frame=benign_h2o)
        assert False
    except H2OTypeError:
        pass

    try:
        H2OKMeansEstimator(k=0, user_points=()).train(x=list(range(numcol)), training_frame=benign_h2o)
        assert False
    except H2OTypeError:
        pass

    try:
        H2OKMeansEstimator(k=0, user_points={}).train(x=list(range(numcol)), training_frame=benign_h2o)
        assert False
    except H2OTypeError:
        pass

    # Log.info("Number of columns doesn't equal training set's")
    start_small = [[random.gauss(0, 1) for c in range(numcol - 2)] for r in range(5)]
    start_large = [[random.gauss(0, 1) for c in range(numcol + 2)] for r in range(5)]

    try:
        H2OKMeansEstimator(k=5, user_points=h2o.H2OFrame(start_small)).\
            train(x=list(range(numcol)), training_frame=benign_h2o)
        assert False
    except EnvironmentError:
        pass

    try:
        H2OKMeansEstimator(k=5, user_points=h2o.H2OFrame(start_large)).\
            train(x=list(range(numcol)), training_frame=benign_h2o)
        assert False
    except EnvironmentError:
        pass

    # Log.info("Number of rows exceeds training set's")
    start = [[random.gauss(0, 1) for c in range(numcol)] for r in range(numrow + 2)]
    try:
        H2OKMeansEstimator(k=numrow + 2, user_points=h2o.H2OFrame(start)).\
            train(x=list(range(numcol)), training_frame=benign_h2o)
        assert False
    except EnvironmentError:
        pass

    # Nones are replaced with mean of a column in H2O. Not sure about Inf.
    # Log.info("Any entry is NA, NaN, or Inf")
    start = [[random.gauss(0, 1) for r in range(3)] for c in range(numcol)]
    for x in ["NA", "NaN", "Inf", "-Inf"]:
        start_err = start[:]
        start_err[random.randint(0, numcol - 1)][1] = x
        H2OKMeansEstimator(k=3, user_points=h2o.H2OFrame(list(zip(*start_err)))).\
            train(x=list(range(numcol)), training_frame=benign_h2o)

    # Duplicates will affect sampling probability during initialization.
    # Log.info("Duplicate initial clusters specified")
    start = [[random.gauss(0, 1) for r in range(3)] for c in range(numcol)]
    for s in start: s[2] = s[0]
    H2OKMeansEstimator(k=3, user_points=h2o.H2OFrame(list(zip(*start)))).\
        train(x=list(range(numcol)), training_frame=benign_h2o)



if __name__ == "__main__":
    pyunit_utils.standalone_test(init_err_casesKmeans)
else:
    init_err_casesKmeans()
