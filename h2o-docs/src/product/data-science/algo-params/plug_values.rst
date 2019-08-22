``plug_values``
---------------

- Available in: GLM
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option is used to specify a method of treating missing values. Whereas other options mean-impute rows or skip them entirely, Plug Values allow you to specify values of your own choosing in the form of a single row frame that contains the desired values—these will be used to impute the missing values of the training / validation frame when used in conjunction with the ``missing_values_handling`` function.

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
	h2o.init()
