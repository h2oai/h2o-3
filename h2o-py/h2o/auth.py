# -*- encoding: utf-8 -*-
"""
Data class for SPNEGO authentication.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import re

from requests.auth import AuthBase

try:
    import kerberos
except ImportError:
    try:
        import winkerberos as kerberos
    except ImportError as e:
        raise ImportError("Neither pykerberos nor winkerberos package found, install one based on your OS.")

try:
    import gssapi
except ImportError:
    raise ImportError("Required gssapi package not found.")


from h2o.utils.typechecks import assert_is_type

__all__ = ("SpnegoAuth", )


class SpnegoAuth(AuthBase):
    """
    Configuration of SPNEGO authentication to back-end H2O.
    """

    def __init__(self, service_principal, mech_oid=kerberos.GSS_MECH_OID_SPNEGO):
        assert_is_type(service_principal, str)

        self._header_regex = re.compile(r'(^|,\s*)Negotiate($|[,\s]+)', re.I)

        self._service_principal = service_principal
        self._mech_oid = mech_oid

    def _authenticate_request(self, response, **kwargs):
        res, ctx = kerberos.authGSSClientInit(self._service_principal, mech_oid=self._mech_oid)
        kerberos.authGSSClientStep(ctx, "")
        token = kerberos.authGSSClientResponse(ctx)
        response.request.headers['Authorization'] = "Negotiate " + token

        response.content  # NOQA - to drain the stream
        response.raw.release_conn()
        authenticated_response = response.connection.send(response.request, **kwargs)
        authenticated_response.history.append(response)
        return authenticated_response

    def _is_spnego_response(self, response):
        header = response.headers.get('www-authenticate', None)
        return self._header_regex.search(header) is not None

    def handle_response(self, response, **kwargs):
        if response.status_code == 401 and self._is_spnego_response(response):
            return self._authenticate_request(response, **kwargs)
        else:
            return response

    def __call__(self, request):
        request.register_hook('response', self.handle_response)
        return request
