import sys
sys.path.insert(1, "../../")
import h2o

def sub_gsub_check(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)

    frame = h2o.import_frame(path=h2o.locate("smalldata/iris/iris.csv"))

    # single column (frame)
    h2o.gsub("s", "z", frame[["C5"]])
    assert frame[0,4] == "Iriz-zetoza", "Expected 'Iriz-zetoza', but got {0}".format(frame[0,4])

    h2o.sub("z", "s", frame[["C5"]])
    assert frame[1,4] == "Iris-zetoza", "Expected 'Iris-zetoza', but got {0}".format(frame[1,4])


    # single column (vec)
    vec = frame["C5"]
    h2o.sub("z", "s", vec)
    assert vec[2] == "Iris-setoza", "Expected 'Iris-setoza', but got {0}".format(vec[2])

    h2o.gsub("s", "z", vec)
    assert vec[3] == "Iriz-zetoza", "Expected 'Iriz-zetoza', but got {0}".format(vec[3])

if __name__ == "__main__":
    h2o.run_test(sys.argv, sub_gsub_check)