Aggregator
----------

Introduction
~~~~~~~~~~~~

The H2O Aggregator method is a clustering-based method for reducing a numerical/categorical dataset into a dataset with fewer rows. If the dataset has categorical columns, then for each categorical column, Aggregator will:

 1. Accumulate the category frequencies.
 2. For the top 1,000 or fewer categories (by frequency), generate dummy variables (called one-hot encoding by ML people, called dummy coding by statisticians).
 3. Calculate the first eigenvector of the covariance matrix of these dummy variables.
 4. Replace the row values on the categorical column with the value from the eigenvector corresponding to the dummy values.

Aggregator maintains outliers as outliers, but lumps together dense clusters into exemplars with an attached count column showing the member points.

The Aggregator method behaves just any other unsupervised model. You can ignore columns, which will then be dropped for distance computation. Training itself creates the aggregated H2O Frame, which also includes the count of members for every row/exemplar. The aggregated frame always includes the full original content of the training frame, even if some columns were ignored for the distance computation. Scoring/prediction is overloaded with a function that returns the members of a given exemplar row index from 0...Nexemplars (this time without a count). 

MOJO Support
''''''''''''

Aggregator currently does not support `MOJOs <../save-and-load-model.html#supported-mojos>`__.

Defining an Aggregator Model
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Parameters are optional unless specified as *required*.

Algorithm-specific parameters
'''''''''''''''''''''''''''''

-  **num_iteration_without_new_exemplar**: The number of iterations to run before aggregator exits if the number of exemplars collected doesn't change. This option defaults to ``500``.

-  **rel_tol_num_exemplars**: Specify the relative tolerance for the number of exemplars (e.g. ``0.5`` is +/- 50 percent). This option defaults to ``0.5``.

-  **save_mapping_frame**: When this option is enabled, the mapping of rows in an aggregated frame to the one in the original/raw frame will be created and exported. This option defaults to ``False`` (disabled).

-  **target_num_exemplars**: Specify a value for the targeted number of exemplars. This option defaults to ``5000``.

Common parameters
'''''''''''''''''

-  `categorical_encoding <algo-params/categorical_encoding.html>`__: Specify one of the following encoding schemes for handling categorical features:

    - ``auto`` or ``AUTO`` (default): Allow the algorithm to decide. In Aggregator, the algorithm will automatically perform ``enum`` encoding.
    - ``one_hot_internal`` or ``OneHotInternal``: On the fly N+1 new cols for categorical features with N levels.
    - ``binary``: No more than 32 columns per categorical feature.
    - ``eigen`` or ``Eigen``: *k* columns per categorical feature, keeping projections of one-hot-encoded matrix onto *k*-dim eigen space only.
    - ``label_encoder`` or ``LabelEncoder``:  Convert every enum into the integer of its index (for example, level 0 -> 0, level 1 -> 1, etc.).
    - ``enum_limited`` or ``EnumLimited``: Automatically reduce categorical levels to the most prevalent ones during Aggregator training and only keep the **T** (10) most frequent levels.

-  `export_checkpoints_dir <algo-params/export_checkpoints_dir.html>`__: Specify a directory to which generated models will be automatically exported.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Enable this option to ignore constant training columns since no information can be gained from them. This option defaults to ``True`` (enabled).

-  `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `training_frame <algo-params/training_frame.html>`__: *Required* Specify the dataset used to build the model. 
  
    **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `transform <algo-params/transform.html>`__: Specify the transformation method for numeric columns in the training data. One of

    - ``"none"``
    - ``"standardize"``
    - ``"normalize"`` (default)
    - ``"demean"``
    - ``"descale"``

-  `x <algo-params/x.html>`__: Specify a vector contaitning the character names of the predictors in the model.

Aggregator Output
~~~~~~~~~~~~~~~~~

The output of the aggregation is a new aggregated frame that can be accessed in R and Python.

Examples
~~~~~~~~


This code illustrates how to build an Aggregator model using H2O-3 in both R and Python, including data frame creation, aggregation with eigen categorical encoding, and viewing the resulting aggregated data.

.. tabs::
   .. code-tab:: r R

        # Create a random frame with 5 columns and 100 rows
        df <- h2o.createFrame(
          rows = 100,
          cols = 5,
          categorical_fraction = 0.6,
          integer_fraction = 0,
          binary_fraction = 0,
          real_range = 100,
          integer_range = 100,
          missing_fraction = 0,
          seed = 123
        )

        # View the dataframe
        df
              C1        C2     C3        C4     C5
        1 c0.l53  10.94351 c2.l88 -93.64087 c4.l56
        2 c0.l21 -93.70999 c2.l37  39.10130 c4.l97
        3 c0.l96  55.43136  c2.l7 -43.47587 c4.l23
        4 c0.l78  27.41477 c2.l63  83.09211 c4.l81
        5 c0.l95 -77.98143 c2.l17 -93.95397  c4.l8
        6 c0.l90  12.54660 c2.l36  60.54920 c4.l56

        [100 rows x 5 columns]

        # Build an aggregated frame using eigan categorical encoding
        target_num_exemplars <- 1000
        rel_tol_num_exemplars <- 0.5
        encoding <- "Eigen"
        agg <- h2o.aggregator(training_frame = df, 
                              target_num_exemplars = target_num_exemplars, 
                              rel_tol_num_exemplars = rel_tol_num_exemplars, 
                              categorical_encoding = encoding)

        # Use the aggregated frame to create a new dataframe 
        new_df <- h2o.aggregated_frame(agg)

        #View the new dataframe
        new_df
              C1        C2     C3        C4     C5 counts
        1 c0.l53  10.94351 c2.l88 -93.64087 c4.l56      1
        2 c0.l21 -93.70999 c2.l37  39.10130 c4.l97      1
        3 c0.l96  55.43136  c2.l7 -43.47587 c4.l23      1
        4 c0.l78  27.41477 c2.l63  83.09211 c4.l81      1
        5 c0.l95 -77.98143 c2.l17 -93.95397  c4.l8      1
        6 c0.l90  12.54660 c2.l36  60.54920 c4.l56      1

        [100 rows x 6 columns] 

   .. code-tab:: python

        import h2o
        h2o.init()
        from h2o.estimators.aggregator import H2OAggregatorEstimator

        # Create a random data frame with 5 columns and 100 rows
        df = h2o.create_frame(
            rows=100,
            cols=5,
            categorical_fraction=0.6,
            integer_fraction=0,
            binary_fraction=0,
            real_range=100,
            integer_range=100,
            missing_fraction=0,
            seed=1234
        )

        # View the dataframe
        >>> df
              C1  C2      C3            C4  C5
        --------  ------  ------  --------  ------
         56.3978  c1.l74  c2.l58   36.4711  c4.l66
        -41.3355  c1.l31  c2.l43  -54.4267  c4.l4
         79.9964  c1.l4   c2.l68  -13.5409  c4.l49
         73.4546  c1.l5   c2.l25  -23.6456  c4.l12
         12.2449  c1.l7   c2.l49  -71.3769  c4.l61
        -20.2171  c1.l41  c2.l92  -70.2103  c4.l50
         80.6089  c1.l28  c2.l18  -34.7444  c4.l19
        -99.6821  c1.l21  c2.l74   93.7822  c4.l31
        -56.1135  c1.l35  c2.l8   -79.3114  c4.l75
        -71.4061  c1.l77  c2.l83  -32.2047  c4.l65

        [100 rows x 5 columns]

        # Build an aggregated frame using eigan categorical encoding
        params = {
            "target_num_exemplars": 1000,
            "rel_tol_num_exemplars": 0.5,
            "categorical_encoding": "eigen"
        }
        agg = H2OAggregatorEstimator(**params)
        agg.train(training_frame=df)

        # Use the aggregated model to create a new dataframe using aggregated_frame
        new_df = agg.aggregated_frame

        # View the new dataframe
        new_df
              C1  C2      C3            C4  C5        counts
        --------  ------  ------  --------  ------  --------
         56.3978  c1.l74  c2.l58   36.4711  c4.l66         1
        -41.3355  c1.l31  c2.l43  -54.4267  c4.l4          1
         79.9964  c1.l4   c2.l68  -13.5409  c4.l49         1
         73.4546  c1.l5   c2.l25  -23.6456  c4.l12         1
         12.2449  c1.l7   c2.l49  -71.3769  c4.l61         1
        -20.2171  c1.l41  c2.l92  -70.2103  c4.l50         1
         80.6089  c1.l28  c2.l18  -34.7444  c4.l19         1
        -99.6821  c1.l21  c2.l74   93.7822  c4.l31         1
        -56.1135  c1.l35  c2.l8   -79.3114  c4.l75         1
        -71.4061  c1.l77  c2.l83  -32.2047  c4.l65         1

        [100 rows x 6 columns]


References
~~~~~~~~~~

`Wilkinson, Leland. “Visualizing Outliers.” (2016). <https://www.cs.uic.edu/~wilkinson/Publications/outliers.pdf>`__
