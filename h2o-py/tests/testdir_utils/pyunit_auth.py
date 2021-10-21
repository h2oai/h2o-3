import sys
sys.path.insert(1,"../../")
from tests import pyunit_utils
from requests.models import Response
from h2o.auth import SpnegoAuth


def test_is_spnego_response():
    invalid1 = "Digest realm='http-auth@example.org', qop='auth, auth-int', algorithm=MD5, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'"
    valid1 = "Negotiate realm='http-auth@example.org', qop='auth, auth-int', algorithm=MD5, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'"
    valid2 = "Basic, Negotiate realm='http-auth@example.org', qop='auth, auth-int', algorithm=MD5, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'"
    invalid2 = "Basic Negotiate realm='http-auth@example.org', qop='auth, auth-int', algorithm=MD5, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'"
    invalid3 = "Basic, Negotiatesds realm='http-auth@example.org', qop='auth, auth-int', algorithm=MD5, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'"
    valid3 = "Basic realm='http-auth@example.org', Negotiate realm='http-auth@example.org', qop='auth, auth-int', algorithm=MD5, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'"
    invalid4 = "Basic, asdfNegotiate realm='http-auth@example.org', qop='auth, auth-int', algorithm=MD5, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'"
    invalid5 = "Basic realm='http-auth@example.org', qop='auth, auth-int', algorithm=Negotiate, nonce='7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v', opaque='FQhe/qaU925kfnzjCev0ciny7QMkPqMAFRtzCUYo5tdS'"
    invalid6 = "Custom realm='apps', negotiate='true'"

    edgeCaseNotSupported = "Basic realm='apps', title='Foo, Negotiate, Bar'"
    
    
    spnego = SpnegoAuth("dummyPrincipal")

    response = Response()
    response.status_code = 200
    response._content = b'{ "key" : "a" }'
    
    response.headers['www-authenticate'] = invalid1
    assert spnego._is_spnego_response(response) is False

    response.headers['www-authenticate'] = valid1
    assert spnego._is_spnego_response(response) is True

    response.headers['www-authenticate'] = valid2
    assert spnego._is_spnego_response(response) is True

    response.headers['www-authenticate'] = invalid2
    assert spnego._is_spnego_response(response) is False

    response.headers['www-authenticate'] = invalid3
    assert spnego._is_spnego_response(response) is False

    response.headers['www-authenticate'] = valid3
    assert spnego._is_spnego_response(response) is True

    response.headers['www-authenticate'] = invalid4
    assert spnego._is_spnego_response(response) is False

    response.headers['www-authenticate'] = invalid5
    assert spnego._is_spnego_response(response) is False

    response.headers['www-authenticate'] = edgeCaseNotSupported
    assert spnego._is_spnego_response(response) is True

    response.headers['www-authenticate'] = invalid6
    assert spnego._is_spnego_response(response) is False


    

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_is_spnego_response)
else:
    test_is_spnego_response()
