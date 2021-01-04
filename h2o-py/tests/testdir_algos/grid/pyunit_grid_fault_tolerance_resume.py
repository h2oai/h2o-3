import sys
import os
import tempfile
import time
import random
import numpy as np

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.deeplearning import H2OAutoEncoderEstimator
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator
from h2o.estimators.kmeans import H2OKMeansEstimator
from h2o.estimators.xgboost import H2OXGBoostEstimator


def import_ecology():
    return h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))


def import_iris():
    return h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))


def import_iris2():
    return h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))


def import_cars():
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars.drop(0)
    return cars


def import_grlm_matrix():
    train = np.dot(np.random.rand(1000, 10), np.random.rand(10, 100))
    return h2o.H2OFrame(train.tolist())    


def test_gbm():
    df = import_ecology()
    df["Angaus"] = df["Angaus"].asfactor()
    df["Weights"] = h2o.H2OFrame.from_python(abs(np.random.randn(df.nrow, 1)).tolist())[0]
    train, calib = df.split_frame(ratios=[.8], destination_frames=["eco_train", "eco_calib"], seed=42)
    params = {
        "distribution": "bernoulli", "min_rows": 10, "max_depth": 5,
        "weights_column": "Weights",
        "calibrate_model": True, "calibration_frame": calib
    }
    hyper_params = {
        "learn_rate": [0.01, 0.05],
        "ntrees": [100, 110, 120, 130]
    }
    grid_ft_resume(
        train, "GBM", params, hyper_params, gbm_start, gbm_resume
    )


def gbm_start(grid_id, export_dir, train, params, hyper_parameters):
    grid = H2OGridSearch(
        H2OGradientBoostingEstimator,
        grid_id=grid_id,
        hyper_params=hyper_parameters,
        recovery_dir=export_dir
    )
    grid.start(x=list(range(2, train.ncol)), y="Angaus", training_frame=train, **params)
    return grid


def gbm_resume(grid, train, params):
    grid.train(x=list(range(2, train.ncol)), y="Angaus", training_frame=train, **params)


def test_dl():
    train = import_iris2()
    ae_model = H2OAutoEncoderEstimator(
        activation="Tanh", hidden=[40, 80],
        model_id="ae_model", epochs=1,
        ignore_const_cols=False
    )
    ae_model.train(list(range(4)), training_frame=train)
    dl1 = H2ODeepLearningEstimator(hidden=[10, 10], export_weights_and_biases=True)
    dl1.train(x=list(range(4)), y=4, training_frame=train)
    w1 = dl1.weights(0)
    w3 = dl1.weights(2)
    b1 = dl1.biases(0)
    b2 = dl1.biases(1)
    params = {
        "initial_weights": [w1, None, w3], "initial_biases": [b1, b2, None],
        "pretrained_autoencoder": "ae_model",
        "hidden": [40, 80], "ignore_const_cols": False
    }
    hyper_params = {
        "epochs": [2, 4, 6, 10, 20, 50],
        "rate": [.005, .006, .007]
    }
    grid_ft_resume(
        train, "DEEP_LEARNING", params, hyper_params, dl_start, dl_resume
    )


def dl_start(grid_id, export_dir, train, params, hyper_parameters):
    grid = H2OGridSearch(
        H2ODeepLearningEstimator,
        grid_id=grid_id,
        hyper_params=hyper_parameters,
        recovery_dir=export_dir
    )
    grid.start(x=list(range(4)), y=4, training_frame=train, **params)
    return grid


def dl_resume(grid, train, params):
    grid.train(x=list(range(4)), y=4, training_frame=train, **params)


def test_glm():
    train = import_cars()
    means = train.mean()
    bc = []
    y = "cylinders"
    x = train.names
    x.remove(y)
    for n in x:
        if train[n].isnumeric()[0]:
            lower_bound = random.uniform(-1, 1)
            upper_bound = lower_bound + random.random()
            bc.append([n, lower_bound, upper_bound])
    beta_constraints = h2o.H2OFrame(bc)
    beta_constraints.set_names(["names", "lower_bounds", "upper_bounds"])
    params = {
        "missing_values_handling": "PlugValues", 
        "plug_values": means,
        "beta_constraints": beta_constraints
    }
    hyper_params = {
        'alpha': [0.01, 0.3, 0.5, 0.7, 0.9],
        'lambda': [1e-5, 1e-6, 1e-7, 1e-8, 5e-5, 5e-6, 5e-7, 5e-8]
    }
    grid_ft_resume(
        train, "GLM", params, hyper_params, glm_start, glm_resume
    )


def glm_start(grid_id, export_dir, train, params, hyper_parameters):
    y = "cylinders"
    x = train.names
    x.remove(y)
    grid = H2OGridSearch(
        H2OGeneralizedLinearEstimator,
        grid_id=grid_id,
        hyper_params=hyper_parameters,
        recovery_dir=export_dir
    )
    grid.start(x=x, y=y, training_frame=train, **params)
    return grid


def glm_resume(grid, train, params):
    y = "cylinders"
    x = train.names
    x.remove(y)
    grid.train(x=x, y=y, training_frame=train, **params)


def test_glrm():
    initial_y = np.random.rand(10, 100)
    initial_y_h2o = h2o.H2OFrame(initial_y.tolist(), destination_frame="glrm_initial_y")
    params = {
        "k": 10, 
        "init": "User",
        "user_y": initial_y_h2o,
        "loss": "Quadratic",
        "regularization_x": "OneSparse", 
        "regularization_y": "NonNegative"
    }
    hyper_params = {
        "transform": ["NONE", "DEMEAN"],
        "gamma_x": [0.1, 1],
        "gamma_y": [0.2, 2]
    }
    grid_ft_resume(
        import_grlm_matrix(), "GLRM", params, hyper_params, glrm_start, glrm_resume
    )


def glrm_start(grid_id, export_dir, train, params, hyper_parameters):
    grid = H2OGridSearch(
        H2OGeneralizedLowRankEstimator,
        grid_id=grid_id,
        hyper_params=hyper_parameters,
        recovery_dir=export_dir
    )
    grid.start(x=train.names, training_frame=train, **params)
    return grid


def glrm_resume(grid, train, params):
    grid.train(x=train.names, training_frame=train, **params)


def test_kmeans():
    s = [[4.9, 3.0, 1.4, 0.2],
         [5.6, 2.5, 3.9, 1.1],
         [6.5, 3.0, 5.2, 2.0]]
    start = h2o.H2OFrame(s, destination_frame="kmeans_user_points")
    params = {
        "k": 3, 
        "user_points": start
    }
    hyper_params = {
        'max_iterations': [1000, 2000, 3000, 4000, 5000, 6000, 10000],
        'standardize': [True, False],
        'seed': [1, 42, 1234]
    }
    grid_ft_resume(
        import_iris2(), "K-means", params, hyper_params, kmeans_start, kmeans_resume
    )


def kmeans_start(grid_id, export_dir, train, params, hyper_parameters):
    grid = H2OGridSearch(
        H2OKMeansEstimator(),
        grid_id=grid_id,
        hyper_params=hyper_parameters,
        recovery_dir=export_dir
    )
    grid.start(x=list(range(4)), training_frame=train, **params)
    return grid


def kmeans_resume(grid, train, params):
    grid.train(x=list(range(4)), training_frame=train, **params)


def test_xgboost():
    df = import_ecology()
    df["Angaus"] = df["Angaus"].asfactor()
    df["Weights"] = h2o.H2OFrame.from_python(abs(np.random.randn(df.nrow, 1)).tolist())[0]
    train, calib = df.split_frame(ratios=[.8], destination_frames=["eco_train", "eco_calib"], seed=42)
    params = {
        "distribution": "bernoulli", "min_rows": 10, "max_depth": 5,
        "weights_column": "Weights",
        "calibrate_model": True, "calibration_frame": calib
    }
    hyper_params = {
        "learn_rate": [0.01, 0.05],
        "ntrees": [50, 100, 150]
    }
    grid_ft_resume(
        train, "XGBOOST", params, hyper_params, xgboost_start, xgboost_resume
    )


def xgboost_start(grid_id, export_dir, train, params, hyper_parameters):
    grid = H2OGridSearch(
        H2OXGBoostEstimator,
        grid_id=grid_id,
        hyper_params=hyper_parameters,
        recovery_dir=export_dir
    )
    grid.start(x=list(range(2, train.ncol)), y="Angaus", training_frame=train, **params)
    return grid


def xgboost_resume(grid, train, params):
    grid.train(x=list(range(2, train.ncol)), y="Angaus", training_frame=train, **params)


def grid_ft_resume(train, grid_id, params, hyper_parameters, start_grid, resume_grid):
    print("TESTING %s\n-------------------" % grid_id)
    export_dir = tempfile.mkdtemp()
    print("Using directory %s" % export_dir)
    grid_size = 1
    for p in hyper_parameters:
        grid_size *= len(hyper_parameters[p])
    print("Grid size %d" % grid_size)
    print("Starting baseline grid")
    grid = start_grid(grid_id, export_dir, train, params, hyper_parameters)
    grid_in_progress = None
    times_waited = 0
    while (times_waited < 3_000) and (grid_in_progress is None or len(grid_in_progress.model_ids) == 0):
        time.sleep(0.1)  # give it tome to train some models
        times_waited += 1
        try:
            grid_in_progress = h2o.get_grid(grid_id)
        except IndexError:
            if times_waited % 100 == 0:
                print("no models trained yet after %ss" % (times_waited / 10))
    grid.cancel()

    grid = h2o.get_grid(grid_id)
    old_grid_model_count = len(grid.model_ids)
    print("Baseline grid has %d models:" % old_grid_model_count)
    assert old_grid_model_count < grid_size, "The full grid should not have finished yet."
    for x in sorted(grid.model_ids):
        print(x)
    h2o.remove_all()

    loaded = h2o.load_grid("%s/%s" % (export_dir, grid_id), load_params_references=True)
    assert loaded is not None
    assert len(grid.model_ids) == old_grid_model_count
    loaded_train = h2o.H2OFrame.get_frame(train.frame_id)
    assert loaded_train is not None, "Train frame was not loaded"
    loaded.hyper_params = hyper_parameters
    print("Starting final grid")
    resume_grid(loaded, loaded_train, params)
    print("Newly grained grid has %d models:" % len(loaded.model_ids))
    for x in sorted(loaded.model_ids):
        print(x)
    assert len(loaded.model_ids) == grid_size, "The full grid was not trained."
    h2o.remove_all()
    

def grid_ft_resume_test():
    test_dl()
    test_gbm()
    test_glm()
    test_glrm()
    test_kmeans()
    test_xgboost()


if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_ft_resume_test)
else:
    grid_ft_resume_test()
