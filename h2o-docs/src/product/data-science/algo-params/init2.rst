``init`` (CoxPH)
----------------

- Available in: CoxPH
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option allows you to set the initial values for the coefficients in the model. This value defaults to 0.


Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the heart dataset
	heart <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")

	# train your model
	  coxph.model <- h2o.coxph(x="age", event_column="event", start_column="start", stop_column="stop", training_frame=heart, init=3)

   .. code-block:: python

	import h2o
	from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator
	h2o.init()

	# import the heart dataset
	heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")

	#specify the parameter's value
	init = 3

	#set the parameters
	coxph = H2OCoxProportionalHazardsEstimator(start_column="start", stop_column="stop", ties="breslow", init=init)

	# train your model
	coxph.train(x="age", y="event", training_frame=heart)
