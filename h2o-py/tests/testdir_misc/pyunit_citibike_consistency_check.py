# Check to make sure the small and large citibike demos have not diverged
import os
import h2o, tests

def consistency_check():

    try:
        small = tests.locate("h2o-py/demos/citi_bike_small.ipynb")
    except ValueError:
        small = tests.locate("h2o-py/demos/citi_bike_small_NOPASS.ipynb")

    try:
        large = tests.locate("h2o-py/demos/citi_bike_large.ipynb")
    except ValueError:
        large = tests.locate("h2o-py/demos/citi_bike_large_NOPASS.ipynb")

    results_dir = tests.locate("results")
    s = os.path.join(results_dir, os.path.basename(small).split('.')[0]+".py")
    l = os.path.join(results_dir, os.path.basename(large).split('.')[0]+".py")

    tests.ipy_notebook_exec(small, save_and_norun = s)
    tests.ipy_notebook_exec(large, save_and_norun = l)

    small_list = list(open(s, 'r'))
    large_list = list(open(l, 'r'))

    for s, l in zip(small_list, large_list):
        if s != l:
            assert s == "data = h2o.import_file(path=small_test)\n" and \
                   l != "data = h2o.import_file(path=large_test)\n", \
                "This difference is not allowed between the small and large citibike demos.\nCitibike small: {0}" \
                "Citibike large: {1}".format(s,l)


pyunit_test = consistency_check
