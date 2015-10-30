import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def show_jira():
    
    local_data = [[1, 'a'],[0, 'b']]
    h2o_data = h2o.H2OFrame(local_data)
    h2o_data.set_names(['response', 'predictor'])
    h2o_data.show()



if __name__ == "__main__":
    pyunit_utils.standalone_test(show_jira)
else:
    show_jira()
