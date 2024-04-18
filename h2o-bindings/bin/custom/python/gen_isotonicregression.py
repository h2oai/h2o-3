examples = dict(
    out_of_bounds="""
>>> import h2o
>>> from h2o import H2OFrame
>>> from h2o.estimators.isotonicregression import H2OIsotonicRegressionEstimator
>>> from sklearn.datasets import make_regression
>>> import numpy as np
>>> h2o.init()
>>> X, y = make_regression(n_samples=10000, n_features=1, random_state=41, noise=0.8)
>>> X = X.reshape(-1)
>>> train = H2OFrame(np.column_stack((y, X)), column_names=["y", "X"])
>>> w_values = np.random.rand(train.shape[0])
>>> w_frame = H2OFrame(w_values.reshape(-1, 1), column_names=["w"])
>>> train = train.cbind(w_frame)
>>> h2o_iso_reg = H2OIsotonicRegressionEstimator(out_of_bounds="clip")
>>> h2o_iso_reg.train(training_frame=train, x="X", y="y")
>>> h2o_iso_reg.predict(train)
"""
)
