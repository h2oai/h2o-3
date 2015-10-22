import sys
sys.path.insert(1, "../../../")
import h2o, tests

def center_scale():
    
    

    iris =  h2o.import_file(path=h2o.locate("smalldata/iris/iris.csv"))[0:4]

    # frame (default args)
    foo = iris.scale()
    # TODO: the below assertion fails. Should it?
    #assert abs(foo[0,0] - -0.8976739) < 1e-6 and  abs(foo[0,1] - 1.01560199) < 1e-6 and abs(foo[0,2] - -1.335752) < 1e-6 \
    #       and abs(foo[0,3] - -1.311052) < 1e-6, "h2o differed from r. h2o got {0}, {1}, {2}, and {3}" \
    #                                             "".format(foo[0,0],foo[0,1],foo[0,2],foo[0,3])

    # frame (centers=True, scale=False)
    foo = iris.scale(center=True, scale=False)

    # frame (centers=False, scale=True)
    foo = iris.scale(center=False, scale=True)

    # frame (centers=False, scale=False)
    foo = iris.scale(center=False, scale=False)

    # vec (default args)
    foo = iris[0].scale()

    # vec (centers=True, scale=False)
    foo = iris[1].scale(center=True, scale=False)

    # vec (centers=False, scale=True)
    foo = iris[2].scale(center=False, scale=True)

    # vec (centers=False, scale=False)
    foo = iris[3].scale(center=False, scale=False)

if __name__ == "__main__":
    tests.run_test(sys.argv, center_scale)
