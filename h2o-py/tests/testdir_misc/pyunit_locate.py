

import h2o, tests

def test_locate():

    iris_path = tests.locate("smalldata/iris/iris.csv")

    try:
        tests.locate("smalldata/iris/afilethatdoesnotexist.csv")
        assert False, "Expected tests.locate to raise a ValueError"
    except ValueError:
        assert True


pyunit_test = test_locate
