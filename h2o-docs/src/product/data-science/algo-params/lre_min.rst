``lre_min``
-----------

- Available in: CoxPH
- Hyperparameter: no

Description
~~~~~~~~~~~

The ``lre_min`` option measures the relative difference of log likelihood before and after iteration of the CoxPH algorithm. When building a CoxPH model, the algorithm stops when :math:`|(logLik - newLoglik) / newLoglik| <= 1e-9`. This value defaults to 9.


Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()

		# import the heart dataset
		heart <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")

		# split the dataset into train and test datasets
		heart_split <- h2o.splitFrame(data = heart, ratios = 0.8, seed = 1234)
		train <- heart_split[[1]]
		test <- heart_split[[2]]

		# train a CoxPH model
		coxph_model <- h2o.coxph(x = "age", 
		                         event_column = "event", 
		                         start_column = "start", 
		                         stop_column = "stop", 
		                         training_frame = heart, 
		                         lre_min = 9)

		# run prediction against the test dataset
		predicted <- h2o.predict(coxph_model, test)

		# view the predictions
		predicted
		           lp
		1 0.26964730
		2 0.16438761
		3 0.07569035
		4 0.27813870
		5 0.27813870
		6 0.26090368

		[34 rows x 1 column]

   .. code-tab:: python

		import h2o
		from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator
		h2o.init()

		# import the heart dataset
		heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")

		# split the dataset into train and test datasets
		train, test = heart.split_frame(ratios = [.8], seed=1234)

		# set the lre_mind parameter's value
		lre_min = 9

		# initialize an train a CoxPH model
		coxph = H2OCoxProportionalHazardsEstimator(start_column="start", 
		                                           stop_column="stop", 
		                                           ties="breslow", 
		                                           lre_min=lre_min)
		coxph.train(x="age", y="event", training_frame=heart)

		# run prediction against the test dataset
		pred = coxph.predict(test_data=test)

		# view the predictions
		pred
	           lp
	    ---------
	    0.269501
	    0.164298
	    0.0756492
	    0.277987
	    0.277987
	    0.260762
	    0.260762
	    0.254712
	    0.347814
	    0.299666

	    [34 rows x 1 column]

