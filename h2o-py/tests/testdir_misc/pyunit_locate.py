



def test_locate():

    iris_path = pyunit_utils.locate("smalldata/iris/iris.csv")

    try:
        pyunit_utils.locate("smalldata/iris/afilethatdoesnotexist.csv")
        assert False, "Expected pyunit_utils.locate to raise a ValueError"
    except ValueError:
        assert True


test_locate()
