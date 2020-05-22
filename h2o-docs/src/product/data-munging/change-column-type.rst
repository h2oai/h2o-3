.. _change-column-type:

Changing the Column Type
------------------------

H2O algorithms will treat a problem as a classification problem if the column type is ``factor`` and a regression problem if the column type is ``numeric``. You can force H2O to use either classification or regression by changing the column type.

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
