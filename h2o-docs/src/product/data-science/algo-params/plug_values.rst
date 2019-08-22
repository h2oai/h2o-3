``plug_values``
---------

- Available in: GLM
- Hyperparameter: yes

Description
~~~~~~~~~~~

Plug Values (a single row frame containing values that will be used to impute missing values of the training/validation frame, use with conjunction missing_values_handling = PlugValues)

This option is used to specify one method of treating missing values. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `missing_values_handling <missing_values_handling.html>`__

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()


   .. code-block:: python

	import h2o
	from h2o.estimators.glm import H2OGeneralizedLinearEstimator
	h2o.init()
