.. _out_of_bounds:

``out_of_bounds``
----------------------

- Available in: Isotonic Regression
- Hyperparameter: no

Description
~~~~~~~~~~~

Use this option to specify how a trained model should treat values of an X predictor that are outside of the bounds seen in training.

Available options for ``out_of_bounds`` include the following:

- ``na``: Output NA for values that are outside of the interval seen during training. This is the default option.
- ``clip``: Use the prediction of the smallest or largest seen value depending on what side of the training interval the particular value falls in.

Example
~~~~~~~

.. tabs::

   .. code-tab:: python

        import h2o
        from h2o import H2OFrame
        from h2o.estimators.isotonicregression import H2OIsotonicRegressionEstimator
        import numpy as np
        from sklearn.datasets import make_regression
        h2o.init()

        X_full, y_full = make_regression(n_samples=10000, n_features=1, random_state=41, noise=0.8)
        X_full = X_full.reshape(-1)

        p05 = np.quantile(X_full, 0.05)
        p95 = np.quantile(X_full, 0.95)

        X = X_full[np.logical_and(p05 < X_full, X_full < p95)]
        y = y_full[np.logical_and(p05 < X_full, X_full < p95)]

        train = H2OFrame(np.column_stack((y, X)), column_names=["y", "X"])
        h2o_iso_reg = H2OIsotonicRegressionEstimator(out_of_bounds="clip")
        h2o_iso_reg.train(training_frame=train, x="X", y="y")

        test = H2OFrame(np.column_stack((y_full, X_full)), column_names=["y", "X"])
        h2o_test_preds = h2o_iso_reg.predict(test).as_data_frame()
        print(h2o_test_preds)
