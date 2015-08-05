# Check to make sure the small and large citibike demos have not diverged
import sys, os
sys.path.insert(1, "../../")
import h2o

def consistency_check(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    h2o.ipy_notebook_exec(h2o.locate("h2o-py/demos/citi_bike_small.ipynb"),save_and_norun=True)
    h2o.ipy_notebook_exec(h2o.locate("h2o-py/demos/citi_bike_large.ipynb"),save_and_norun=True)

    small = list(open('citi_bike_small.py', 'r'))
    large = list(open('citi_bike_large.py', 'r'))
    os.remove(h2o.locate("h2o-py/tests/testdir_misc/citi_bike_small.py"))
    os.remove(h2o.locate("h2o-py/tests/testdir_misc/citi_bike_large.py"))

    for s, l in zip(small, large):
        if s != l:
            assert s == "data = h2o.import_frame(path=small_test)\n" and \
                   l != "data = h2o.import_frame(path=large_test)\n", \
                "This difference is not allowed between the small and large citibike demos.\nCitibike small: {0}" \
                "Citibike large: {1}".format(s,l)

if __name__ == "__main__":
    h2o.run_test(sys.argv, consistency_check)