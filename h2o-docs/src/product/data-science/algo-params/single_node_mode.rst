``single_node_mode``
--------------------

- Available in: CoxPH, Deep Learning
- Hyperparameter: no

Description
~~~~~~~~~~~

Specify whether to run on a single node for fine-tuning of model parameters. Running on a single node reduces the effect of network overhead for smaller datasets.

Example
~~~~~~~

.. tabs::
	.. code-tab:: r R

		library(h2o)
		h2o.init()

		# Import the heart dataset:
		heart <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")

		# Build and train the model:
		coxph_model <- h2o.coxph(x = "age", 
		                         event_column = "event", 
		                         start_column = "start", 
		                         stop_column = "stop",
		                         training_frame = heart, 
		                         single_node_mode = TRUE)

		# Generate predictions: 
		pred <- h2o.predict(object = coxph_model, newdata = heart)


	.. code-tab:: python

		import h2o
		from h2o.estimators import H2OCoxProportionalHazardsEstimator
		h2o.init()

		# Import the heart dataset:
		heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")

		# Build and train the model:
		coxph = H2OCoxProportionalHazardsEstimator(start_column="start", 
		                                           stop_column="stop", 
		                                           single_node_mode=True)
		coxph.train(x="age", y="event", training_frame=heart)

		# Generate predictions:
		pred = coxph.predict(test_data=heart)
