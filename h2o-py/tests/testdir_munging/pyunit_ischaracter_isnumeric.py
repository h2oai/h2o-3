

import h2o, tests
import random

def pyunit_ischaracter_isnumeric():

    iris = h2o.import_file(tests.locate("smalldata/iris/iris.csv"))
    assert iris[0].isnumeric(), "Expected the first column of iris to be numeric"
    assert not iris[0].ischaracter(), "Expected the first column of iris to be numeric"
    assert not iris[4].isnumeric(), "Expected the last column of iris to be character"
    iris[4] = iris[4].ascharacter()
    assert iris[4].isstring(), "Expected the last column of iris to be a string"


pyunit_test = pyunit_ischaracter_isnumeric
