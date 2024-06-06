Quantiles
=========

This function retrieves and displays quantiles for H2O-3 parsed data.

.. note:: 
	
	The quantile results in Flow are computed lazily on-demand and cached. It's a fast approximation (max - min / 1024) that's very accurate for most use cases. If the distribution is skewed, the quantile results may not be as accurate as the results obtained using ``h2o.quantile`` in R or ``H2OFrame.quantile`` in Python.

Quantile parameters
-------------------

- **combine_method**: Specify the method for combining quantiles for even sample sizes. Abbreviations for average, low, and high are acceptable (avg, lo, hi). The default is to do linear interpolation (e.g. if method is "lo", then it will take the lo value of the quantile). Available methods include: 

	- ``average``
	- ``high``
	- ``interpolate``
	- ``low``
	
- **h2oFrame**: Specify the H2OFrame.
- **prob**: Specify a list of probabilities with values in the range [0,1]. By default, the following probabilities are returned:
	
	- Python: 0.01, 0.1, 0.25, 0.333, 0.5, 0.667, 0.75, 0.9, 0.99 
	- R: 0.001, 0.01, 0.1, 0.25, 0.33, 0.5, 0.667, 0.75, 0.9, 0.99, 0.999 

- `weights_column <algo-params/weights_column.html>`__: A string name identifying the obsevation weights column in this frame or a single-column, separate H2OFrame of observation weights. If this option isn't specified, then all rows are assumed to have equal importance.

Examples
--------

.. tabs::
	.. code-tab:: python

		# Import the prostate dataset:
		prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")

		# Request quantiles for the parsed dataset
		prostate.quantile()

		# Request quantiles for the AGE column:
		prostate["AGE"].quantile()

		# Request quantiles for probabilities 0.001 and 0.01 for the AGE column
		prostate["AGE"].quantile(prob=[0.001, 0.01])

	.. code-tab:: r R

		# Import the prostate dataset:
		prostate <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")

		# Request quantiles for the parsed dataset
		quantile(prostate)

		# Request quantiles for the AGE column:
		quantile(prostate[, 3])

		# Request quantiles for probabilities 0.001 and 0.01 for the AGE column
		quantile(prostate[, 3], prob=c(0.001, 0.01))

