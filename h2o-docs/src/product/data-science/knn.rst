K-nearest neighbors (KNN)
-------------------------

Introduction
~~~~~~~~~~~~

K-nearest neighbors is a well-known classify algorithm. Based on the selected distance measure, it finds a defined number of nearest neighbors for every data point. The resulting class is decided based on these neighbor's classes. 

Supported distances are:

- ``euclidean`` :   :math:`\sqrt{\sum_{i=1}^{n}{(p_i - q_i)^2}}`
- ``manhattan``:    :math:`\sum_{i=1}^{n}{|p_i - q_i|}`
- ``cosine``:       :math:`1 - \frac{\sum_{i=1}^{n} p_i * q_i}{\sqrt{\sum_{i=1}^{n} p_i^2} * \sqrt{\sum_{i=1}^{n} q_i^2}}`

where:

- :math:`p_i` and :math:`q_i` are data vector components 
- :math:`n` is number of components in the vector


MOJO Support
''''''''''''

KNN currently does not support MOJO. 

Defining a KNN Model
~~~~~~~~~~~~~~~~~~~~

Parameters are optional unless specified as *required*.

Algorithm-specific parameters
'''''''''''''''''''''''''''''


-  `k <algo-params/k.html>`__: Specify the number of neighbours to find. This option defaults to ``3``.

-  `distance <algo-params/distance.html>`__: Specify the metric to use for computing the distances:

    -  ``euclidean`` (default)
    -  ``manhattan``
    -  ``cosine``

- `id_column <algo-params/id_column.html>`__: *Required* Specify the ID column.

Common parameters
'''''''''''''''''

- `auc_type <algo-params/auc_type.html>`__: Set the default multinomial AUC type. Must be one of:

    - ``"AUTO"`` (default)
    - ``"NONE"``
    - ``"MACRO_OVR"``
    - ``"WEIGHTED_OVR"``
    - ``"MACRO_OVO"``
    - ``"WEIGHTED_OVO"``

- `categorical_encoding <algo-params/categorical_encoding.html>`__: Specify one of the following encoding schemes for handling categorical features:

  - ``auto`` or ``AUTO`` (default): Allow the algorithm to decide. In KNN, the algorithm will automatically perform ``enum`` encoding.
  - ``enum`` or ``Enum``: 1 column per categorical feature.

-  `custom_metric_func <algo-params/custom_metric_func.html>`__: Specify a custom evaluation function.

-  `distribution <algo-params/distribution.html>`__: Specify the distribution (i.e. the loss function). The options are:
      
      - ``AUTO`` (default)
      - ``bernoulli`` -- response column must be 2-class categorical
      - ``multinomial`` -- response column must be categorical

- `gainslift_bins <algo-params/gainslift_bins.html>`__: The number of bins for a Gains/Lift table. The default value is ``-1`` and makes the binning automatic. To disable this feature, set to ``0``.

-  `ignore_const_cols <algo-params/ignore_const_cols.html>`__: Specify whether to ignore constant training columns, since no information can be gained from them. This option defaults to ``True`` (enabled).

-  `ignored_columns <algo-params/ignored_columns.html>`__: (Python and Flow only) Specify the column or columns to be excluded from the model. In Flow, click the checkbox next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **All** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **None** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. To change the selections for the hidden columns, use the **Select Visible** or **Deselect Visible** buttons.

-  `model_id <algo-params/model_id.html>`__: Specify a custom name for the model to use as a reference. By default, H2O automatically generates a destination key.

-  `max_runtime_secs <algo-params/max_runtime_secs.html>`__: Maximum allowed runtime in seconds for model training.  This option defaults to ``0`` (unlimited).

-  `seed <algo-params/seed.html>`__: Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. This option defaults to ``-1`` (time-based random number).

-  `training_frame <algo-params/training_frame.html>`__: *Required* Specify the dataset used to build the model. 
      
      **NOTE**: In Flow, if you click the **Build a model** button from the ``Parse`` cell, the training frame is entered automatically.

-  `x <algo-params/x.html>`__: Specify a vector containing the names or indices of the predictor variables to use when building the model. If ``x`` is missing, then all columns except ``y`` are used.

-  `y <algo-params/y.html>`__: *Required* Specify the column to use as the dependent variable. The data can be numeric or categorical.

Interpreting a KNN Model
~~~~~~~~~~~~~~~~~~~~~~~~

The output for KNN includes the following:

-  **Model parameters**  (hidden)
-  **Model Output**  (model category, model summary, training metrics)
-  **Neighbours information**  (distances, ids, classes)

FAQ
~~~

-  **How does the algorithm handle missing values during scoring?**

-  **How does the algorithm handle missing values during testing?**

-  **Does it matter if the data is sorted?**

    The order of the data could lead to different results. For example if there is more neighbors with the same distance the algorithm has to decide which select first. 

-  **What if there are a large number of rows?**

    The algorithm calculates exact k-nearest neighbors, so for large number of rows it could rapidly increase the time computing. 

-  **What if there are a large number of columns?**
    
    Large number of columns could slightly affect the calculation time, but not too rapidly as number of rows. 

-  **What if there are a large number of categorical factor levels?**

    Only enum categorical encoding is available, so the large number of categorical factor levels does not affect the computation. 

-  **How are categorical columns handled during model building?**

    Categorical columns are converted to enum categorical variable. No other categorical encoding is not available. It is not recommended to mix numeric and categorical variables together. For categorical columns cosine distance can be useful. 


KNN Algorithm
~~~~~~~~~~~~~

We implemented the exact KNN using map-reduce. The algorithm has these phases:

1. split data into n queries buckets
2. split data into m search buckets
3. for every query bucket, go throw all search buckets in parallel and find the local k-nearest neighbor
4. for every query bucket, reduce all local k-nearest neighbor results into one global

To store local k-nearest neighbors, we used TreeMap, where the map's size equals the `k`. The local TreeMaps are reduced to one global in the reduction phase.

The final class is calculated as an average class of the k-nearest neighbors' classes.

To score testing data, the training data are used to find k-nearest neighbors and define the result class. 

This algorithm is not a classical learning algorithm, so validation and cross-validation cannot be used.


Examples
~~~~~~~~

This example demonstrates how to build a K-nearest neighbors (KNN) model using H2O-3.

.. tabs::
   .. code-tab:: r R

    library(h2o)
    h2o.init()

    # Import the iris dataset into H2O
    iris.hex <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris.csv")

    # Generate ID column
    iris.hex$id <- as.h2o(1:nrow(iris.hex))

    # Train a model
    iris.knn <- h2o.knn(x=1:4, y=5, training_frame=iris.hex, id_column = "id", k=3 , distance="euclidean", seed=1234, auc_type="WEIGHTED_OVO")

    # Get performance
    perf <- h2o.performance(iris.knn, iris.hex, auc_type="WEIGHTED_OVO")
    print(perf)
    
    # Get multinomial AUC Table
    auc_table <- h2o.multinomial_auc_table(perf)

    # Get distances table 
    distances <- h2o.getFrame(iris.knn@model$distances)

   .. code-tab:: python

    import h2o
    from h2o.estimators import H2OKnnEstimator
    h2o.init()

    # Import the iris dataset into H2O
    train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris.csv")

    id_column = "id"
    response_column = "class"
    x_names = ["sepal_len", "sepal_wid", "petal_len", "petal_wid"]

    train[response_column] = train[response_column].asfactor()
    train[id_column] = h2o.H2OFrame(np.arange(0, train.shape[0]))

    model = H2OKnnEstimator(
        k=3,
        id_column=id_column,
        distance="euclidean",
        seed=1234,
        auc_type="macroovr"
    )

    # Train a model
    model.train(y=response_column, x=x_names, training_frame=train)

    # Get the performance
    perf = model.model_performance()
    print(perf)

    # Get the distances
    distances = model.distances()


References
~~~~~~~~~~

`Chi Zhang, and Jeffrey Jestes, "Efficient Parallel kNN Joins for Large Data in MapReduce", <https://ww2.cs.fsu.edu/~czhang/knnjedbt/>`_
