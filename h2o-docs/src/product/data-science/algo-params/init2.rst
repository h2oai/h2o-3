``init`` (CoxPH)
----------------

- Available in: CoxPH
- Hyperparameter: no

Description
~~~~~~~~~~~

When building a CoxPH model, the ``init`` option specifies the initial value, :math:`\beta^{(0)}`, for the coefficient vector. This value defaults to 0.


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

		# split the dataset into train and validation datasets
		heart_split <- h2o.splitFrame(data = heart, ratios = 0.8, seed = 1234)
		train <- heart_split[[1]]
		test <- heart_split[[2]]

		# train your model
		coxph_model <- h2o.coxph(x = "age", 
		                         event_column = "event", 
		                         start_column = "start", 
		                         stop_column = "stop", 
		                         training_frame = heart, 
		                         init = 3)

		# view the model details
		coxph_model
		Loading required namespace: survival
		Model Details:
		==============

		H2OCoxPHModel: coxph
		Model ID:  CoxPH_model_R_1570809926481_1 
		Call:
		Surv(start, stop, event) ~ age

		      coef exp(coef) se(coef)    z     p
		age 0.0307    1.0312   0.0143 2.15 0.031

		Likelihood ratio tes = 6109  on 1 df, p =< 2e-16
		n = 172, number of events = 75 

   .. code-tab:: python

		import h2o
		from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator
		h2o.init()

		# import the heart dataset
		heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")

		# split the dataset into train and test datasets
		train, test = heart.split_frame(ratios = [.8], seed=1234)

		#specify the init parameter's value
		init = 3

		# initialize an train a CoxPH model
		coxph = H2OCoxProportionalHazardsEstimator(start_column="start", 
		                                           stop_column="stop", 
		                                           ties="breslow", 
		                                           init=init)
		coxph.train(x="age", y="event", training_frame=heart)

		# view the model details
		coxph.train
		Model Details
		=============
		H2OCoxProportionalHazardsEstimator :  Cox Proportional Hazards
		Model Key:  CoxPH_model_python_1570808496252_2

		Call: 
		Surv(start, stop, event) ~ age

		Coefficients: CoxPH Coefficients
		names    coefficients    exp_coef    exp_neg_coef    se_coef    z_coef
		-------  --------------  ----------  --------------  ---------  --------
		age      0.030691        1.03117     0.969775        0.0142686  2.15095

		Likelihood ratio test=5.160759
		n=172, number of events=75

		Scoring History: 
		    timestamp            duration    iterations    loglik
		--  -------------------  ----------  ------------  --------
		    2019-10-11 08:59:31  0.000 sec   0             -298.326
		    2019-10-11 08:59:31  0.001 sec   1             -295.799
		    2019-10-11 08:59:31  0.002 sec   2             -295.745
		    2019-10-11 08:59:31  0.004 sec   3             -295.745
		<bound method H2OEstimator.train of >
