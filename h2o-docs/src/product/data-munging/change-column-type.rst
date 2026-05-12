.. _change-column-type:

Change the Column Type
======================

You can change the column type using H2O-3's capabilities.

``factor`` and ``numeric``
--------------------------

H2O-3 algorithms will treat a problem as a classification problem if the column type is ``factor`` and as a regression problem if the column type is ``numeric``. You can force H2O-3 to use either classification or regression by changing the column type.

.. tabs::
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

``enum`` and ``numeric``
------------------------

If the column type is ``enum`` and you want to convert it to ``numeric``, you should first convert it to ``character`` then convert it to ``numeric``. Otherwise, the values may be converted to underlying factor values, not the expected mapped values.

.. tabs::
	.. code-tab:: python

		# Using the data from the above example, convert the 'name' column  to numeric:
		cars_df['name'] = cars_df['name'].ascharacter().asnumeric()

	.. code-tab:: r R

		# Using the data from the above example, convert the 'name' column  to numeric:
		cars_df["name"] <- as.character(cars_df["name"])
		cars_df["name"] <- as.numeric(cars_df["name"])

Convert dates columns
---------------------

H2O-3 represents dates as (unix) timestamps. These are then raw input to the algorithm. However, this is not very useful in most cases. You are expected to do your own feature engineering and break the data into day, month, and year using the functions H2O-3 provides.

.. tabs::
	.. code-tab:: python

		import h2o
		h2o.init()

		# convert the frame (containing strings / categoricals) into the date format:
		hdf = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/jira/v-11-eurodate.csv")
		hdf["ds5"].as_date("%d.%m.%y %H:%M")

		# You can also access the date/time information from the raw data.
		# Access the day of week:
		hdf["ds3"].dayOfWeek()

		# Access the year, month, week, and day:
		hdf["ds3"].year()
		hdf["ds3"].month()
		hdf["ds3"].week()
		hdf["ds3"].day()

		# Access the hour, minute, and second:
		hdf["ds3"].hour()
		hdf["ds3"].minute()
		hdf["ds3"].second()

	.. code-tab:: r R

		library(h2o)
		h2o.init()

		# convert the frame (containing strings / categoricals) into the date format:
		hdf <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/jira/v-11-eurodate.csv")
		h2o.as_date(hdf["ds5"], c("%d.%m.%y %H:%M"))

		# You can also access the date/time information from the raw data.
		# Access the day of week:
		h2o.dayOfWeek(hdf["ds3"])

		# Access the year, month, week, and day:
		h2o.year(hdf["ds3"])
		h2o.month(hdf["ds3"])
		h2o.week(hdf["ds3"])
		h2o.day(hdf["ds3"])

		# Access the hour:
		h2o.hour(hdf["ds3"])
		