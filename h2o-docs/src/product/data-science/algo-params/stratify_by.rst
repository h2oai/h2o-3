``stratify_by``
---------------

- Available in: CoxPH
- Hyperparameter: no

Description
~~~~~~~~~~~

In a CoxPH model, stratification is useful as a diagnostic for checking the proportional hazards assumption, as it allows for as many different hazard functions as there are strata. For example, when attempting to predict X, you can include a secondary categorical predictor, Z, that can be adjusted for when making inferences about X’s relationship to the time-to-event endpoint.

Use the ```stratify_by`` parameter to specify a list of columns to use for stratification when building a CoxPH model. The stratification column must be present in the ``x`` list in the ``<model_name>.train()`` call (e.g. if ``x=["PhoneService", "MultipleLines", "InternetService", "Contract"]``, then ``stratify_by`` must equal one of those columns).

Related Parameters
~~~~~~~~~~~~~~~~~~

- None

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()

		# import the heart dataset:
		heart <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")

		# set the predictor and response column:
		x <- "age"
		y <- "event"

		# set the start and stop columns:
		start <- "start"
		stop <- "stop"

		# convert the age column to a factor:
		heart["age"] <- as.factor(heart["age"])

		# train your model:
		heart_coxph <- h2o.coxph(x = c("year", x), 
		                         event_column = y, 
		                         start_column = start, 
		                         stop_column = stop, 
		                         stratify_by = x, 
		                         training_frame = heart)

		# view the model details:
		heart_coxph
		Model Details:
		==============

		H2OCoxPHModel: coxph
		Model ID:  CoxPH_model_R_1570209287520_5 
		Call:
		Surv(start, stop, event) ~ year + strata(age)

		        coef    exp(coef) se(coef)  z      p
		year    4.734   113.717   8973.421  0.001  1

		Likelihood ratio test = 1.39  on 1 df, p = 0.239
		n = 172, number of events = 75


   .. code-tab:: python

    	 import h2o
    	 from h2o.estimators import H2OCoxProportionalHazardsEstimator
    	 h2o.init()

    	 # import the heart dataset:
    	 heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")

    	 # set the predictor and response column:
    	 x = ["age", "year"]
    	 y = "event"

    	 # convert the age column to a factor:
    	 heart["age"] = heart["age"].ascharacter()
    	 heart["age"] = heart["age"].asfactor()

    	 # build and train your model:
    	 heart_coxph = H2OCoxProportionalHazardsEstimator(start_column="start", 
    	 						  stop_column="stop", 
    	 						  ties="breslow", 
    	 						  stratify_by=["age"])
    	 heart_coxph.train(x=x, y=y, training_frame=heart)

    	 # view the model details:
    	 heart_coxph
    	 Model Details
    	 =============
    	 H2OCoxProportionalHazardsEstimator :  Cox Proportional Hazards
    	 Model Key:  CoxPH_model_python_1604581637715_647

    	 Call:
    	 Surv(start, stop, event) ~ year + strata(age)

    	 Coefficients: CoxPH Coefficients
    	 names    coefficients    exp_coef    exp_neg_coef    se_coef    z_coef
    	 -------  --------------  ----------  --------------  ---------  -----------
    	 year     4.73372         113.717     0.00879373      8973.42    0.000527526

    	 Likelihood ratio test=1.386294
    	 n=172, number of events=75

