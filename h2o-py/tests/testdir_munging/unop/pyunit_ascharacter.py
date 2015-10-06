import sys
sys.path.insert(1, "../../../")
import h2o, tests

def ascharacter():
    h2oframe =  h2o.import_file(path=tests.locate("smalldata/junit/cars.csv"))
    h2oframe.show()
    h2oframe['cylinders'] = h2oframe['cylinders'].asfactor()
    h2oframe['cylinders'].ascharacter()
    assert h2oframe["cylinders"].isfactor(), "expected the column be a factor"
    assert not h2oframe["cylinders"].isstring(), "expected the column to not be a string"

if __name__ == "__main__":
    tests.run_test(sys.argv, ascharacter)
