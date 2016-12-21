``link``
--------

- Available in: GLM
- Hyperparameter: no

Description
~~~~~~~~~~~

GLM problems consist of three main components:

- A random component :math:`f` for the dependent variable :math:`y`: The density function :math:`f(y;\theta,\phi)` has a probability distribution from the exponential family parametrized by :math:`\theta` and :math:`\phi`. This removes the restriction on the distribution of the error and allows for non-homogeneity of the variance with respect to the mean vector. 
- A systematic component (linear model) :math:`\eta`: :math:`\eta = X\beta`, where :math:`X` is the matrix of all observation vectors :math:`x_i`.
- A link function :math:`g`: :math:`E(y) = \mu = {g^-1}(\eta)` relates the expected value of the response :math:`\mu` to the linear component :math:`\eta`. The link function can be any monotonic differentiable function. This relaxes the constraints on the additivity of the covariates, and it allows the response to belong to a restricted range of values depending on the chosen transformation :math:`g`. 

Accordingly, in order to specify a GLM problem, you must choose a family function :math:`f`, link function :math:`g`, and any parameters needed to train the model. 

H2O's GLM supports the following link functions: Family_Default, Identity, Logit, Log, Inverse, and Tweedie.

The following table describes the allowed Family/Link combinations.

+----------------+-------------------------------------------------------------+
| **Family**     | **Link Function**                                           |
+----------------+----------------+----------+-------+-----+---------+---------+
|                | Family_Default | Identity | Logit | Log | Inverse | Tweedie |
+----------------+----------------+----------+-------+-----+---------+---------+
| Binomial       | X              |          | X     |     |         |         |
+----------------+----------------+----------+-------+-----+---------+---------+
| Quasibinomial  | X              |          | X     |     |         |         |
+----------------+----------------+----------+-------+-----+---------+---------+
| Multinomial    | X              |          |       |     |         |         |
+----------------+----------------+----------+-------+-----+---------+---------+
| Gaussian       | X              | X        |       | X   | X       |         |
+----------------+----------------+----------+-------+-----+---------+---------+
| Poisson        | X              | X        |       | X   |         |         |
+----------------+----------------+----------+-------+-----+---------+---------+
| Gamma          | X              | X        |       | X   | X       |         |
+----------------+----------------+----------+-------+-----+---------+---------+
| Tweedie        | X              |          |       |     |         | X       |
+----------------+----------------+----------+-------+-----+---------+---------+

Refer to the `Links <../glm.html#links>`__ section for more information. 

Related Parameters
~~~~~~~~~~~~~~~~~~

- `family <family.html>`__

Example
~~~~~~~

.. example-code::
   .. code-block:: r

	library(h2o)
	h2o.init()

	# import the iris dataset:
	# this dataset is used to classify the type of iris plant
	# the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Iris
	iris <- h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

	# convert response column to a factor
	iris['class'] <- as.factor(iris['class'])

	# set the predictor names and the response column name
	predictors <- colnames(iris)[-length(iris)]
	response <- 'class'

	# split into train and validation
	iris.splits <- h2o.splitFrame(data = iris, ratios = .8)
	train <- iris.splits[[1]]
	valid <- iris.splits[[2]]

	# try using the `link` parameter:
	iris_glm <- h2o.glm(x = predictors, y = response, family = 'multinomial', link = 'family_default',
	                   training_frame = train, validation_frame = valid)

	# print the logloss for the validation data
	print(h2o.logloss(iris_glm, valid = TRUE))
   
   .. code-block:: python

	import h2o
	from h2o.estimators.glm import H2OGeneralizedLinearEstimator
	h2o.init()

	# import the iris dataset:
	# this dataset is used to classify the type of iris plant
	# the original dataset can be found at https://archive.ics.uci.edu/ml/datasets/Iris
	iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")

	# convert response column to a factor
	iris['class'] = iris['class'].asfactor()

	# set the predictor names and the response column name
	predictors = iris.columns[:-1]
	response = 'class'

	# split into train and validation sets
	train, valid = iris.split_frame(ratios = [.8])

	# try using the `link` parameter:
	# Initialize and train a GLM
	iris_glm = H2OGeneralizedLinearEstimator(family = 'multinomial', link = 'family_default')
	iris_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

	# print the logloss for the validation data
	iris_glm.logloss(valid = True)
