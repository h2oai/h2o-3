import sys
sys.path.insert(1,"../../")
from tests import pyunit_utils
from requests.models import Response
from h2o.auth import SpnegoAuth


def test_is_spnego_response():
    edgeCaseNotSupported = "Basic realm='apps', title='Foo, Negotiate, Bar'"
    invalid = {
        "Digest realm='http-auth@example.org', qop='auth, auth-int', algorithm=MD5, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'",
        "Basic Negotiate realm='http-auth@example.org', qop='auth, auth-int', algorithm=MD5, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'",
        "Basic, Negotiatesds realm='http-auth@example.org', qop='auth, auth-int', algorithm=MD5, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'",
        "Basic, asdfNegotiate realm='http-auth@example.org', qop='auth, auth-int', algorithm=MD5, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'",
        "Basic realm='http-auth@example.org', qop='auth, auth-int', algorithm=Negotiate, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'",
        "Custom realm='apps', negotiate='true'"        
    }
    valid = {"Negotiate realm='http-auth@example.org', qop='auth, auth-int', algorithm=MD5, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'",
             "Basic, Negotiate realm='http-auth@example.org', qop='auth, auth-int', algorithm=MD5, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'",
             "Basic realm='http-auth@example.org', Negotiate realm='http-auth@example.org', qop='auth, auth-int', algorithm=MD5, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'",
             "Negotiate", 
             "Basic, Negotiate", 
             "Negotiate, Basic",
             edgeCaseNotSupported
    }
    
    spnego = SpnegoAuth("dummyPrincipal")

    response = Response()
    response.status_code = 200
    response._content = b'{ "key" : "a" }'
    
    for header in invalid:
        response.headers['www-authenticate'] = header
        assert spnego._is_spnego_response(response) is False

    for header in valid:
        response.headers['www-authenticate'] = header
        assert spnego._is_spnego_response(response) is True


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_is_spnego_response)
else:
    test_is_spnego_response()
