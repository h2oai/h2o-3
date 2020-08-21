``interaction_pairs``
---------------------

- Available in: GLM, GAM, CoxPH
- Hyperparameter: no

Description
~~~~~~~~~~~

By default, interactions between predictor columns are expanded and computed on the fly as GLM iterates over dataset. The ``interaction_pairs`` parameter allows you to define a list of specific interactions to include instead of all interactions. 

Note that adding a list of interactions to a model changes the interpretation of all of the coefficients. For example, a typical predictor has the form ‘response ~ terms’ where ‘response’ is the (numeric) response vector, and ‘terms’ is a series of terms that specify a linear predictor for ‘response’. For ‘binomial’ and ‘quasibinomial’ families in GLM, the response can also be specified as a ‘factor’ (when the first level denotes failure and all other levels denote success) or as a two-column matrix with the columns giving the numbers of successes and failures. 

When using this parameter, specify a list of pairwise columns that should interact. When specified, GLM will compute interactions between 

Note that this option is mutually exclusive with ``interactions``.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `interactions <interactions.html>`__

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

        library(h2o)
        h2o.init()
        # import the airlines dataset
        df <-  h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")

        # specify the columns to include
        XY <- names(df)[c(1, 2, 3, 4, 6, 8, 9, 13, 17, 18, 19, 31)

        # specify the predictor column indices to interact
        interactions <- XY[c(5, 7, 9)]

        # train the model and build the coefficients table
        m1 <- h2o.glm(x = XY[-length(XY)],
    	             y = XY[length(XY)],
    	             training_frame = df,
    	             interactions = interactions, 
    	             lambda_search = TRUE,
    	             family = "binomial")
        m1_coefs <- m1@model$coefficients_table

        # train the model with the interaction pairs
        m2 <- h2o.glm(x = XY[-length(XY)],
    	              y = XY[length(XY)],
    	              training_frame = df,
    	              interaction_pairs = list(
    	               c("CRSDepTime", "UniqueCarrier"),
    	               c("CRSDepTime", "Origin"),
    	               c("UniqueCarrier", "Origin")
    	               ),
    	              lambda_search = TRUE,
    	              family = "binomial")
        m2_coefs <- m2@model$coefficients_table

   .. code-tab:: python

        import(h2o)
        h2o.init()
        from h2o.estimators.glm import H2OGeneralizedLinearEstimator

        # import the airlines dataset
        df = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")

        # specify the columns to include
        XY = [df.names[i-1] for i in [1,2,3,4,6,8,9,13,17,18,19,31]]

        # specify the predictor column indices to interact
        interactions = [XY[i-1] for i in [5,7,9]]

        # train the model and build the coefficients table
        m = H2OGeneralizedLinearEstimator(lambda_search=True, 
                                          family="binomial", 
                                          interactions=interactions)
        m.train(x=XY[:len(XY)], y=XY[-1],training_frame=df)
        coef_m = m._model_json['output']['coefficients_table']

        # define specific interaction pairs
        interaction_pairs = [("CRSDepTime", "UniqueCarrier"), 
                             ("CRSDepTime", "Origin"), 
                             ("UniqueCarrier", "Origin")]

        # train the model with the interaction pairs
        mexp = H2OGeneralizedLinearEstimator(lambda_search=True, 
                                             family="binomial", 
                                             interaction_pairs=interaction_pairs)
        mexp.train(x=XY[:len(XY)], y=XY[-1],training_frame=df)
        coef_mexp = mexp._model_json['output']['coefficients_table']

