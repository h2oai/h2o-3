``distribution``
----------------

- Available in: GBM, Deep Learning, XGBoost, AutoML
- Hyperparameter: yes

Description
~~~~~~~~~~~

Unlike in GLM, where users specify both a distribution ``family`` and a ``link`` for the loss function, in GBM, Deep Learning, and XGBoost, distributions and loss functions are tightly coupled. In these algorithms, a loss function is specified using the ``distribution`` parameter. When specifying the distribution, the loss function is automatically selected as well. For exponential families (such as Poisson, Gamma, and Tweedie), the canonical logarithmic link function is used.

By default, the loss function method performs AUTO distribution. In this case, the algorithm will guess the model type based on the response column type (specified using ``y``). More specifically, if the response column type is numeric, AUTO defaults to "gaussian"; if categorical, AUTO defaults to bernoulli or multinomial depending on the number of response categories.

Certain cases can exist, however, in which the median starting value for this loss function can lead to poor results (for example, if the median is the lowest or highest value in a tree node). The ``distribution`` option allows you to specify a different method. Available methods include AUTO, bernoulli, multinomial, gaussian, poisson, gamma, laplace, quantile, huber, and tweedie.

- If the distribution is ``bernoulli``, the response column must be 2-class categorical.
- If the distribution is ``quasibinomial``, the response column must be numeric and binary. (Available in GBM only.)
- If the distribution is ``multinomial``, the response column must be categorical.
- If the distribution is ``gaussian``, the response column must be numeric.
- If the distribution is ``poisson``, the response column must be numeric.
- If the distribution is ``gamma``, the response column must be numeric.
- If the distribution is ``laplace``, the response column must be numeric.
- If the distribution is ``quantile``, the response column must be numeric.
- If the distribution is ``huber``, the response column must be numeric.
- If the distribution is ``modified_huber``, the response column must be 2-class categorical.
- If the distribution is ``tweedie``, the response column must be numeric.
- If the distribution is ``ordinal``, the response column must be categorical with at least 3 levels.
- If the distribution is ``custom``, the response column must be numeric/binary/categorical depends on type of custom distribution.

**NOTE**: ``laplace``, ``quantile``, and ``huber`` are NOT available in XGBoost. ``custom`` is available ONLY in GBM.

The following general guidelines apply when selecting a distribution:

 For Classification problems:

 - Bernoulli and Quasibinomial distributions are used for binary outcomes.
 - A Multinomial distribution can handle multiple discrete outcomes.

 For Regression problems:

 - A Gaussian distribution is the function for continuous targets.
 - A Poisson distribution is used for estimating counts.
 - A Gamma distribution is used for estimating total values (such as claim payouts, rainfall, etc.).
 - A Tweedie distribution is used for estimating densities. 
 - A Laplacian loss function (absolute L1-loss function) can predict the median percentile.
 - A Quantile regression loss function can predict a specified percentile.
 - A Huber loss function, a combination of squared error and absolute error, is more robust to outliers than L2 squared-loss function. 

When ``quasibinomial`` is specified, the response must be numeric and binary. The response must also have a low value of 0 (negative class). Note that this option is available in GBM only.

When ``tweedie`` is specified, users must also specify a ``tweedie_power`` value. Users can tune over this option with values > 1.0 and < 2.0. More information is available `here <https://en.wikipedia.org/wiki/Tweedie_distribution>`__.	

When ``quantile`` is specified, then users can also specify a ``quantile_alpha`` value, which defines the desired quantile when performing quantile regression. For example, if you want to predict the 80th percentile of a column's value, then you can specify ``quantile_alpha=0.8``. The ``quantile_alpha`` value defaults to 0.5 (i.e., the median value, and essentially the same as specifying ``distribution=laplace``). Note that this option is not available in XGBoost. 

When ``huber`` is specified, then users can also specify a ``huber_alpha`` value. This indicates the top percentile of error that should be considered as outliers. Note that this option is not available in XGBoost.

When ``custom`` is specified, then users must also specify a ``custom_distribution_func`` value. This is reference to loaded custom distribution function. For information how to load custom distribution see `custom_distribution_func <custom_distribution_func.html>`__. If ``custom`` distribution is set, no deviance metrics (training deviance, validation deviance and mean deviance) are calculated.

For all distributions except ``multinomial`` and ``custom``, you can specify an ``offset_column``. Offsets are per-row “bias values” that are used during model training. For Gaussian distributions, they can be seen as simple corrections to the response (y) column. Instead of learning to predict the response (y-row), the model learns to predict the (row) offset of the response column. For other distributions, the offset corrections are applied in the linearized space before applying the inverse link function to get the actual response values. For more information, refer to the following `link <offset_column.html>`__. 

Some examples of response distributions are provided below.

**Gaussian Distribution**

.. figure:: ../../images/gaussian.png
   :alt: Gaussian distribution

**Poisson Distribution**

.. figure:: ../../images/poisson.png
   :alt: Poisson distribution

**Laplace Distribution**

.. figure:: ../../images/laplace.png
   :alt: Laplace distribution

**Bernoulli Distribution**

.. figure:: ../../images/bernoulli.png
   :alt: Bernoulli distribution

Equations
~~~~~~~~~

Where:

- :math:`y` is a true response
- :math:`f` is a predicted response
- :math:`w` is weight

+-------------------+-------------------------------------------------------------------------------------------+
| Distribution Type | Equation                                                                                  |
+===================+===========================================================================================+
| Bernoulli         | :math:`-2\times w\times(y\times\log(f))+(1-y)\times\log(1-f))`                            |
+-------------------+-------------------------------------------------------------------------------------------+
| Quasibinomial     | :math:`f= \begin{cases} -2\times w\times y\times\log(f) & \text{for } f>1 \\              |
|                   | -2\times w\times(1-y)\times\log(1-f) & \text{for } f<0 \\ 0 & \text{for } f=y \\          |
|                   | -2\times w\times (y \times\log(f)+(1-y)\times\log(1-f)) & \text{otherwise}                |
|                   | \\\end{cases}`                                                                            |
+-------------------+-------------------------------------------------------------------------------------------+
| Gaussian          | - Equivalent to wMSE (weighted mean squared error):                                       |
|                   | - :math:`w \times(y-f)^2`                                                                 |
+-------------------+-------------------------------------------------------------------------------------------+
| Poisson           | - With :math:`f\text{_}link=link(f)`                                                      |
|                   | - :math:`-2\times w\times(y\times f\text{_}link- \exp(f\text{_}link))`                    |
+-------------------+-------------------------------------------------------------------------------------------+
| Gamma             | - With :math:`f\text{_}link=link(f)`                                                      |
|                   | - :math:`2\times w\times (y\times \exp(-f\text{_}link)+f\text{_}link)`                    |
+-------------------+-------------------------------------------------------------------------------------------+
| Laplace           | :math:`w \text{ }\times\mid{y-f}\mid`                                                     |
+-------------------+-------------------------------------------------------------------------------------------+
| Quantile          | :math:`f= \begin{cases}w\times QuantileAlpha \times(y-f) & \text{for }y>f \\              |
|                   | w\times(1-QuantileAlpha)\times(f-y)& \text{for } y \leq f \\\end{cases}`                  |
+-------------------+-------------------------------------------------------------------------------------------+
| Huber             | :math:`f= \begin{cases} w\times(y-f)^2 & \text{for } \mid{y-f}\mid \leq HuberDelta &      |
|                   | \text{(Equivalent to wMSE)} \\                                                            |
|                   | w\times(2 \times \mid{y-f}\mid - HuberDelta)\times HuberDelta & \text{for } y \leq f &    |
|                   | \text{(Equivalent to w * (2 * MAE - delta) * delta)} \\\end{cases}`                       |
+-------------------+-------------------------------------------------------------------------------------------+
| Modified_Huber    | :math:`\text{With } z=(2\times y-1)\times f \\                                            |
|                   | f= \begin{cases}-4\times w\times z & \text{for } z\leq 1 \\                               |
|                   | 0 & \text{for } z>1 \\                                                                    |
|                   | w\times z^2 & \text{otherwise} \\\end{cases}`                                             |
+-------------------+-------------------------------------------------------------------------------------------+
| Tweedie           | with :math:`f=\text{link}(f)`, requires :math:`1>\text{Tweedie_Power}<2`:                 |
|                   | :math:`2\times w\times y^{(2-\text{Tweedie_Power})/((1-\text{Tweedie_Power})\times(2-     |
|                   | \text{Tweedie_Power}))}-y \times \exp(f\times(1-\text{Tweedie_Power}))/ (1-\text          |
|                   | {Tweedie_Power})+ \exp(f\times(2-\text{Tweedie_Power}))/(2-\text{Tweedie_Power})`         |
+-------------------+-------------------------------------------------------------------------------------------+

+-------------------+---------------------------------------------------------------------------+
| Link/Inverse Link | Equation                                                                  |
| Functions         |                                                                           |
+===================+===========================================================================+
| Identity: Gaussian| - :math:`link(f)=f`                                                       |
| Huber, Laplace,   | - :math:`linkInversion(f)=f`                                              |
| Quantile          |                                                                           |
+-------------------+---------------------------------------------------------------------------+
| Log: Multinomial, | - :math:`link(f)=\log(f)`                                                 |
| Poisson, Gamma,   | - :math:`linkInversion(f)= \exp(f)`                                       |
| Tweedie           |                                                                           |
+-------------------+---------------------------------------------------------------------------+
| Logit: Bernoulli, | - :math:`link(f)=(f/(f-1))`                                               |
| Quasibinomial,    | - :math:`linkInversion(f)=1/(1+ \exp(-f))`                                |
| Modified_Huber    |                                                                           |
+-------------------+---------------------------------------------------------------------------+
| Inversion         | :math:`link(f)= linkInversion(f) = \begin{cases}min(-1e-5, f)             |
|                   | & \text{for }f<0 \\ max(-1e-5, f)& \text{for }f>0 \\\end{cases}`          |
+-------------------+---------------------------------------------------------------------------+
| Ologit            | - :math:`link(f)=\log(f/(1-f))`                                           |
|                   | - :math:`linkInversion(f)=1/(1+ \exp(-f))`                                |
+-------------------+---------------------------------------------------------------------------+
| Ologlog           | - :math:`link(f)=\log(-1\times\log(1-f))`                                 |
|                   | - :math:`linkInversion(f)=1- \exp(-1\times \exp(f))`                      |
+-------------------+---------------------------------------------------------------------------+
| Oprobit           | - :math:`link(f)=normalDistribution.inverseCumulativeProbability(f)`      |
|                   | - :math:`linkInversion(f)=normalDistribution.cumulativeProbability(f)`    |
+-------------------+---------------------------------------------------------------------------+

**Note**: Inversion, Ologit, Ologlog, and Oprobit are not associated with concrete distributions but can be used with a custom distribution.

AutoML
~~~~~~

AutoML supports distribution parameter by passing the ``distribution`` to the individual algos, in case of GLM ``distribution`` is converted to
a corresponding family. Algos that don't support the specified distribution will use the default ``AUTO`` distribution which corresponds to
``bernoulli`` for binary classification, ``multinomial`` for multinomial classification, and ``gaussian`` for regression.

**Available distributions in AutoML:**

+--------------------+--------------+-----+-----+-----+---------+
|                    | DeepLearning | DRF | GBM | GLM | XGBoost |
+====================+==============+=====+=====+=====+=========+
| Bernoulli/Binomial |       X      |  X  |  X  |  X  |    X    |
+--------------------+--------------+-----+-----+-----+---------+
| Multinomial        |       X      |  X  |  X  |  X  |    X    |
+--------------------+--------------+-----+-----+-----+---------+
| Gaussian           |       X      |  X  |  X  |  X  |    X    |
+--------------------+--------------+-----+-----+-----+---------+
| Poisson            |       X      |     |  X  |  X  |    X    |
+--------------------+--------------+-----+-----+-----+---------+
| NegativeBinomial   |              |     |     |  X  |         |
+--------------------+--------------+-----+-----+-----+---------+
| Gamma              |       X      |     |  X  |  X  |    X    |
+--------------------+--------------+-----+-----+-----+---------+
| Laplace            |       X      |     |  X  |     |         |
+--------------------+--------------+-----+-----+-----+---------+
| Quantile           |       X      |     |  X  |     |         |
+--------------------+--------------+-----+-----+-----+---------+
| Huber              |       X      |     |  X  |     |         |
+--------------------+--------------+-----+-----+-----+---------+
| Tweedie            |       X      |     |  X  |  X  |    X    |
+--------------------+--------------+-----+-----+-----+---------+


.. tabs::
   .. code-tab:: r R

        library(h2o)
        h2o.init()

        # import the cars dataset:
        # this dataset is used to classify whether or not a car is economical based on
        # the car's displacement, power, weight, and acceleration, and the year it was made
        cars <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

        # set the predictor names and the response column name
        predictors <- c("displacement","power", "weight", "acceleration", "year")
        response <- "cylinders"

        # split into train and validation sets
        cars_splits <- h2o.splitFrame(data =  cars, ratios = 0.8, seed = 1234)
        train <- cars_splits[[1]]
        valid <- cars_splits[[2]]

        # try using the distribution parameter:
        # train AutoML
        aml_poisson <- h2o.automl(x = predictors, y = response, training_frame = train,
                                  validation_frame = valid,
                                  distribution = "poisson",
                                  max_models = 10,
                                  seed = 1234)
        print(aml_poisson@leaderboard)

        # AutoML with tweedie distribution with default value of tweedie_power=1.5
        aml_tweedie <- h2o.automl(x = predictors, y = response, training_frame = train,
                                  validation_frame = valid,
                                  distribution = "tweedie",
                                  max_models = 10,
                                  seed = 1234)
        print(aml_tweedie@leaderboard)

        # AutoML with tweedie distribution with a specified value of tweedie_power=1.75
        aml_tweedie2 <- h2o.automl(x = predictors, y = response, training_frame = train,
                                   validation_frame = valid,
                                   distribution = list(distribution = "tweedie", tweedie_power = 1.75),
                                   max_models = 10,
                                   seed = 1234)
        print(aml_tweedie2@leaderboard)

   .. code-tab:: python

        import h2o
        from h2o.automl import H2OAutoML
        h2o.init()

        # import the cars dataset:
        # this dataset is used to classify whether or not a car is economical based on
        # the car's displacement, power, weight, and acceleration, and the year it was made
        cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

        # set the predictor names and the response column name
        predictors = ["displacement","power","weight","acceleration","year"]
        response = "cylinders"

        # split into train and validation sets
        train, valid = cars.split_frame(ratios=[.8], seed=1234)

        # try using the distribution parameter:
        # Initialize and train AutoML
        aml_poisson = H2OAutoML(distribution="poisson", seed=1234, max_models=10)
        aml_poisson.train(x=predictors, y=response, training_frame=train, validation_frame=valid)
        print(aml_poisson.leaderboard)

        # AutoML with tweedie distribution with default value of tweedie_power=1.5
        aml_tweedie = H2OAutoML(distribution="tweedie", seed=1234, max_models=10)
        aml_tweedie.train(x=predictors, y=response, training_frame=train, validation_frame=valid)
        print(aml_tweedie.leaderboard)

        # AutoML with tweedie distribution with a specified value of tweedie_power=1.75
        aml_tweedie2 = H2OAutoML(distribution=dict(distribution="tweedie", tweedie_power=1.75), seed=1234, max_models=10)
        aml_tweedie2.train(x=predictors, y=response, training_frame=train, validation_frame=valid)
        print(aml_tweedie2.leaderboard)


Related Parameters
~~~~~~~~~~~~~~~~~~

- `huber_alpha <huber_alpha.html>`__
- `offset_column <offset_column.html>`__
- `quantile_alpha <quantile_alpha.html>`__
- `tweedie_power <tweedie_power.html>`__
- `y <y.html>`__
- `custom_distribution_func <custom_distribution_func.html>`__

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


		# set the predictor names and the response column name
		predictors <- c("displacement","power", "weight", "acceleration", "year")
		response <- "cylinders"

		# split into train and validation sets
		cars_splits <- h2o.splitFrame(data =  cars, ratios = 0.8, seed = 1234)
		train <- cars_splits[[1]]
		valid <- cars_splits[[2]]

		# try using the distribution parameter:
		# train a GBM
		car_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
		                   validation_frame = valid,
		                   distribution = "poisson",
		                   seed = 1234)

		# print the MSE for your validation data
		print(h2o.mse(car_gbm, valid = TRUE))

   .. code-tab:: python

		import h2o
		from h2o.estimators.gbm import H2OGradientBoostingEstimator
		h2o.init()

		# import the cars dataset:
		# this dataset is used to classify whether or not a car is economical based on
		# the car's displacement, power, weight, and acceleration, and the year it was made
		cars = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

		# set the predictor names and the response column name
		predictors = ["displacement","power","weight","acceleration","year"]
		response = "cylinders"

		# split into train and validation sets
		train, valid = cars.split_frame(ratios = [.8], seed = 1234)

		# try using the distribution parameter:
		# Initialize and train a GBM
		cars_gbm = H2OGradientBoostingEstimator(distribution = "poisson", seed = 1234)
		cars_gbm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

		# print the MSE for the validation data
		cars_gbm.mse(valid=True)
