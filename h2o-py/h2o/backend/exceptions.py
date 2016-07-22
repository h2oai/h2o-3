# -*- encoding: utf-8 -*-
"""
Exception classes used by H2OConnection and H2OLocalServer.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

__all__ = ("H2OStartupError", "H2OConnectionError", "H2OServerError", "H2OResponseError")



class H2OStartupError(Exception):
    """Raised by H2OLocalServer when the class fails to launch a server."""


class H2OConnectionError(Exception):
    """
    Raised when connection to an H2O server cannot be established.

    This can be raised if the connection was not initialized; or the server cannot be reached at the specified address;
    or there is an authentication error; or the request times out; etc.
    """


# This should have been extending from Exception as well; however in old code version all exceptions were
# EnvironmentError's, so for old code to work we extend H2OResponseError from EnvironmentError.
class H2OResponseError(EnvironmentError):
    """Raised when the server encounters a user error and sends back an H2OErrorV3 response."""


class H2OServerError(Exception):
    """
    Raised when any kind of server error is encountered.

    This includes: server returning HTTP status 500; or server sending malformed JSON; or server returning an
    unexpected response (e.g. lacking a "__schema" field); or server indicating that it is in an unhealthy state; etc.
    """

    def __init__(self, message, stacktrace=None):
        """
        Instantiate a new H2OServerError exception.

        :param message: error message describing the exception.
        :param stacktrace: (optional, list(str)) server-side stacktrace, if available. This will be printed out by
            our custom except hook (see debugging.py).
        """
        super(H2OServerError, self).__init__(message)
        self.stacktrace = stacktrace
