``upload_custom_distribution``
------------------------------

- Available in: GBM
- Hyperparameter: no

Description
~~~~~~~~~~~

**Note**: This function is only available in Python.

H2O’s GBM provides a number of distribution options that can be specified when building a model. Alternatively, you can use the ``upload_custom_distribution`` function to upload a custom distribution into a running H2O cluster.

The custom distribution is a function which implements the `water.udf.CDistributionFunc` interface. This interface follows the design of `hex.Distribution` and contains four methods to support distributed invocation:

- ``link``: This method returns type of link function transformation of the probability of response variable to a continuous scale that is unbounded.

- ``init``: This method combines weight, actual response and offset to compute the numerator and denominator of the initial value. It can return `[ weight * (response - offset), weight]` by default.

- ``gamma``: This method combines weight, actual response, residual and predicted response to compute numerator and denominator of size of step in terminal node estimate.

- ``gradient``: This method computes the (negative half) gradient of deviance function at the predicted value for actual response in one GBM learning step.

Three separate fields must be specified when using this function:

- ``klazz``: Represents a custom distribution function that provides the four methods described above.

- ``func_name``: Assigns a name with uploaded custom functions. This name corresponds to the name of the key in the distributed key-value store.

- ``func_file``: The name of the file to store function in uploaded jar [wip]. The source code of the given class is saved into a file that is subsequently zipped, uploaded as a zip-archive, and saved into the distributed key-value store.

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. example-code::
   .. code-block:: python

	import h2o
	h2o.init()

	# wip
	from h2o.utils.distributions import CustomDistributionGaussian
	custom_dist_func = h2o.upload_custom_distribution(CustomDistributionGaussian, func_name="gaussian", func_file="dist_gaussian.py")

	# wip
	print(custom_dist_func)
	python:gaussian=dist_gaussian.CustomDistributionGaussianWrapper