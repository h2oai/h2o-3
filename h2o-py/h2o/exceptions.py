# -*- encoding: utf-8 -*-
# Copyright: (c) 2016 H2O.ai
# License:   Apache License Version 2.0 (see LICENSE for details)
"""
:mod:`h2o.exceptions` -- all exceptions classes in h2o module.

All H2O exceptions derive from :class:`H2OError`.
"""
from __future__ import absolute_import, division, print_function, unicode_literals

__all__ = ("H2OStartupError", "H2OConnectionError", "H2OServerError", "H2OResponseError",
           "H2OValueError", "H2OTypeError", "H2OJobCancelled", "H2OError",
           "H2ODeprecationWarning")


class H2OError(Exception):
    """Base class for all H2O exceptions."""


class H2OSoftError(H2OError):
    """Base class for exceptions that trigger "soft" exception handling hook."""


# ----------------------------------------------------------------------------------------------------------------------
# H2OValueError
# ----------------------------------------------------------------------------------------------------------------------

class H2OValueError(H2OSoftError, ValueError):
    """Error indicating that wrong parameter value was passed to a function."""

    def __init__(self, message, var_name=None, skip_frames=0):
        """Create an H2OValueError exception object."""
        super(H2OValueError, self).__init__(message)
        self.var_name = var_name
        self.skip_frames = skip_frames
        

# ----------------------------------------------------------------------------------------------------------------------
# H2OTypeError
# ----------------------------------------------------------------------------------------------------------------------

class H2OTypeError(H2OSoftError, TypeError):
    """
    Error indicating that the user passed a parameter of wrong type.

    This error will trigger "soft" exception handling, in the sense that the stack trace will be much more compact
    than usual.
    """

    def __init__(self, var_name=None, var_value=None, var_type_name=None, exp_type_name=None, message=None,
                 skip_frames=0):
        """
        Create an H2OTypeError exception object.

        :param message: error message that will be shown to the user. If not given, this message will be constructed
            from ``var_name``, ``var_value``, etc.
        :param var_name: name of the variable whose type is wrong (can be used for highlighting etc).
        :param var_value: the value of the variable.
        :param var_type_name: the name of the variable's actual type.
        :param exp_type_name: the name of the variable's expected type.
        :param skip_frames: how many auxiliary function calls have been made since the moment of the exception. This
            many local frames will be skipped in the output of the exception message. For example if you want to check
            a variables type, and call a helper function ``assert_is_type()`` to do that job for you, then
            ``skip_frames`` should be 1 (thus making the call to ``assert_is_type`` invisible).
        """
        super(H2OTypeError, self).__init__(message)
        self._var_name = var_name
        self._var_value = var_value
        self._var_type_name = var_type_name or str(type(var_value))
        self._exp_type_name = exp_type_name
        self._message = message
        self._skip_frames = skip_frames

    def __str__(self):
        """Used when printing out the exception message."""
        if self._message:
            return self._message
        # Otherwise construct the message
        var = self._var_name
        val = self._var_value
        atn = self._var_type_name
        etn = self._exp_type_name or ""
        article = "an" if etn.lstrip("?")[0] in "aioeH" else "a"
        return "Argument `{var}` should be {an} {expected_type}, got {actual_type} {value}".\
               format(var=var, an=article, expected_type=etn, actual_type=atn, value=val)

    @property
    def var_name(self):
        """Variable name."""
        return self._var_name

    @property
    def skip_frames(self):
        """Number of local frames to skip when printing out the stacktrace."""
        return self._skip_frames


# ----------------------------------------------------------------------------------------------------------------------
# Backend exceptions
# ----------------------------------------------------------------------------------------------------------------------

class H2OStartupError(H2OSoftError):
    """Raised by H2OLocalServer when the class fails to launch a server."""


class H2OConnectionError(H2OSoftError):
    """
    Raised when connection to an H2O server cannot be established.

    This can be raised if the connection was not initialized; or the server cannot be reached at the specified address;
    or there is an authentication error; or the request times out; etc.
    """


# This should have been extending from Exception as well; however in old code version all exceptions were
# EnvironmentError's, so for old code to work we extend H2OResponseError from EnvironmentError.
class H2OResponseError(H2OError, EnvironmentError):
    """
    Raised when the server encounters a user error and sends back an H2OErrorV3 response.
    """


class H2OServerError(H2OError):
    """
    Raised when any kind of server error is encountered.

    This includes:

        - server returning HTTP status 500,
        - server sending malformed JSON,
        - server returning an unexpected response (e.g. lacking a "__schema" field),
        - server indicating that it is in an unhealthy state,
        - etc.
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


# ----------------------------------------------------------------------------------------------------------------------
# H2OJobCancelled
# ----------------------------------------------------------------------------------------------------------------------

class H2OJobCancelled(H2OError):
    """
    Raised when the user interrupts a running job.

    By default, this exception will not trigger any output (as if it is caught and ignored), however the user still
    has an ability to catch this explicitly and perform a custom action.
    """


# ----------------------------------------------------------------------------------------------------------------------
# Warnings 
# ----------------------------------------------------------------------------------------------------------------------

class H2ODeprecationWarning(DeprecationWarning):
    """
    Identifies deprecations in the h2o package.
    """
    pass
