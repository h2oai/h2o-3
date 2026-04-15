``family``
----------

- Available in: GLM, GAM, HGLM
- Hyperparameter: no

Description
~~~~~~~~~~~

GLM and GAM problems consist of three main components:

- A random component :math:`f` for the dependent variable :math:`y`: The density function :math:`f(y;\theta,\phi)` has a probability distribution from the exponential family parametrized by :math:`\theta` and :math:`\phi`. This removes the restriction on the distribution of the error and allows for non-homogeneity of the variance with respect to the mean vector. 
- A systematic component (linear model) :math:`\eta`: :math:`\eta = X\beta`, where :math:`X` is the matrix of all observation vectors :math:`x_i`.
- A link function :math:`g`: :math:`E(y) = \mu = {g^-1}(\eta)` relates the expected value of the response :math:`\mu` to the linear component :math:`\eta`. The link function can be any monotonic differentiable function. This relaxes the constraints on the additivity of the covariates, and it allows the response to belong to a restricted range of values depending on the chosen transformation :math:`g`. 

Accordingly, in order to specify a GLM problem, you must choose a family function :math:`f`, link function :math:`g`, and any parameters needed to train the model.

You can specify one of the following ``family`` options based on the response column type:

- ``gaussian``: The data must be numeric (Real or Int). This is the default family.
- ``binomial``: The data must be categorical 2 levels/classes or binary (Enum or Int).
-  If the family is **fractionalbinomial**, the response must be a numeric between 0 and 1.
- ``ordinal``: The data must be categorical with at least 3 levels. 
- ``quasibinomial``: The data must be numeric.
- ``multinomial``: The data can be categorical with more than two levels/classes (Enum).
- ``poisson``: The data must be numeric and non-negative (Int).
- ``gamma``: The data must be numeric and continuous and positive (Real or Int).
- ``tweedie``: The data must be numeric and continuous (Real) and non-negative.
- ``negativebinomial``: The data must be numeric and non-negative (Int).
- ``AUTO``: The family can fall into three cases based on the response:
		
		- If the data is **Enum** with cardinality = 2, then the family is automatically determined as **binomial**.
		- If the data is **Enum** with cardinality > 2, then the family is automatically determined as **multinomial**.
		- If the data is numeric (**Real** or **Int**), then the family is automatically determined as **gaussian**.

Refer to the `Families <../glm.html#families>`__ section for detailed information about each family option. 

**Note**: If your response column is binomial, then you must convert that column to a categorical (``.asfactor()`` in Python and ``as.factor()`` in R) and set ``family = binomial``. The following configurations can lead to unexpected results. 

 - If you DO convert the response column to categorical and DO NOT to set ``family=binomial``, then you will receive an error message.
 - If you DO NOT convert response column to categorical and DO NOT set the family, then the algorithm will assume the 0s and 1s are numbers and will provide a Gaussian solution to a regression problem.

Related Parameters
~~~~~~~~~~~~~~~~~~

- `link <link.html>`__

Example
~~~~~~~

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()

		# import the cars dataset:
		# this dataset is used to classify whether or not a car is economical based on
		# the car's displacement, power, weight, and acceleration, and the year it was made
		cars <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

		# convert response column to a factor
		cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])

		# set the predictor names and the response column name
		predictors <- c("displacement", "power", "weight", "acceleration", "year")
		response <- "economy_20mpg"

		# split into train and validation
		cars_splits <- h2o.splitFrame(data = cars, ratios = 0.8)
		train <- cars_splits[[1]]
		valid <- cars_splits[[2]]

		# try using the `family` parameter:
		car_glm <- h2o.glm(x = predictors, y = response, family = 'binomial', training_frame = train, 
		                   validation_frame = valid)

		# print the auc for your validation data
		print(h2o.auc(car_glm, valid = TRUE))
   
   .. code-tab:: python

		import h2o
		from h2o.estimators.glm import H2OGeneralizedLinearEstimator
		h2o.init()

		# import the cars dataset:
		# this dataset is used to classify whether or not a car is economical based on
		# the car's displacement, power, weight, and acceleration, and the year it was made
		cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

		# convert response column to a factor
		cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

		# set the predictor names and the response column name
		predictors = ["displacement","power","weight","acceleration","year"]
		response = "economy_20mpg"

		# split into train and validation sets
		train, valid = cars.split_frame(ratios = [.8])

		# try using the `family` parameter:
		# Initialize and train a GLM
		cars_glm = H2OGeneralizedLinearEstimator(family = 'binomial')
		cars_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the auc for the validation data
		cars_glm.auc(valid = True)

