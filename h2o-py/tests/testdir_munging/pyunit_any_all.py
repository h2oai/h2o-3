



def test_any_all():

    foo = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))

    foo["C6"] = foo["C1"] > 0.0
    any = foo[:,"C6"].any()
    all = foo[:,"C6"].all()
    assert any and all, "expected any and all to be True but but got {0} and {1}".format(any, all)

    foo["C6"] = foo["C1"] > 5.0
    any = foo[:,"C6"].any()
    all = foo[:,"C6"].all()
    assert any and not all, "expected any to be True and all to be False but but got {0} and {1}".format(any, all)


test_any_all()
