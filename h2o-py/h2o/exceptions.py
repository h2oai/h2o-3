# -*- encoding: utf-8 -*-
# Copyright: (c) 2016 H2O.ai
# License:   Apache License Version 2.0 (see LICENSE for details)
"""
:mod:`h2o.exceptions` -- all exceptions classes in h2o module.

All H2O exceptions derive from :class:`H2OError`.
"""
from __future__ import absolute_import, division, print_function, unicode_literals

__all__ = ("H2OStartupError", "H2OConnectionError", "H2OServerError", "H2OResponseError",
           "H2OValueError", "H2OTypeError")


class H2OError(Exception):
    """Base class for all H2O exceptions."""

class H2OValueError(H2OError):
    """Error indicating that wrong parameter value was passed to a function."""



#-----------------------------------------------------------------------------------------------------------------------
# H2OTypeError
#-----------------------------------------------------------------------------------------------------------------------

class H2OTypeError(H2OError):
    """
    Error indicating that the user passed a parameter of wrong type.

    This error will trigger "soft" exception handling, in the sense that the stack trace will be much more compact
    than usual.
    """

    def __init__(self, var_name=None, exp_type=None, var_value=None, message=None, skip_frames=0):
        """
        Create an H2OTypeError exception object.

        :param message: error message that will be shown to the user. If not given, this message will be constructed
            from ``var_name``, ``var_value`` and ``exp_type``.
        :param exp_type: expected variable's type.
        :param var_name: name of the variable whose type is wrong (can be used for highlighting etc).
        :param skip_frames: how many auxiliary function calls have been made since the moment of the exception. This
            many local frames will be skipped in the output of the exception message. For example if you want to check
            a variables type, and call a helper function ``assert_is_type()`` to do that job for you, then
            ``skip_frames`` should be 1 (thus making the call to ``assert_is_type`` invisible).
        """
        super(H2OTypeError, self).__init__(message)
        self._var_name = var_name
        self._exp_type = exp_type
        self._var_value = var_value
        self._message = message
        self._skip_frames = skip_frames

    def __str__(self):
        """Used when printing out the exception message."""
        if self._message:
            return self._message
        # Otherwise construct the message
        var = self._var_name
        val = self._var_value
        etn = self._get_type_name(self._exp_type)
        article = "an" if etn.lstrip("?")[0] in "aioeH" else "a"
        atn = self._get_type_name(type(val))
        return "Argument `{var}` should be {an} {expected_type}, got {actual_type} (value: {value})".\
               format(var=var, an=article, expected_type=etn, actual_type=atn, value=val)

    @property
    def var_name(self):
        """Variable name."""
        return self._var_name

    @property
    def skip_frames(self):
        """Number of local frames to skip when printing our the stacktrace."""
        return self._skip_frames


    @staticmethod
    def _get_type_name(stype, _nested=False):
        """
        Return the name of the provided type.

            >>> _get_type_name(int) == "integer"
            >>> _get_type_name(str) == "string"
            >>> _get_type_name(tuple) == "tuple"
            >>> _get_type_name(Exception) == "Exception object"
            >>> _get_type_name((int, float, bool)) == "integer|float|bool"
            >>> _get_type_name((H2OFrame, None)) == "?H2OFrame"
        """
        if stype is None or isinstance(None, stype):
            return "None"
        elif stype is str:
            return "string"
        elif stype is int:
            return "integer"
        elif isinstance(stype, type):
            n = stype.__name__
            if n[0].isupper() and not _nested:
                return n + " object"
            else:
                return n
        elif isinstance(stype, tuple):
            maybe_type = False
            res = []
            for tt in stype:
                nn = H2OTypeError._get_type_name(tt, _nested=True)
                if nn == "None":
                    maybe_type = True
                else:
                    res.append(nn)
            if maybe_type:
                if res:
                    res[0] = "?" + res[0]
                else:
                    res.append("None")
            return "|".join(res)
        else:
            raise RuntimeError("Unexpected `stype`: %r" % stype)



#-----------------------------------------------------------------------------------------------------------------------
# Backend exceptions
#-----------------------------------------------------------------------------------------------------------------------

class H2OStartupError(H2OError):
    """Raised by H2OLocalServer when the class fails to launch a server."""


class H2OConnectionError(H2OError):
    """
    Raised when connection to an H2O server cannot be established.

    This can be raised if the connection was not initialized; or the server cannot be reached at the specified address;
    or there is an authentication error; or the request times out; etc.
    """


# This should have been extending from Exception as well; however in old code version all exceptions were
# EnvironmentError's, so for old code to work we extend H2OResponseError from EnvironmentError.
class H2OResponseError(H2OError, EnvironmentError):
    """Raised when the server encounters a user error and sends back an H2OErrorV3 response."""


class H2OServerError(H2OError):
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
