``distance``
-----

- Available in: KNN
- Hyperparameter: no

Description
~~~~~~~~~~~

In K-nearest neighbors algorithm, the ``distance`` type specifies a measure to calculate the distance between data points.

Options:

- ``euclidean`` :   :math:`\sqrt{\sum_{i=1}^{n}{(p_i - q_i)^2}}`
- ``manhattan``:    :math:`\sum_{i=1}^{n}{|p_i - q_i|}`
- ``cosine``:       :math:`1 - \frac{\sum_{i=1}^{n} p_i * q_i}{\sqrt{\sum_{i=1}^{n} p_i^2} * \sqrt{\sum_{i=1}^{n} q_i^2}}`

where:

- :math:`p_i` and :math:`q_i` are data vector components 
- :math:`n` is number of components in the vector


Related Parameters
~~~~~~~~~~~~~~~~~~

- `k <k.html>`__ 

Example
~~~~~~~

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
