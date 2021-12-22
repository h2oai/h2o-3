import sys

sys.path.insert(1,"../../")
import h2o
from h2o.estimators import H2OGradientBoostingEstimator
from h2o.exceptions import H2OResponseError
from h2o.schemas import H2OErrorV3, H2OModelBuilderErrorV3
from tests import pyunit_utils as pu


def test_backend_error():
    try:
        h2o.api("GET /3/Foo", data=dict(bar='baz'))
        assert False, "API call should have failed"
    except H2OResponseError as e:
        backend_err = e.args[0]
        assert isinstance(backend_err, H2OErrorV3)
        assert backend_err.endpoint == "GET /3/Foo"
        assert backend_err.payload == (None, None, None, dict(bar='baz'))  # yeah! because on GET, data becomes params and turns into None, this is so confusing!
        assert backend_err.http_status == 404
        assert isinstance(backend_err.stacktrace, list) 
        assert len(backend_err.stacktrace) > 10
        assert backend_err.stacktrace[0] == "water.exceptions.H2ONotFoundArgumentException: Resource /3/Foo not found"
        assert backend_err.msg == "Resource /3/Foo not found"
        assert backend_err.dev_msg == backend_err.msg
        assert backend_err.exception_msg == backend_err.msg
        assert backend_err.exception_type == "water.exceptions.H2ONotFoundArgumentException"
        assert backend_err.error_url == "Resource /3/Foo"
        assert backend_err.timestamp > 0
        assert len(backend_err.values) == 0
        

def test_model_builds_error():
    try:
        df = h2o.import_file(path=pu.locate("smalldata/prostate/prostate.csv"))
        gbm = H2OGradientBoostingEstimator()
        gbm.train(y=-1, training_frame=df, offset_column="foo")
        assert False, "model training should have failed"
    except H2OResponseError as e:
        mb_err = e.args[0]
        assert isinstance(mb_err, H2OModelBuilderErrorV3)
        assert mb_err.endpoint == "POST /3/ModelBuilders/gbm"
        data = mb_err.payload[0]
        assert data is not None
        assert data['offset_column'] == 'foo'
        assert mb_err.http_status == 412  # see H2OIllegalArgumentException
        assert isinstance(mb_err.stacktrace, list)
        assert len(mb_err.stacktrace) > 10
        assert "water.exceptions.H2OModelBuilderIllegalArgumentException: Illegal argument(s) for GBM model" in mb_err.stacktrace[0]
        assert "ERRR on field: _offset_column: Offset column 'foo' not found in the training frame" in mb_err.msg 
        assert mb_err.dev_msg == mb_err.msg
        assert mb_err.exception_msg == mb_err.msg
        assert mb_err.exception_type == "water.exceptions.H2OModelBuilderIllegalArgumentException"
        assert mb_err.error_url == "/3/ModelBuilders/gbm"
        assert mb_err.timestamp > 0
        assert len(mb_err.values) == 4
        assert {'algo', 'error_count', 'messages', 'parameters'} == set(mb_err.values.keys())
        assert mb_err.values['algo'] == 'GBM'
        assert mb_err.values['error_count'] == 4  # no idea why 4, but adding it to test as it's interesting
        assert mb_err.values['parameters']['_offset_column'] == 'foo'
        assert len(mb_err.values['messages']) > 1
        msgs_lev_1 = [m for m in mb_err.values['messages'] if m['_log_level'] == 1]
        assert len(msgs_lev_1) == 2
        assert msgs_lev_1[0] == msgs_lev_1[1]  # it is duplicated indeed!
        assert msgs_lev_1[0]['_field_name'] == '_offset_column'
        assert msgs_lev_1[0]['_message'] == "Offset column 'foo' not found in the training frame"
        # specific to H2OModelBuilderErrorV3
        assert mb_err.error_count == mb_err.values['error_count']
        assert len(mb_err.messages) == len(mb_err.values['messages'])
        assert len(mb_err.parameters) < len(mb_err.values['parameters'])  # no idea what's the difference there, outside that on the left side, parameters are accessible with the full schema
        

pu.run_tests([
    test_backend_error,
    test_model_builds_error
])

