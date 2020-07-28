import time, os, sys, traceback
import h2o
from h2o.estimators import H2OXGBoostEstimator
from mock_steam import MockSteam

sys.path.insert(1, os.path.join("../../../../h2o-py"))
from tests import pyunit_utils

def make_starting_response(req):
    return {
        "_id": "%s_response" % req["_id"],
        "_type": "xgboostClusterStartNotification",
        "status": "starting"
    }

def make_started_response(req, status, uri, user, password, reason=None):
    return {
        "_id": "%s_response" % req["_id"],
        "_type": "xgboostClusterStartNotification",
        "uri": uri,
        "user": user,
        "password": password,
        "status": status,
        "reason": reason
    }

def make_stop_req(id):
    return {
        "_id": id,
        "_type": "stopXGBoostClusterNotification"
    }

def test():
    host_port = os.environ["cloud_ip_port_main"]
    main_uri = "%s/main" % host_port
    username = "jenkins"
    password = "main"
    
    xgb_host_port = "%s/xgb" % os.environ["cloud_ip_port_xgb"]
    xgb_username = "jenkins"
    xgb_password = "xgb"
    
    h2o.connect(url="http://%s" % main_uri, auth=(username, password))
    
    # hello test
    steam = MockSteam(main_uri, username, password)
    steam.send({"_type": "hello", "_id": "hi_1"})
    hello_response = steam.wait_for_message()
    assert hello_response is not None, "No hello response sent."
    assert "hello_response" == hello_response["_type"]
    assert "hi_1_response" == hello_response["_id"]
    
    # load data
    name_node = pyunit_utils.hadoop_namenode()
    train = h2o.import_file("hdfs://" + name_node + "/datasets/chicagoCensus.csv")
    x = list(range(0, train.ncol-1))
    y = train.ncol-1
    train = train[~train[y].isna(), :]
    model1 = H2OXGBoostEstimator(ntrees=5)
    model2 = H2OXGBoostEstimator(ntrees=5)
    
    # make sure H2O thinks there is no cluster running
    steam.send(make_stop_req("stop_check"))
    stop_resp = steam.wait_for_message()
    assert stop_resp is not None, "No stop response"
    assert "stopXGBoostClusterConfirmation" == stop_resp["_type"]
    assert stop_resp["allowed"] is not None # response could be anything here
    
    # steam does not respond
    model1.start(x=x, y=y, training_frame=train)
    start_req = steam.wait_for_message()
    assert start_req is not None, "No start request sent"
    assert "startXGBoostCluster" == start_req["_type"]
    time.sleep(20)
    try:
        model1.join()
        assert False, "Model train did not fail when steam did not respond"
    except Exception as e:
        print(e)
        assert True, "Jon failed as expected"
    
    # xgboost happy path
    model1.start(x=x, y=y, training_frame=train)
    start_req_1 = steam.wait_for_message()
    assert start_req_1 is not None, "No start request sent"
    assert "startXGBoostCluster" == start_req_1["_type"]
    steam.send(make_starting_response(start_req_1))
    time.sleep(10)
    steam.send(make_started_response(start_req_1, "started", xgb_host_port, xgb_username, xgb_password))
    model1.join()
    steam.send(make_stop_req("stop_01"))
    stop_resp_1 = steam.wait_for_message()
    assert stop_resp_1 is not None, "No stop response"
    assert "stopXGBoostClusterConfirmation" == stop_resp_1["_type"]
    assert "true" == stop_resp_1["allowed"]
    
    # another train should trigger another cluster start
    model1.start(x=x, y=y, training_frame=train)
    start_req_2 = steam.wait_for_message()
    assert start_req_2 is not None, "No start request sent"
    assert "startXGBoostCluster" == start_req_2["_type"]
    steam.send(make_started_response(start_req_2, "started", xgb_host_port, xgb_username, xgb_password))
    model1.join()
    model2.start(x=x, y=y, training_frame=train)
    assert steam.wait_for_message() is None, "Should not sent start request for another job"
    model2.join()
    steam.send(make_stop_req("stop_02"))
    stop_resp_2 = steam.wait_for_message()
    assert stop_resp_2 is not None, "No stop response"
    assert "stopXGBoostClusterConfirmation" == stop_resp_2["_type"]
    assert "true" == stop_resp_2["allowed"]
    
    # starting of cluster fails
    model1.start(x=x, y=y, training_frame=train)
    start_req_3 = steam.wait_for_message()
    assert start_req_3 is not None, "No start request sent"
    assert "startXGBoostCluster" == start_req_3["_type"]
    steam.send(make_started_response(start_req_3, "failed", xgb_host_port, xgb_username, xgb_password, reason="Testing, testing"))
    try:
        model1.join()
        assert False, "Model train did not fail when steam responded with failure"
    except Exception as e:
        print(e)
        assert True, "Jon failed as expected"
    
    # cleanup
    steam.close()

try:
    test()
    print("Test passed!")
except:
    print("--------------------------------------")
    print("Test failed! Error:")
    print("--------------------------------------")
    traceback.print_exc()
    sys.exit(1)
