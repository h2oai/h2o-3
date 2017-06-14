``laplace``
-----------

- Available in: Naïve-Bayes
- Hyperparameter: yes

Description
~~~~~~~~~~~

This option specifies a value for the Laplace smoothing factor, which sets the conditional probability of a predictor. If the Laplace smoothing parameter is disabled (``laplace = 0``), then Naive Bayes will predict a probability of 0 for any row in the test set that contains a previously unseen categorical level. However, if the Laplace smoothing parameter is used (e.g. ``laplace = 1``), then the model can make predictions for rows that include previously unseen categorical level.

Laplace smoothing adjusts the maximum likelihood estimates by adding 1 to the numerator and :math:`k` to the denominator to allow for new categorical levels in the training set:

   :math:`\phi_{j|y=1}= \frac{\Sigma_{i=1}^m 1(x_{j}^{(i)} \ = \ 1 \ \bigcap y^{(i)} \ = \ 1) \ + \ 1}{\Sigma_{i=1}^{m}1(y^{(i)} \ = \ 1) \ + \ k}`

   :math:`\phi_{j|y=0}= \frac{\Sigma_{i=1}^m 1(x_{j}^{(i)} \ = \ 1 \ \bigcap y^{(i)} \ = \ 0) \ + \ 1}{\Sigma_{i \ = \ 1}^{m}1(y^{(i)} \ = \ 0) \ + \ k}`

:math:`x^{(i)}` represents features, :math:`y^{(i)}` represents the response column, and :math:`k` represents the addition of each new categorical level. (:math:`k` functions to balance the added 1 in the numerator.)

Laplace smoothing should be used with care; it is generally intended to allow for predictions in rare events. As prediction data becomes increasingly distinct from training data, new models should be trained when possible to account for a broader set of possible feature values.

This value must be >=0 and defaults to 0. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the cars dataset:
	prostate <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")

	# Converting CAPSULE, RACE, DCAPS, and DPROS to categorical
	prostate$CAPSULE <- as.factor(prostate$CAPSULE)
	prostate$RACE <- as.factor(prostate$RACE)
	prostate$DCAPS <- as.factor(prostate$DCAPS)
	prostate$DPROS <- as.factor(prostate$DPROS)

	# Compare with Naive Bayes when x = 3:9, y = 2, and use laplace smoothing
	prostate.nb <- h2o.naiveBayes(x = 3:9, y = 2, training_frame = prostate, laplace = 1)
	print(prostate.nb)

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

    # Compare with Naive Bayes when x = 3:9, y = 2, and use laplace smoothing
    prostate_nb = H2ONaiveBayesEstimator(laplace = 1)
    prostate_nb.train(x=list(range(3,9)), y=response_col, training_frame=prostate)
    prostate_nb.show() 
    
    # Predict on training data
    prostate_pred = prostate_nb.predict(prostate)
    prostate_pred.head()
