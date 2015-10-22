import h2o
h2o.init()

airlines_url = "https://s3.amazonaws.com/h2o-airlines-unpacked/allyears2k.csv"

airlines_df = h2o.import_file(airlines_url)

airlines_df.columns

airlines_df.describe()  # output suppressed

airlines_df["IsArrDelayed"].describe()

independent_vars = ["Year","Month","DayOfWeek","CRSDepTime","CRSArrTime","Origin","Dest","UniqueCarrier"]
dependent_var = "IsArrDelayed"

from h2o.estimators.glm import H2OGeneralizedLinearEstimator

linear_classification_estimator = H2OGeneralizedLinearEstimator(family="binomial")

linear_classification_estimator.train(X=independent_vars, y=dependent_var, training_frame=airlines_df)

linear_classification_estimator.show()


