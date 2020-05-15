``stop_column``
---------------

- Available in: CoxPH
- Hyperparameter: no

Description
~~~~~~~~~~~

This option is used to specify the name of an integer column in the **source** data set representing the stop time. This is required. In addition, if a **start_column** is specified, then the value of the **stop_column** must be strictly greater than the **start_column** in each row.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `start_column <start_column.html>`__


Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

        library(h2o)
        h2o.init()
        # import the heart dataset
        heart <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")

        # set the predictor name and response column
        x <- "age"
        y <- "event" 

        # set the start and stop columns
        start <- "start"
        stop <- "stop"

        # train your model
        heart_coxph <- h2o.coxph(x = x, event_column = y, 
                                 start_column = start, stop_column = stop, 
                                 training_frame = heart)

        # view the model details
        heart_coxph
        Model Details:
        ==============

        H2OCoxPHModel: coxph
        Model ID:  CoxPH_model_R_1527700369755_2 
        Call:
        "Surv(start, stop, event) ~ age"

              coef exp(coef) se(coef)    z     p
        age 0.0307    1.0312   0.0143 2.15 0.031

        Likelihood ratio test = 5.17  on 1 df, p = 0.023
        n = 172, number of events = 75



   .. code-tab:: python
    
        import h2o
        from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator
        h2o.init()

        # import the heart dataset
        heart = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/coxph_test/heart.csv")

        # set the parameters
        heart_coxph = H2OCoxProportionalHazardsEstimator(start_column="start", 
                                                         stop_column="stop", 
                                                         ties="breslow")

        # train your model
        heart_coxph.train(x="age", y="event", training_frame=heart)

        # view the model details
        heart_coxph 
