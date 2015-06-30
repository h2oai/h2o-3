import sys
sys.path.insert(1, "../../../")
import h2o

def ascharacter(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    h2oframe =  h2o.import_frame(path=h2o.locate("smalldata/junit/cars.csv"))

    h2oframe['cylinders'].ascharacter()
    assert not h2oframe["cylinders"].isfactor(), "expected the column to not be a factor"

    h2oframe['cylinders'] = h2oframe['cylinders'].ascharacter()
    assert h2oframe["cylinders"].isfactor(), "expected the column to be a factor"

if __name__ == "__main__":
    h2o.run_test(sys.argv, ascharacter)