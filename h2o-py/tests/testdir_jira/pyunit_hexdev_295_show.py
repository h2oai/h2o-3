import sys
sys.path.insert(1, "../../")
import h2o

def show_jira(ip, port):
    
    local_data = [[1, 'a'],[0, 'b']]
    h2o_data = h2o.H2OFrame(python_obj=local_data)
    h2o_data.setNames(['response', 'predictor'])
    h2o_data.show()

if __name__ == "__main__":
    h2o.run_test(sys.argv, show_jira)
