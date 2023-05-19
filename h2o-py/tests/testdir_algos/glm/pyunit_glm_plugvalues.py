import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def plug_values_glm():
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars.drop(0)

    glm_means = H2OGeneralizedLinearEstimator(seed=42)
    glm_means.train(training_frame=cars, y="cylinders")

    means = cars.mean()

    glm_plugs1 = H2OGeneralizedLinearEstimator(seed=42,
                                               missing_values_handling="PlugValues",
                                               plug_values=means)
    glm_plugs1.train(training_frame=cars, y="cylinders")
    
    assert glm_means.coef() == glm_plugs1.coef()

    not_means = 0.1 + (means * 0.5)

    glm_plugs2 = H2OGeneralizedLinearEstimator(seed=42,
                                               missing_values_handling="PlugValues",
                                               plug_values=not_means)
    glm_plugs2.train(training_frame=cars, y="cylinders")

    # just to show plug values are not ignored
    assert glm_means.coef() != glm_plugs2.coef()

    # we standardize the dataset manually (implicit standardization doesn't consider the plug values)
    cars = cars.scale()

    glm_plugs3 = H2OGeneralizedLinearEstimator(seed=42, 
                                               missing_values_handling="PlugValues",
                                               plug_values=not_means,
                                               standardize=False)
    glm_plugs3.train(training_frame=cars, y="cylinders")

    cars.impute(values=not_means.getrow())
    
    glm_plugs4 = H2OGeneralizedLinearEstimator(seed=42, standardize=False)
    glm_plugs4.train(training_frame=cars, y="cylinders")

    assert glm_plugs3.coef() == glm_plugs4.coef()


if __name__ == "__main__":
    pyunit_utils.standalone_test(plug_values_glm)
else:
    plug_values_glm()
