``compute_metrics``
--------------------

- Available in: Naïve-Bayes, PCA
- Hyperparameter: yes

Description
~~~~~~~~~~~

The ``compute_metrics`` option specifies to compute metrics on the training data. This option is enabled by default.

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the prostate dataset:
	prostate <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")

	# Converting CAPSULE, RACE, DCAPS, and DPROS to categorical
	prostate$CAPSULE <- as.factor(prostate$CAPSULE)
	prostate$RACE <- as.factor(prostate$RACE)
	prostate$DCAPS <- as.factor(prostate$DCAPS)
	prostate$DPROS <- as.factor(prostate$DPROS)

	# Compare with Naive Bayes when x = 3:9, y = 2, and do not compute metrics
	prostate.nb <- h2o.naiveBayes(x = 3:9, y = 2, training_frame = prostate, laplace = 0, compute_metrics = FALSE)
	print(prostate.nb) # Note that metrics are not computed and, thus, do not display.

	# Predict on training data
	prostate.pred <- predict(prostate.nb, prostate)
	print(head(prostate.pred))

   .. code-block:: python

    import h2o
    h2o.init()
    from h2o.estimators.naive_bayes import H2ONaiveBayesEstimator

    # import prostate dataset:
    prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
    
    # Converting CAPSULE, RACE, DCAPS, and DPROS to categorical, and set the response column
    prostate['CAPSULE'] = prostate['CAPSULE'].asfactor()
    prostate['RACE'] = prostate['RACE'].asfactor()
    prostate['DCAPS'] = prostate['DCAPS'].asfactor()
    prostate['DPROS'] = prostate['DPROS'].asfactor()
    response_col = 'CAPSULE'

    # Compare with Naive Bayes when x = 3:9, y = 2, and do not compute metrics
    prostate_nb = H2ONaiveBayesEstimator(laplace = 0, compute_metrics = False)
    prostate_nb.train(x=list(range(3,9)), y=response_col, training_frame=prostate)
    prostate_nb.show() # Note that metrics are not computed and, thus, do not display.

    # Predict on training data
    prostate_pred = prostate_nb.predict(prostate)
    prostate_pred.head()
