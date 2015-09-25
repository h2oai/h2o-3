# Check to make sure the small and large citibike demos have not diverged
import sys, os
sys.path.insert(1, "../../")
import h2o, tests

def consistency_check():

    try:
        small = h2o.locate("h2o-py/demos/citi_bike_small.ipynb")
    except ValueError:
        small = h2o.locate("h2o-py/demos/citi_bike_small_NOPASS.ipynb")

    try:
        large = h2o.locate("h2o-py/demos/citi_bike_large.ipynb")
    except ValueError:
        large = h2o.locate("h2o-py/demos/citi_bike_large_NOPASS.ipynb")

    tests.ipy_notebook_exec(small, save_and_norun=True)
    tests.ipy_notebook_exec(large, save_and_norun=True)

    s = os.path.basename(small).split('.')[0]+".py"
    l = os.path.basename(large).split('.')[0]+".py"
    small_list = list(open(s, 'r'))
    large_list = list(open(l, 'r'))
    os.remove(h2o.locate(s))
    os.remove(h2o.locate(l))

    for s, l in zip(small_list, large_list):
        if s != l:
            assert s == "data = h2o.import_file(path=small_test)\n" and \
                   l != "data = h2o.import_file(path=large_test)\n", \
                "This difference is not allowed between the small and large citibike demos.\nCitibike small: {0}" \
                "Citibike large: {1}".format(s,l)

if __name__ == "__main__":
    tests.run_test(sys.argv, consistency_check)
