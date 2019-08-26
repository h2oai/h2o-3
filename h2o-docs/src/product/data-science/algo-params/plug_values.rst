``plug_values``
---------------

- Available in: GLM
- Hyperparameter: yes

Description
~~~~~~~~~~~

When ``missing_values_handling=PlugValues``, this option is used to specify a frame containing values that will be used to impute missing values. Whereas other options mean-impute rows or skip them entirely, plug values allow you to specify values of your own choosing in the form of a single row frame that contains the desired value.

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