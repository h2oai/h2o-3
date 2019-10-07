``lre_min``
-----------

- Available in: CoxPH
- Hyperparameter: yes

Description
~~~~~~~~~~~

A positive number to use as the minimum log-relative error (LRE) of subsequent log partial likelihood calculations to determine algorithmic convergence. This value defaults to 9.

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
	  coxph.model <- h2o.coxph(x="age", event_column="event", start_column="start", stop_column="stop", training_frame=heart, lre_min=9)

   .. code-block:: python

	import h2o
	from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator
	h2o.init()

	# import the heart dataset
	heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")

	#specify the parameter's value
	lre_min = 9

	#set the parameters
	coxph = H2OCoxProportionalHazardsEstimator(start_column="start", stop_column="stop", ties="breslow", lre_min=lre_min)

	# train your model
	coxph.train(x="age", y="event", training_frame=heart)
