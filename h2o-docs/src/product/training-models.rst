Training Models
===============

With H2O-3, you can train supervised, unsupervised, and Word2Vec models.

Supervised Learning
-------------------

In supervised learning, the dataset is labeled with the answer that algorithm should come up with. Supervised learning takes input variables (x) along with an output variable (y). The output variable represents the column that you want to predict on. The algorithm then uses these variables to learn and approximate the mapping function from the input to the output. Supervised learning algorithms support classification and regression problems.  

Classification and Regression Problems
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In classification problems, the output variable (y) is a categorical value. The answer can be binary (answer is yes/no), or it can be multiclass (answer is dog/cat/mouse).

In regression problems, the output variable (y) is a real or continuous value. An example would be predicting someone's age or predicting the median value of a house. 

Changing the Column Type
''''''''''''''''''''''''

H2O algorithms will treat a problem as a classification problem if the column type is ``factor`` or a regression problem if the column type is ``numeric``. You can force H2O to use either classification or regression by changing the column type.

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()

		# import the boston housing dataset:
		boston <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")

		# check the column type for the `chas` column
		h2o.isnumeric(boston["chas"])
		[1] TRUE

		# change the column type to a factor
		boston["chas"] <- as.factor(boston["chas"])
		# verify that the column is now a factor
		h2o.isfactor(boston["chas"])
		[1] TRUE

		# change the column type back to numeric
		boston["chas"] <- as.numeric(boston["chas"])
		# verify that the column is numeric and not a factor
		h2o.isfactor(boston["chas"])
		[1] FALSE
		h2o.isnumeric(boston["chas"])
		[1] TRUE

   .. code-tab:: python

		import h2o
		from h2o.estimators.glm import H2OGeneralizedLinearEstimator
		h2o.init()

		# import the boston dataset:
		boston = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv")

		# check the column type for the `chas` column
		boston["chas"].isnumeric()
		[True]
		# change the column type to a factor
		boston['chas'] = boston['chas'].asfactor()
		# verify that the column is now a factor
		boston["chas"].isfactor()
		[True]

		# change the column type back to numeric
		boston["chas"] = boston["chas"].asfactor()
		# verify that the column is numeric and not a factor
		boston["chas"].isfactor()
		[False]
		boston["chas"].isnumeric()
		[True]

Classification Example
''''''''''''''''''''''

This example uses the Prostate dataset and :ref:`H2O's GLM algorithm <glm>` to predict the likelihood of a patient being diagnosed with prostate cancer. The dataset includes the following columns:

- **ID**: A row identifier. This can be dropped from the list of predictors.
- **CAPSULE**: Whether the tumor prenetrated the prostatic capsule
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
    # and initiliaze model training
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

This example uses the Boston Housing data and :ref:`H2O's GLM algorithm <glm>` to predict the median home based using all available features. The dataset includes the following columns:

- **crim**: The per capita crime rate by town
- **zn**: The proportion of residential land zoned for lots over 25,000 sq.ft
- **indus**: The proportion of non-retail business acres per town
- **chas**: A Charles River dummy variable (1 if the tract bounds the Charles river; 0 otherwise)
- **nox**: Nitric oxides concentration (parts per 10 million)
- **rm**: The average number of rooms per dwelling
- **age**: The proportion of owner-occupied units built prior to 1940
- **dis**: The weighted distances to five Boston employment centres
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
		boston_splits <- h2o.splitFrame(data =  boston, ratios = 0.8, seed = 1234)
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
---------------------

In unsupervised learning, the model is provided with a dataset that isn't labeled - i.e., without an explicit outcome that the algorithm should return. In this case, the algorithm attempts to find patterns and structure in the data by extracting useful features. The model organizes the data in different ways, depending on the algorithm (clustering, anomaly detection, autoencoders, etc). 

Clustering Example
~~~~~~~~~~~~~~~~~~

The example below uses K-Means to perform a simple clustering model.



Training Segments
-----------------


