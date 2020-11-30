``ties``
--------

- Available in: CoxPH
- Hyperparameter: no

Description
~~~~~~~~~~~

This option configures approximation method for handling ties in the partial likelihood. This can be either **efron** (default) or **breslow**).

Of the two approximations, Efron's produces results closer to the exact combinatoric solution than Breslow's. Under this approximation, the partial likelihood and log partial likelihood are defined as:

 :math:`PL(\beta) = \prod_{m=1}^M \frac{\exp(\sum_{j \in D_m} w_j\mathbf{x}_j^T\beta)}{\big[\prod_{k=1}^{d_m}(\sum_{j \in R_m} w_j \exp(\mathbf{x}_j^T\beta) - \frac{k-1}{d_m} \sum_{j \in D_m} w_j \exp(\mathbf{x}_j^T\beta))\big]^{(\sum_{j \in D_m} w_j)/d_m}}`

 :math:`pl(\beta) = \sum_{m=1}^M \big[\sum_{j \in D_m} w_j\mathbf{x}_j^T\beta - \frac{\sum_{j \in D_m} w_j}{d_m} \sum_{k=1}^{d_m} \log(\sum_{j \in R_m} w_j \exp(\mathbf{x}_j^T\beta) - \frac{k-1}{d_m} \sum_{j \in D_m} w_j \exp(\mathbf{x}_j^T\beta))\big]`

Under Breslow's approximation, the partial likelihood and log partial likelihood are defined as:

 :math:`PL(\beta) = \prod_{m=1}^M \frac{\exp(\sum_{j \in D_m} w_j\mathbf{x}_j^T\beta)}{(\sum_{j \in R_m} w_j \exp(\mathbf{x}_j^T\beta))^{\sum_{j \in D_m} w_j}}`

 :math:`pl(\beta) = \sum_{m=1}^M \big[\sum_{j \in D_m} w_j\mathbf{x}_j^T\beta - (\sum_{j \in D_m} w_j)\log(\sum_{j \in R_m} w_j \exp(\mathbf{x}_j^T\beta))\big]`


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

        # set the predictor name and response column
        x <- "age"
        y <- "event" 

        # set the start and stop columns
        start <- "start"
        stop <- "stop"

        # train your model
        heart_coxph <- h2o.coxph(x = x, event_column = y, 
                                 start_column = start, stop_column = stop, 
                                 ties = "breslow", training_frame = heart)

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
