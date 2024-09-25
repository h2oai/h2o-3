Training Models
===============

H2O supports training of supervised models (where the outcome variable is known) and unsupervised models (unlabeled data). Below we present examples of classification, regression, clustering, dimensionality reduction and training on data segments (train a set of models -- one for each partition of the data).

Supervised Learning
-------------------

Supervised learning algorithms support classification and regression problems.

Classification and Regression
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In classification problems, the output or "response" variable is a categorical value. The answer can be binary (for example, yes/no), or it can be multiclass (for example, dog/cat/mouse).

In regression problems,  the output or "response" variable is a numeric value. An example would be predicting someone's age or the sale price of a house. 

**Classification vs Regression in H2O models**: The way that you tell H2O whether you want to do classification versus regression for a particular supervised algorithm is by encoding the response column as either a categorical/factor type (classification) or a numeric type (regression).  If your column is represented as strings ("yes", "no"), then H2O will automatically encode that column as an categorical/factor (aka. "enum") type when you import your dataset.  However if you have a column with integers that represents a class in a classification problem (0, 1), you will have to :ref:`change the column type <change-column-type>` from numeric to categorical/factor (aka. "enum") type.  The reason that H2O requires the response column to be encoded as the "correct" type for a particular task is to maximze efficiency of the algorithm.

Classification Example
''''''''''''''''''''''

This example uses the Prostate dataset and :ref:`H2O's GLM algorithm <glm>` to predict the likelihood of a patient being diagnosed with prostate cancer. The dataset includes the following columns:

- **ID**: A row identifier. This can be dropped from the list of predictors.
- **CAPSULE**: Whether the tumor penetrated the prostatic capsule
- **AGE**: The patient's age
- **RACE**: The patient's race
- **DPROS**: The result of the digital rectal exam, where 1=no nodule; 2=unilober nodule on the left; 3 =unilibar nodule on the right; and 4=bilobar nodule.
- **DCAPS**: Whether there existed capsular involvement on the rectal exam
- **PSA**: The Prostate Specific Antigen Value (mg/ml)
- **VOL**: The tumor volume (cm3)
- **GLEASON**: The patient's Gleason score in the range 0 to 10

This example uses only the AGE, RACE, VOL, and GLEASON columns to make the prediction.

.. tabs::
   .. code-tab:: r R

    library(h2o)
    h2o.init()

    # import the prostate dataset
    df <- h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")

    # convert columns to factors
    df$CAPSULE <- as.factor(df$CAPSULE)
    df$RACE <- as.factor(df$RACE)
    df$DCAPS <- as.factor(df$DCAPS)
    df$DPROS <- as.factor(df$DPROS)

    # set the predictor and response columns
    predictors <- c("AGE", "RACE", "VOL", "GLEASON")
    response <- "CAPSULE"

    # split the dataset into train and test sets
    df_splits <- h2o.splitFrame(data =  df, ratios = 0.8, seed = 1234)
    train <- df_splits[[1]]
    test <- df_splits[[2]]

    # build a GLM model
    prostate_glm <- h2o.glm(family = "binomial", 
                            x = predictors, 
                            y = response, 
                            training_frame = df, 
                            lambda = 0, 
                            compute_p_values = TRUE)

    # predict using the GLM model and the testing dataset 
    predict <- h2o.predict(object = prostate_glm, newdata = test)

    # view a summary of the predictions
    h2o.head(predict)
      predict        p0         p1    StdErr
    1       1 0.5318993 0.46810066 0.4472349
    2       0 0.7269800 0.27302003 0.4413739
    3       0 0.9009476 0.09905238 0.3528526
    4       0 0.9062937 0.09370628 0.2874410
    5       1 0.6414760 0.35852398 0.2719742
    6       1 0.5092692 0.49073080 0.4007240

   .. code-tab:: python

    import h2o
    h2o.init()
    from h2o.estimators.glm import H2OGeneralizedLinearEstimator

    # import the prostate dataset
    prostate = h2o.import_file("https://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")

    # convert columns to factors
    prostate['CAPSULE'] = prostate['CAPSULE'].asfactor()
    prostate['RACE'] = prostate['RACE'].asfactor()
    prostate['DCAPS'] = prostate['DCAPS'].asfactor()
    prostate['DPROS'] = prostate['DPROS'].asfactor()

    # set the predictor and response columns
    predictors = ["AGE", "RACE", "VOL", "GLEASON"]
    response_col = "CAPSULE"

    # split into train and testing sets
    train, test = prostate.split_frame(ratios = [0.8], seed = 1234)

    # set GLM modeling parameters
    # and initialize model training
    glm_model = H2OGeneralizedLinearEstimator(family= "binomial", 
                                              lambda_ = 0, 
                                              compute_p_values = True)
    glm_model.train(predictors, response_col, training_frame= prostate)

    # predict using the model and the testing dataset
    predict = glm_model.predict(test)

    # View a summary of the prediction
    predict.head()
      predict        p0         p1    StdErr
    ---------  --------  ---------  --------
            1  0.531899  0.468101   0.447235
            0  0.72698   0.27302    0.441374
            0  0.900948  0.0990524  0.352853
            0  0.906294  0.0937063  0.287441
            1  0.641476  0.358524   0.271974
            1  0.509269  0.490731   0.400724
            1  0.355024  0.644976   0.235607
            1  0.304671  0.695329   1.33002
            1  0.472833  0.527167   0.170934
            0  0.720066  0.279934   0.221276

    [10 rows x 4 columns]


Regression Example
''''''''''''''''''

This example uses the Boston Housing data and :ref:`H2O's GLM algorithm <glm>` to predict the median home price using all available features. The dataset includes the following columns:

- **crim**: The per capita crime rate by town
- **zn**: The proportion of residential land zoned for lots over 25,000 sq.ft
- **indus**: The proportion of non-retail business acres per town
- **chas**: A Charles River dummy variable (1 if the tract bounds the Charles river; 0 otherwise)
- **nox**: Nitric oxides concentration (parts per 10 million)
- **rm**: The average number of rooms per dwelling
- **age**: The proportion of owner-occupied units built prior to 1940
- **dis**: The weighted distances to five Boston employment centers
- **rad**: The index of accessibility to radial highways
- **tax**: The full-value property-tax rate per $10,000
- **ptratio**: The pupil-teacher ratio by town
- **b**: 1000(Bk - 0.63)^2, where Bk is the black proportion of population
- **lstat**: The % lower status of the population
- **medv**: The median value of owner-occupied homes in $1000's

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()

		# import the boston dataset:
		# this dataset looks at features of the boston suburbs and predicts median housing prices
		# the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Housing
		boston <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")

		# set the predictor names and the response column name
		predictors <- colnames(boston)[1:13]

		# this example will predict the medv column
		# you can run the following to see that medv is indeed a numeric value
		h2o.isnumeric(boston["medv"])
		[1] TRUE
		# set the response column to "medv", which is the median value of owner-occupied homes in $1000's
		response <- "medv"

		# convert the `chas` column to a factor 
		# `chas` = Charles River dummy variable (= 1 if tract bounds river; 0 otherwise)
		boston["chas"] <- as.factor(boston["chas"])

		# split into train and test sets
		boston_splits <- h2o.splitFrame(data = boston, ratios = 0.8, seed = 1234)
		train <- boston_splits[[1]]
		test <- boston_splits[[2]]

		# set the `alpha` parameter to 0.25 and train the model
		boston_glm <- h2o.glm(x = predictors, 
		                      y = response, 
		                      training_frame = train,
		                      alpha = 0.25)

		# predict using the GLM model and the testing dataset
		predict <- h2o.predict(object = boston_glm, newdata = test)

		# view a summary of the predictions
		h2o.head(predict)
		   predict
		1 28.29427
		2 19.45689
		3 19.08230
		4 16.90933
		5 16.23141
		6 18.23614

   .. code-tab:: python

		import h2o
		from h2o.estimators.glm import H2OGeneralizedLinearEstimator
		h2o.init()

		# import the boston dataset:
		# this dataset looks at features of the boston suburbs and predicts median housing prices
		# the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Housing
		boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")

		# set the predictor columns
		predictors = boston.columns[:-1]

		# this example will predict the medv column
		# you can run the following to see that medv is indeed a numeric value
		boston["medv"].isnumeric()
		[True]
		# set the response column to "medv", which is the median value of owner-occupied homes in $1000's
		response = "medv"

		# convert the `chas` column to a factor 
		# `chas` = Charles River dummy variable (= 1 if tract bounds river; 0 otherwise)
		boston['chas'] = boston['chas'].asfactor()

		# split into train and testing sets
		train, test = boston.split_frame(ratios = [0.8], seed = 1234)

		# set the `alpha` parameter to 0.25
		# then initialize the estimator then train the model
		boston_glm = H2OGeneralizedLinearEstimator(alpha = 0.25)
		boston_glm.train(x = predictors, 
		                 y = response, 
		                 training_frame = train)

		# predict using the model and the testing dataset
		predict = boston_glm.predict(test)

		# View a summary of the prediction
		predict.head()
		  predict
		---------
		28.2943
		19.4569
		19.0823
		16.9093
		16.2314
		18.2361
		12.6945
		17.5583
		15.4797
		20.7294

		[10 rows x 1 column]

Unsupervised Learning
----------------------

Unsupervised learning algorithms include clustering and anomaly detection methods. Unsupervised learning algorithms such as GLRM and PCA can also be used to perform dimensionality reduction.

Clustering Example
~~~~~~~~~~~~~~~~~~

The example below uses the :ref:`K-Means <kmeans>` algorithm to build a simple clustering model of the Iris dataset.

.. tabs::
   .. code-tab:: r R

    library(h2o)
    h2o.init()

    # Import the iris dataset into H2O:
    iris <- h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

    # Set the predictors:
    predictors <- c("sepal_len", "sepal_wid", "petal_len", "petal_wid")

    # Split the dataset into a train and valid set:
    iris_split <- h2o.splitFrame(data = iris, ratios = 0.8, seed = 1234)
    train <- iris_split[[1]]
    valid <- iris_split[[2]]

    # Build and train the model:
    iris_kmeans <- h2o.kmeans(k = 10, 
                              estimate_k = TRUE, 
                              standardize = FALSE, 
                              seed = 1234, 
                              x = predictors, 
                              training_frame = train, 
                              validation_frame = valid)

    # Eval performance:
    perf <- h2o.performance(iris_kmeans)
    perf

    H2OClusteringMetrics: kmeans
    ** Reported on training data. **

    Total Within SS:  63.09516
    Between SS:  483.8141
    Total SS:  546.9092 
    Centroid Statistics: 
      centroid     size within_cluster_sum_of_squares
    1        1 36.00000                      11.08750
    2        2 51.00000                      30.78627
    3        3 36.00000                      21.22139


   .. code-tab:: python

    import h2o
    from h2o.estimators import H2OKMeansEstimator
    h2o.init()

    # Import the iris dataset into H2O:
    iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

    # Set the predictors:
    predictors = ["sepal_len", "sepal_wid", "petal_len", "petal_wid"]

    # Split the dataset into a train and valid set:
    train, valid = iris.split_frame(ratios=[.8], seed=1234)

    # Build and train the model:
    iris_kmeans = H2OKMeansEstimator(k=10, 
                                     estimate_k=True, 
                                     standardize=False, 
                                     seed=1234)
    iris_kmeans.train(x=predictors, 
                      training_frame=train, 
                      validation_frame=valid)

    # Eval performance:
    perf = iris_kmeans.model_performance()
    perf

    ModelMetricsClustering: kmeans
    ** Reported on train data. **
    
    MSE: NaN
    RMSE: NaN
    Total Within Cluster Sum of Square Error: 63.09516069071749
    Total Sum of Square Error to Grand Mean: 546.9092331233204
    Between Cluster Sum of Square Error: 483.8140724326029

    Centroid Statistics: 
        centroid    size    within_cluster_sum_of_squares
    --  ----------  ------  -------------------------------
        1           36      11.0875
        2           51      30.7863
        3           36      21.2214

Anomaly Detection Example
~~~~~~~~~~~~~~~~~~~~~~~~~

This example uses the :ref:`isoforest` algorithm to detect anomalies in the Electrocardiograms (ECG) dataset.

.. tabs::
   .. code-tab:: r R

	library(h2o)
	h2o.init()

	# import the ecg discord datasets:
	train <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_train.csv")
	test <- h2o.importFile("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_test.csv")

	# train using the `sample_size` parameter:
	isofor_model <- h2o.isolationForest(training_frame = train, 
	                                    sample_size = 5, 
	                                    ntrees = 7, 
	                                    seed = 12345)

	# test the predictions and retrieve the mean_length.
	# mean_length is the average number of splits it took to isolate 
	# the record across all the decision trees in the forest. Records 
	# with a smaller mean_length are more likely to be anomalous 
	# because it takes fewer partitions of the data to isolate them.
	pred <- h2o.predict(isofor_model, test)
	pred
	    predict mean_length
	1 0.5555556    1.857143
	2 0.5555556    1.857143
	3 0.3333333    2.142857
	4 1.0000000    1.285714
	5 0.7777778    1.571429
	6 0.6666667    1.714286

	[23 rows x 2 columns]


   .. code-tab:: python

	import h2o
	from h2o.estimators.isolation_forest import H2OIsolationForestEstimator
	h2o.init()

	# import the ecg discord datasets:
	train = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_train.csv")
	test = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/anomaly/ecg_discord_test.csv")

	# build a model using the `sample_size` parameter:
	isofor_model = H2OIsolationForestEstimator(sample_size = 5, ntrees = 7, seed = 12345) 
	isofor_model.train(training_frame = train)

	# test the predictions and retrieve the mean_length.
	# mean_length is the average number of splits it took to isolate 
	# the record across all the decision trees in the forest. Records 
	# with a smaller mean_length are more likely to be anomalous 
	# because it takes fewer partitions of the data to isolate them.
	pred = isofor_model.predict(test)
	pred
	  predict    mean_length
	---------  -------------
	 0.555556        1.85714
	 0.555556        1.85714
	 0.333333        2.14286
	 1               1.28571
	 0.777778        1.57143
	 0.666667        1.71429
	 0.666667        1.71429
	 0               2.57143
	 0.888889        1.42857
	 0.777778        1.57143

	[23 rows x 2 columns]

Dimensionality Reduction Example
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This code demonstrates how to apply the :ref:`GLRM <glrm>` algorithm for dimensionality reduction on the USArrests dataset using H2O-3. It includes examples in both R and Python for importing data, splitting it into training and validation sets, training the GLRM model, evaluating its performance, and reconstructing the dataset when predict is called. The purpose of this example is to show how GLRM can reduce data dimensionality while preserving essential information, making it useful for feature extraction and data preprocessing.

.. tabs::
   .. code-tab:: r R

    library(h2o)
    h2o.init()

    # Import the USArrests dataset into H2O:
    arrests <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")

    # Split the dataset into a train and valid set:
    arrests_splits <- h2o.splitFrame(data = arrests, ratios = 0.8, seed = 1234)
    train <- arrests_splits[[1]]
    valid <- arrests_splits[[2]]

    # Build and train the model:
    glrm_model = h2o.glrm(training_frame = train, 
                          k = 4, 
                          loss = "Quadratic", 
                          gamma_x = 0.5, 
                          gamma_y = 0.5,  
                          max_iterations = 700, 
                          recover_svd = TRUE, 
                          init = "SVD", 
                          transform = "STANDARDIZE")

    # Eval performance:
    arrests_perf <- h2o.performance(glrm_model)
    arrests_perf
    H2ODimReductionMetrics: glrm
    ** Reported on training data. **

    Sum of Squared Error (Numeric):  1.983347e-13
    Misclassification Error (Categorical):  0
    Number of Numeric Entries:  144
    Number of Categorical Entries:  0

    # Generate predictions on a validation set:
    arrests_pred <- h2o.predict(glrm_model, newdata = valid)
    arrests_pred
      reconstr_Murder reconstr_Assault reconstr_UrbanPop reconstr_Rape
    1       0.2710690        0.2568493       -1.08479880  -0.281431002
    2       2.3535244        0.5080499       -0.39237403   0.357493436
    3      -1.3270945       -1.3460498       -0.60010146  -1.113046938
    4       1.8692325        0.9626034        0.02308083  -0.007606243
    5      -1.3513091       -1.0230776       -1.01555632  -1.468004959
    6       0.8764339        1.5726620        0.09232330   0.560326590

    [14 rows x 4 columns] 

   .. code-tab:: python

    import h2o
    from h2o.estimators import H2OGeneralizedLowRankEstimator
    h2o.init()

    # Import the USArrests dataset into H2O:
    arrestsH2O = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")

    # Split the dataset into a train and valid set:
    train, valid = arrestsH2O.split_frame(ratios = [.8], seed = 1234)

    # Build and train the model:
    glrm_model = H2OGeneralizedLowRankEstimator(k = 4, 
                                                loss = "quadratic", 
                                                gamma_x = 0.5, 
                                                gamma_y = 0.5, 
                                                max_iterations = 700, 
                                                recover_svd = True, 
                                                init = "SVD", 
                                                transform = "standardize")
    glrm_model.train(training_frame=train) 

    # Eval performance
    arrests_perf = glrm_model.model_performance()
    arrests_perf

    ModelMetricsGLRM: glrm
    ** Reported on train data. **

    MSE: NaN
    RMSE: NaN
    Sum of Squared Error (Numeric): 1.983347263428422e-13
    Misclassification Error (Categorical): 0.0

    # Generate predictions on a validation set:
    pred = glrm_model.predict(valid)
    pred
      reconstr_Murder    reconstr_Assault    reconstr_UrbanPop    reconstr_Rape
    -----------------  ------------------  -------------------  ---------------
            0.271069             0.256849           -1.0848         -0.281431
            2.35352              0.50805            -0.392374        0.357493
            -1.32709             -1.34605            -0.600101       -1.11305
            1.86923              0.962603            0.0230808      -0.00760624
            -1.35131             -1.02308            -1.01556        -1.468
            0.876434             1.57266             0.0923233       0.560327
            -0.794373            -0.23359             1.33869        -0.605964
            1.07015              1.03437             0.577021        1.30067
            -0.818588            -0.795801           -0.253889       -0.585681
            -0.0679354           -0.113971            1.61566        -0.352423

    [14 rows x 4 columns]



Training Segments
-----------------

In H2O, you can perform bulk training on segments, or partitions, of the training set. The ``train_segments()`` method in Python and ``h2o.train_segments()`` function in R train an H2O model for each segment (subpopulation) of the training dataset.

Defining a Segmented Model
~~~~~~~~~~~~~~~~~~~~~~~~~~

The ``train_segments()`` function accepts the following parameters:

- **x**: A list of column names or indices indicating the predictor columns.

- **y**: An index or a column name indicating the response column.

- **algorithm** (R only): When building a segmented model in R, specify the algorithm to use. Available options include:

  - ``aggregator`` 
  - ``coxph``
  - ``deeplearning``
  - ``gam``
  - ``gbm``
  - ``glrm`` 
  - ``glm`` 
  - ``isolationforest``
  - ``kmeans``
  - ``naivebayes``
  - ``pca`` 
  - ``psvm``
  - ``randomForest``
  - ``stackedensemble``
  - ``svd``
  - ``targetencoder``
  - ``word2vec`` 
  - ``xgboost``

- **training_frame**: The H2OFrame having the columns indicated by ``x`` and ``y`` (as well as any additional columns specified by ``fold_column``, ``offset_column``, and ``weights_column``).

- **offset_column**: The name or index of the column in the ``training_frame`` that holds the offsets.

- **fold_column**: The name or index of the column in the ``training_frame`` that holds the per-row fold assignments.

- **weights_column**: The name or index of the column in the ``training_frame`` that holds the per-row weights.

- **validation_frame**: The H2OFrame with validation data to be scored on while training.

- **max_runtime_secs**: Maximum allowed runtime in seconds for each model training. Use 0 to disable. Please note that regardless of how this parameter is set, a model will be built for each input segment. This parameter only affects individual model training.

- **segments** (Python)/**segment_columns** (R): A list of columns to segment by. H2O will group the training (and validation) dataset by the segment-by columns and train a separate model for each segment (group of rows). As an alternative to providing a list of columns, users can also supply an explicit enumeration of segments to build the models for. This enumeration needs to be represented as H2OFrame.

- **segment_models_id**: Identifier for the returned collection of Segment Models. If not specified it will be automatically generated.

- **parallelism**: Level of parallelism of the bulk segment models building. This is the maximum number of models each H2O node will be building in parallel.

- **verbose**: Enable to print additional information during model building. Defaults to False.

Segmented Model Example
~~~~~~~~~~~~~~~~~~~~~~~

This code provides an example of training a segmented model using the Titanic dataset, with the goal of predicting survival across different passenger classes. It includes both R and Python implementations for data preparation, model training, and conversion of segmented models to an H2OFrame. A more detailed example is available in the `H2O Segment Model Building <https://github.com/h2oai/h2o-tutorials/blob/master/tutorials/segment_model_building/h2o-segment-model-building.ipynb>`__ demo. 

.. tabs::
   .. code-tab:: r R

	library(h2o)
	h2o.init()

	# import the titanic dataset
	titanic <-  h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")


	# set the predictor and response columns
	predictors <- c("name", "sex", "age", "sibsp", "parch", "ticket", "fare", "cabin")
	response <- "survived"

	# convert the response columnn to a factor
	titanic['survived'] <- as.factor(titanic['survived'])

	# split the dataset into training and validation datasets
	titanic.splits <- h2o.splitFrame(data =  titanic, ratios = .8, seed = 1234)
	train <- titanic.splits[[1]]
	valid <- titanic.splits[[2]]

	# train a segmented model by iterating over the pclass column
	titanic_models <- h2o.train_segments(algorithm = "gbm",
	                                     segment_columns = "pclass",
	                                     x = predictors,
	                                     y = response,
	                                     training_frame = train,
	                                     validation_frame = valid,
	                                     seed = 1234)

	# convert the segmented models to an H2OFrame
	as.data.frame(titanic_models)


   .. code-tab:: python

	import h2o
	from h2o.estimators.gbm import H2OGradientBoostingEstimator
	h2o.init()

	# import the titanic dataset:
	titanic = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")

	# set the predictor and response columns
	predictors = ["name", "sex", "age", "sibsp", "parch", "ticket", "fare", "cabin"]
	response = "survived"

	# convert the response columnn to a factor
	titanic[response] = titanic[response].asfactor()

	# split the dataset into training and validation datasets
	train, valid = titanic.split_frame(ratios = [.8], seed = 1234)

	# train a segmented model by iterating over the plcass column
	titanic_gbm = H2OGradientBoostingEstimator(seed = 1234)
	titanic_models = titanic_gbm.train_segments(segments = ["pclass"],
	                                            x = predictors,
	                                            y = response,
	                                            training_frame = train,
	                                            validation_frame = valid)

	# convert the segmented models to an H2OFrame
	titanic_models.as_frame()

