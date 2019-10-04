``stratify_by``
---------------

- Available in: CoxPH
- Hyperparameter: yes

Description
~~~~~~~~~~~

Specify a list of columns to use for stratification.


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

	# set the predictor and response column
	x <- "age"
	y <- "event"

	# set the start and stop columns
	start <- "start"
	stop <- "stop"

	# convert the age column to a factor
	heart["age"] <- as.factor(heart["age"])

	# train your model
	coxph.h2o <- h2o.coxph(x=c("year", x), event_column=y, start_column=start, stop_column=stop, stratify_by=x, training_frame=heart)

	# view the model details
	coxph.h2o
	Model Details:
	==============

    H2OCoxPHModel: coxph
    Model ID:  CoxPH_model_R_1570209287520_5 
    Call:
    Surv(start, stop, event) ~ year + strata(age)

            coef    exp(coef) se(coef)  z      p
    year    4.734   113.717   8973.421  0.001  1

    Likelihood ratio test=1.39  on 1 df, p=0.239
    n= 172, number of events= 75
    