# Used swedish insurance data from smalldata instead of MASS/insurance due to the license of the MASS R package.
import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
h2o.init()

h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/Motor_insurance_sweden.txt", sep = '\t')
poisson_fit = H2OGeneralizedLinearEstimator(family = "poisson")
poisson_fit.train(y="Claims", x = ["Payment", "Insured", "Kilometres", "Zone", "Bonus", "Make"], training_frame = h2o_df)
