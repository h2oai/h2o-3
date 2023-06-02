import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

try:    # redirect python output
    from StringIO import StringIO  # for python 3
except ImportError:
    from io import StringIO  # for python 2

def group_by():
    '''
    This test checks that if a groupby operation is specified for frames with string columns, a warning is
    generated about the string columns being skipped.

    In addition, it checks that operations on numeric/enum columns are performed and generated the correct
    expected outputs.
    
    '''
    # Connect to a pre-existing cluster

    buffer = StringIO() # redirect output
    sys.stderr=buffer
    h2o_f1 = h2o.import_file(path=pyunit_utils.locate("smalldata/jira/test_groupby_with_strings.csv"),col_types=['real','string','string','real'])
    grouped = h2o_f1.group_by("C1")
    grouped.mean(na="all").median(na="all").max(na="all").min(na="all").sum(na="all")
    print(grouped.get_frame())
    print("Checking number of warning messages...")
    check_warnings(2, buffer) # make sure we received two warning, one per string row

def check_warnings(warnNumber, buffer):
    warn_phrase = "UserWarning"
    warn_string_of_interest = "slash (/) found"
    sys.stderr=sys.__stderr__   # redirect printout back to normal path

    try:        # for python 2.7
        assert len(buffer.buflist)==warnNumber
        if len(buffer.buflist) > 0:  # check to make sure we have the right number of warning
            for index in range(len(buffer.buflist)):
                print("*** captured warning message: {0}".format(buffer.buflist[index]))
                assert (warn_phrase in buffer.buflist[index]) and (warn_string_of_interest in buffer.buflist[index])
    except:     # for python 3.
        warns = buffer.getvalue()
        print("*** captured warning message: {0}".format(warns))
        countWarns = warns.count("UserWarning")
        assert countWarns==warnNumber, "Expected number of warnings: {0}, but received {1}.".format(warnNumber, countWarns)

if __name__ == "__main__":
    pyunit_utils.standalone_test(group_by)
else:
    group_by()
