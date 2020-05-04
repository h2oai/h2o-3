Quantiles
---------

This function retrieves and displays quantiles for H2O parsed data along with the parameters that can be specified.

**Note**: The quantile results in Flow are computed lazily on-demand and cached. It is a fast approximation (max - min / 1024) that is very accurate for most use cases. If the distribution is skewed, the quantile results may not be as accurate as the results obtained using ``h2o.quantile`` in R or ``H2OFrame.quantile`` in Python.

Quantile Parameters
~~~~~~~~~~~~~~~~~~~

- `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.
- `weights_column <algo-params/weights_column.html>`__: (Optional) String name of the obsevation weights column in x or an ``H2OFrame`` object with a single numeric column or observation weights.
- **probs**: Numeric vector of probabilities with values in [0,1].
- **combine_method**: How to combine quantiles for even sample sizes. Abbreviations for average, low, and high are acceptable (avg, lo, hi). The default is to do linear interpolation (e.g. if method is "lo", then it will take the lo value of the quantile). 

Examples
~~~~~~~~

.. tabs::
	.. code-tab:: r R

		# Import the prostate dataset:
		prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
		prostate <- h2o.uploadFile(path = prostate_path)

		# Request quantiles for a subset of columns in an H2O parsed data set:
		quantile(prostate[,3])
		for(i in 1:ncol(prostate))
			quantile(prostate[, i])

	.. code-tab:: python

		# Import the prostate dataset:
		prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")

		# Request quantiles for a subset of columns in an H2O parsed data set:
		prostate["AGE"].quantile()


