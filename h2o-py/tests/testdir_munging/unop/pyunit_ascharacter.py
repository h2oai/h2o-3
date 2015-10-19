



def ascharacter():
    h2oframe =  h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars.csv"))
    h2oframe.show()
    h2oframe['cylinders'] = h2oframe['cylinders'].asfactor()
    h2oframe['cylinders'].ascharacter()
    assert h2oframe["cylinders"].isfactor(), "expected the column be a factor"
    assert not h2oframe["cylinders"].isstring(), "expected the column to not be a string"


ascharacter()
