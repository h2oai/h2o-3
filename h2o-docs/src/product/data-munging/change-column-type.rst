.. _change-column-type:

Changing the Column Type
------------------------

H2O algorithms will treat a problem as a classification problem if the column type is ``factor`` and a regression problem if the column type is ``numeric``. You can force H2O to use either classification or regression by changing the column type.

.. tabs::
   .. code-tab:: r R

		library(h2o)
		h2o.init()

		# Import the cars dataset:
		cars_df <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

		# Check the column type for the "cylinders" column:
		print(h2o.isnumeric(cars_df["cylinders"]))
		#TRUE

		# Change the column type to a factor:
		cars_df["cylinders"] <- as.factor(cars_df["cylinders"])

		# Verify that the column is now a factor:
		print(h2o.isfactor(cars_df["cylinders"]))
		#TRUE

		# Change the column type back to numeric:
		cars_df["cylinders"] <- as.numeric(cars_df["cylinders"])
		# Verify that the column is now numeric and not a factor:
		print(h2o.isfactor(cars_df["cylinders"]))
		#FALSE
		print(h2o.isnumeric(cars_df["cylinders"]))
		#TRUE

		# Change multiple columns to factors:
		cars_df[c("cylinders","economy_20mpg")] <- as.factor(cars_df[c("cylinders","economy_20mpg")])

		# Verify that the columns are now factors:
		print(h2o.isfactor(cars_df[c("cylinders","economy_20mpg")]))
		# TRUE TRUE


   .. code-tab:: python

		import h2o
		h2o.init()

		# Import the cars dataset:
		cars_df = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

		# Check the column type for the 'cylinders' column:
		print(cars_df['cylinders'].isnumeric())
		#[True]

		# Change the column type to a factor:
		cars_df['cylinders'] = cars_df['cylinders'].asfactor()

		# Verify that the column is now a factor:
		print(cars_df['cylinders'].isfactor())
		#[True]

		# Change the column type back to numeric:
		cars_df["cylinders"] = cars_df["cylinders"].asnumeric()
		# Verify that the column is now numeric and not a factor:
		print(cars_df['cylinders'].isfactor())
		#[False]
		print(cars_df['cylinders'].isnumeric())
		#[True]

		# Reload data:
		cars_df = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")

		# Change multiple columns to factors:
		cars_df[['cylinders','economy_20mpg']] = cars_df[['cylinders','economy_20mpg']].asfactor()

		# Verify that the columns are now factors:
		print(cars_df[['cylinders','economy_20mpg']].isfactor())
		# [True, True]


If the column type is ``enum`` and you want to convert it to ``numeric``, you should first convert it to ``character`` then convert it to ``numeric``. Otherwise, the values may be converted to underlying factor values, not the expected mapped values.

.. tabs::
	.. code-tab:: r R

		# Using the data from the above example, convert the 'name' column  to numeric:
		cars_df["name"] <- as.character(cars_df["name"])
		cars_df["name"] <- as.numeric(cars_df["name"])


	.. code-tab:: python

		# Using the data from the above example, convert the 'name' column  to numeric:
		cars_df['name'] = cars_df['name'].ascharacter().asnumeric()

Converting Dates Columns
~~~~~~~~~~~~~~~~~~~~~~~~

H2O represents dates as (unix) timestamps. These are then raw input to the algorithm, however, this is not very useful in most cases. You are expected to do your own feature engineering and break the data into day, month, and year using the functions H2O provides.

.. tabs::
	.. code-tab:: r R

		library(h2o)
		h2o.init()

		# convert the frame (containing strings / categoricals) into the date format:
		hdf <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/jira/v-11-eurodate.csv")
		h2o.as_date(hdfs["ds5"], c("%d.%m.%y %H:%M"))


	.. code-tab:: python

		import h2o
		h2o.init()

		# convert the frame (containing strings / categoricals) into the date format:
		hdf = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/jira/v-11-eurodate.csv")
		hdf["ds5"].as_date("%d.%m.%y %H:%M")

		