# -*- encoding: utf-8 -*-
"""
Class for communication with an H2O server.

`H2OConnection` is the main class of this module, and it handles the connection itself:
    hc = H2OConnection.open() : open a new connection
    hc.request(endpoint, [data|json|filename]) : make a REST API request to the server
    hc.info() : return information about the current connection
    hc.close() : close the connection
    hc.session_id : current session id

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import atexit
import os
import sys
import tempfile
import time
from warnings import warn

import requests
from requests.auth import AuthBase

from h2o.backend.server import H2OLocalServer
from h2o.exceptions import H2OConnectionError, H2OServerError, H2OResponseError, H2OValueError
from h2o.schemas.cloud import H2OCluster
from h2o.schemas.error import H2OErrorV3, H2OModelBuilderErrorV3
from h2o.two_dim_table import H2OTwoDimTable
from h2o.utils.backward_compatibility import backwards_compatible, CallableString
from h2o.utils.compatibility import *  # NOQA
from h2o.utils.shared_utils import stringify_list, print2
from h2o.utils.typechecks import (assert_is_type, assert_matches, assert_satisfies, assert_maybe_numeric, is_str)
from h2o.model.metrics_base import (H2ORegressionModelMetrics, H2OClusteringModelMetrics, H2OBinomialModelMetrics,
                                    H2OMultinomialModelMetrics, H2OAutoEncoderModelMetrics)

__all__ = ("H2OConnection", )



class H2OConnection(backwards_compatible()):
    """
    Connection handle to an H2O cluster.

    In a typical scenario you don't need to access this class directly. Instead use :func:`h2o.connect` to
    establish a connection, and :func:`h2o.api` to make requests to the backend H2O server. However if your
    use-case is not typical, then read on.

    Instances of this class may only be created through a static method :meth:`open`::

        hc = H2OConnection.open(...)

    Once opened, the connection remains active until the script exits (or until you explicitly :meth:`close` it).
    If the script exits with an exception, then the connection will fail to close, and the backend server will
    keep all the temporary frames and the open session.

    Alternatively you can use this class as a context manager, which will ensure that the connection gets closed
    at the end of the ``with ...`` block even if an exception occurs::

        with H2OConnection.open() as hc:
            hc.info().pprint()

    Once the connection is established, you can send REST API requests to the server using :meth:`request`.
    """

    @staticmethod
    def open(server=None, url=None, ip=None, port=None, https=None, auth=None, verify_ssl_certificates=True,
             proxy=None, cluster_name=None, verbose=True):
        r"""
        Establish connection to an existing H2O server.

        The connection is not kept alive, so what this method actually does is it attempts to connect to the
        specified server, and checks that the server is healthy and responds to REST API requests. If the H2O server
        cannot be reached, an :class:`H2OConnectionError` will be raised. On success this method returns a new
        :class:`H2OConnection` object, and it is the only "official" way to create instances of this class.

        There are 3 ways to specify the target to connect to (these settings are mutually exclusive):

            * pass a ``server`` option,
            * pass the full ``url`` for the connection,
            * provide a triple of parameters ``ip``, ``port``, ``https``.

        :param H2OLocalServer server: connect to the specified local server instance. There is a slight difference
            between connecting to a local server by specifying its ip and address, and connecting through
            an H2OLocalServer instance: if the server becomes unresponsive, then having access to its process handle
            will allow us to query the server status through OS, and potentially provide snapshot of the server's
            error log in the exception information.
        :param url: full url of the server to connect to.
        :param ip: target server's IP address or hostname (default "localhost").
        :param port: H2O server's port (default 54321).
        :param https: if True then connect using https instead of http (default False).
        :param verify_ssl_certificates: if False then SSL certificate checking will be disabled (default True). This
            setting should rarely be disabled, as it makes your connection vulnerable to man-in-the-middle attacks. When
            used, it will generate a warning from the requests library. Has no effect when ``https`` is False.
        :param auth: authentication token for connecting to the remote server. This can be either a
            (username, password) tuple, or an authenticator (AuthBase) object. Please refer to the documentation in
            the ``requests.auth`` module.
        :param proxy: url address of a proxy server. If you do not specify the proxy, then the requests module
            will attempt to use a proxy specified in the environment (in HTTP_PROXY / HTTPS_PROXY variables). We
            check for the presence of these variables and issue a warning if they are found. In order to suppress
            that warning and use proxy from the environment, pass ``proxy="(default)"``.
        :param cluster_name: name of the H2O cluster to connect to. This option is used from Steam only.
        :param verbose: if True, then connection progress info will be printed to the stdout.

        :returns: A new :class:`H2OConnection` instance.
        :raises H2OConnectionError: if the server cannot be reached.
        :raises H2OServerError: if the server is in an unhealthy state (although this might be a recoverable error, the
            client itself should decide whether it wants to retry or not).
        """
        if server is not None:
            assert_is_type(server, H2OLocalServer)
            assert_is_type(ip, None, "`ip` should be None when `server` parameter is supplied")
            assert_is_type(url, None, "`ip` should be None when `server` parameter is supplied")
            if not server.is_running():
                raise H2OConnectionError("Unable to connect to server because it is not running")
            ip = server.ip
            port = server.port
            scheme = server.scheme
        elif url is not None:
            assert_is_type(url, str)
            assert_is_type(ip, None, "`ip` should be None when `url` parameter is supplied")
            # We don't allow any Unicode characters in the URL. Maybe some day we will...
            match = assert_matches(url, r"^(https?)://((?:[\w-]+\.)*[\w-]+):(\d+)/?$")
            scheme = match.group(1)
            ip = match.group(2)
            port = int(match.group(3))
        else:
            if ip is None: ip = str("localhost")
            if port is None: port = 54321
            if https is None: https = False
            if is_str(port) and port.isdigit(): port = int(port)
            assert_is_type(ip, str)
            assert_is_type(port, int)
            assert_is_type(https, bool)
            assert_matches(ip, r"(?:[\w-]+\.)*[\w-]+")
            assert_satisfies(port, 1 <= port <= 65535)
            scheme = "https" if https else "http"

        if verify_ssl_certificates is None: verify_ssl_certificates = True
        assert_is_type(verify_ssl_certificates, bool)
        assert_is_type(proxy, str, None)
        assert_is_type(auth, AuthBase, (str, str), None)
        assert_is_type(cluster_name, str, None)

        conn = H2OConnection()
        conn._verbose = bool(verbose)
        conn._local_server = server
        conn._base_url = "%s://%s:%d" % (scheme, ip, port)
        conn._verify_ssl_cert = bool(verify_ssl_certificates)
        conn._auth = auth
        conn._cluster_name = cluster_name
        conn._proxies = None
        if proxy and proxy != "(default)":
            conn._proxies = {scheme: proxy}
        elif not proxy:
            # Give user a warning if there are any "*_proxy" variables in the environment. [PUBDEV-2504]
            # To suppress the warning pass proxy = "(default)".
            for name in os.environ:
                if name.lower() == scheme + "_proxy":
                    warn("Proxy is defined in the environment: %s. "
                         "This may interfere with your H2O Connection." % os.environ[name])

        try:
            # Make a fake _session_id, otherwise .request() will complain that the connection is not initialized
            retries = 20 if server else 5
            conn._stage = 1
            conn._timeout = 3.0
            conn._cluster_info = conn._test_connection(retries)
            # If a server is unable to respond within 1s, it should be considered a bug. However we disable this
            # setting for now, for no good reason other than to ignore all those bugs :(
            conn._timeout = None
            atexit.register(lambda: conn.close())
        except:
            # Reset _session_id so that we know the connection was not initialized properly.
            conn._stage = 0
            raise
        return conn


    def request(self, endpoint, data=None, json=None, filename=None):
        """
        Perform a REST API request to the backend H2O server.

        :param endpoint: (str) The endpoint's URL, for example "GET /4/schemas/KeyV4"
        :param data: data payload for POST (and sometimes GET) requests. This should be a dictionary of simple
            key/value pairs (values can also be arrays), which will be sent over in x-www-form-encoded format.
        :param json: also data payload, but it will be sent as a JSON body. Cannot be used together with `data`.
        :param filename: file to upload to the server. Cannot be used with `data` or `json`.

        :returns: an H2OResponse object representing the server's response
        :raises H2OConnectionError: if the H2O server cannot be reached (or connection is not initialized)
        :raises H2OServerError: if there was a server error (http 500), or server returned malformed JSON
        :raises H2OResponseError: if the server returned an H2OErrorV3 response (e.g. if the parameters were invalid)
        """
        if self._stage == 0: raise H2OConnectionError("Connection not initialized; run .connect() first.")
        if self._stage == -1: raise H2OConnectionError("Connection was closed, and can no longer be used.")

        # Prepare URL
        assert_is_type(endpoint, str)
        match = assert_matches(str(endpoint), r"^(GET|POST|PUT|DELETE|PATCH|HEAD) (/.*)$")
        method = match.group(1)
        urltail = match.group(2)
        url = self._base_url + urltail

        # Prepare data
        if filename is not None:
            assert_is_type(filename, str)
            assert_is_type(json, None, "Argument `json` should be None when `filename` is used.")
            assert_is_type(data, None, "Argument `data` should be None when `filename` is used.")
            assert_satisfies(method, method == "POST",
                             "File uploads can only be done via POST method, got %s" % method)
        elif data is not None:
            assert_is_type(data, dict)
            assert_is_type(json, None, "Argument `json` should be None when `data` is used.")
        elif json is not None:
            assert_is_type(json, dict)

        data = self._prepare_data_payload(data)
        files = self._prepare_file_payload(filename)
        params = None
        if method == "GET" and data:
            params = data
            data = None

        # Make the request
        start_time = time.time()
        try:
            self._log_start_transaction(endpoint, data, json, files, params)
            headers = {"User-Agent": "H2O Python client/" + sys.version.replace("\n", ""),
                       "X-Cluster": self._cluster_name}
            resp = requests.request(method=method, url=url, data=data, json=json, files=files, params=params,
                                    headers=headers, timeout=self._timeout,
                                    auth=self._auth, verify=self._verify_ssl_cert, proxies=self._proxies)
            self._log_end_transaction(start_time, resp)
            return self._process_response(resp)

        except (requests.exceptions.ConnectionError, requests.exceptions.HTTPError) as e:
            if self._local_server and not self._local_server.is_running():
                self._log_end_exception("Local server has died.")
                raise H2OConnectionError("Local server has died unexpectedly. RIP.")
            else:
                self._log_end_exception(e)
                raise H2OConnectionError("Unexpected HTTP error: %s" % e)
        except requests.exceptions.Timeout as e:
            self._log_end_exception(e)
            elapsed_time = time.time() - start_time
            raise H2OConnectionError("Timeout after %.3fs" % elapsed_time)
        except H2OResponseError as e:
            err = e.args[0]
            err.endpoint = endpoint
            err.payload = (data, json, files, params)
            raise


    def info(self, refresh=False):
        """
        Information about the current state of the connection, or None if it has not been initialized properly.

        :param refresh: If False, then retrieve the latest known info; if True then fetch the newest info from the
            server. Usually you want `refresh` to be True, except right after establishing a connection when it is
            still fresh.
        :return: H2OCluster object.
        """
        if self._stage == 0: return None
        if refresh:
            self._cluster_info = self.request("GET /3/Cloud")
        self._cluster_info.connection = self
        return self._cluster_info


    def close(self):
        """
        Close an existing connection; once closed it cannot be used again.

        Strictly speaking it is not necessary to close all connection that you opened -- we have several mechanisms
        in place that will do so automatically (__del__(), __exit__() and atexit() handlers), however there is also
        no good reason to make this method private.
        """
        if self._session_id:
            try:
                self.request("DELETE /4/sessions/%s" % self._session_id)
                self._print("H2O session %s closed." % self._session_id)
            except:
                pass
            self._session_id = None
        self._stage = -1


    @property
    def session_id(self):
        """
        Return the session id of the current connection.

        The session id is issued (through an API request) the first time it is requested, but no sooner. This is
        because generating a session id puts it into the DKV on the server, which effectively locks the cloud. Once
        issued, the session id will stay the same until the connection is closed.
        """
        if self._session_id is None:
            self._session_id = self.request("POST /4/sessions")["session_key"]
        return CallableString(self._session_id)

    @property
    def base_url(self):
        """Base URL of the server, without trailing ``"/"``. For example: ``"https://example.com:54321"``."""
        return self._base_url

    @property
    def proxy(self):
        """URL of the proxy server used for the connection (or None if there is no proxy)."""
        if self._proxies is None: return None
        return self._proxies.values()[0]

    @property
    def requests_count(self):
        """Total number of request requests made since the connection was opened (used for debug purposes)."""
        return self._requests_counter

    @property
    def timeout_interval(self):
        """Timeout length for each request, in seconds."""
        return self._timeout

    @timeout_interval.setter
    def timeout_interval(self, v):
        assert_maybe_numeric(v)
        self._timeout = v


    def shutdown_server(self, prompt):
        """
        Shut down the specified server.

        This method checks if H2O is running at the specified IP address and port, and if it is, shuts down that H2O
        instance. All data will be lost.

        :param prompt: A logical value indicating whether to prompt the user before shutting down the H2O server.
        """
        if not self.cluster_is_up(): return
        assert_is_type(prompt, bool)
        if prompt:
            question = "Are you sure you want to shutdown the H2O instance running at %s (Y/N)? " % self._base_url
            response = input(question)  # works in Py2 & Py3 because redefined in h2o.utils.compatibility module
        else:
            response = "Y"
        if response.lower() in {"y", "yes"}:
            self.request("POST /3/Shutdown")
            self.close()


    def cluster_is_up(self):
        """
        Determine if an H2O cluster is running or not.

        :returns: True if the cluster is up; False otherwise
        """
        try:
            if self._local_server and not self._local_server.is_running(): return False
            self.request("GET /")
            return True
        except (H2OConnectionError, H2OServerError):
            return False

    def start_logging(self, dest=None):
        """
        Start logging all API requests to the provided destination.

        :param dest: Where to write the log: either a filename (str), or an open file handle (file). If not given,
            then a new temporary file will be created.
        """
        assert_is_type(dest, None, str, type(sys.stdout))
        if dest is None:
            dest = os.path.join(tempfile.mkdtemp(), "h2o-connection.log")
        self._print("Now logging all API requests to file %r" % dest)
        self._is_logging = True
        self._logging_dest = dest

    def stop_logging(self):
        """Stop logging API requests."""
        if self._is_logging:
            self._print("Logging stopped.")
            self._is_logging = False


    #-------------------------------------------------------------------------------------------------------------------
    # PRIVATE
    #-------------------------------------------------------------------------------------------------------------------

    def __init__(self):
        """[Private] Please use H2OConnection.connect() to create H2OConnection objects."""
        super(H2OConnection, self).__init__()
        globals()["__H2OCONN__"] = self  # for backward-compatibility: __H2OCONN__ is the latest instantiated object
        self._stage = 0             # 0 = not connected, 1 = connected, -1 = disconnected
        self._session_id = None     # Rapids session id; issued upon request only
        self._base_url = None       # "{scheme}://{ip}:{port}"
        self._verify_ssl_cert = None
        self._auth = None           # Authentication token
        self._proxies = None        # `proxies` dictionary in the format required by the requests module
        self._cluster_name = None
        self._cluster_info = None   # Latest result of "GET /3/Cloud" request
        self._verbose = None        # Print detailed information about connection status
        self._requests_counter = 0  # how many API requests were made
        self._timeout = None        # timeout for a single request (in seconds)
        self._is_logging = False    # when True, log every request
        self._logging_dest = None   # where the log messages will be written, either filename or open file handle
        self._local_server = None   # H2OLocalServer instance to which we are connected (if known)
        # self.start_logging(sys.stdout)


    def _test_connection(self, max_retries=5):
        """
        Test that the H2O cluster can be reached, and retrieve basic cloud status info.

        :param max_retries: Number of times to try to connect to the cloud (with 0.2s intervals)
        :return Cloud information (an H2OCluster object)
        :raise H2OConnectionError, H2OServerError
        """
        self._print("Connecting to H2O server at " + self._base_url, end="..")
        cld = None
        errors = []
        for _ in range(max_retries):
            self._print(".", end="", flush=True)
            if self._local_server and not self._local_server.is_running():
                raise H2OServerError("Local server was unable to start")
            try:
                cld = self.request("GET /3/Cloud")
                if cld.consensus and cld.cloud_healthy:
                    self._print(" successful!")
                    return cld
                else:
                    if cld.consensus and not cld.cloud_healthy:
                        msg = "in consensus but not healthy"
                    elif not cld.consensus and cld.cloud_healthy:
                        msg = "not in consensus but healthy"
                    else:
                        msg = "not in consensus and not healthy"
                    errors.append("Cloud is in a bad shape: %s (size = %d, bad nodes = %d)"
                                  % (msg, cld.cloud_size, cld.bad_nodes))
            except (H2OConnectionError, H2OServerError) as e:
                message = str(e)
                if "\n" in message: message = message[:message.index("\n")]
                errors.append("[%s.%02d] %s: %s" %
                              (time.strftime("%M:%S"), int(time.time() * 100) % 100, e.__class__.__name__, message))
            # Cloud too small, or voting in progress, or server is not up yet; sleep then try again
            time.sleep(0.2)

        self._print(" failed.")
        if cld and not cld.cloud_healthy:
            raise H2OServerError("Cluster reports unhealthy status")
        if cld and not cld.consensus:
            raise H2OServerError("Cluster cannot reach consensus")
        else:
            raise H2OConnectionError("Could not establish link to the H2O cloud %s after %d retries\n%s"
                                     % (self._base_url, max_retries, "\n".join(errors)))


    @staticmethod
    def _prepare_data_payload(data):
        """
        Make a copy of the `data` object, preparing it to be sent to the server.

        The data will be sent via x-www-form-urlencoded or multipart/form-data mechanisms. Both of them work with
        plain lists of key/value pairs, so this method converts the data into such format.
        """
        if not data: return None
        res = {}
        for key, value in viewitems(data):
            if value is None: continue  # don't send args set to None so backend defaults take precedence
            if isinstance(value, list):
                value = stringify_list(value)
            elif isinstance(value, dict) and "__meta" in value and value["__meta"]["schema_name"].endswith("KeyV3"):
                value = value["name"]
            else:
                value = str(value)
            # Some hackery here... It appears that requests library cannot stomach "upgraded" strings if they contain
            # certain characters such as '/'. Therefore we explicitly cast them to their native representation.
            # Reproduction steps:
            #   >>> import requests
            #   >>> from future.types import newstr as str
            #   >>> requests.get("http://www.google.com/search", params={"q": str("/foo/bar")})
            # (throws a "KeyError 47" exception).
            # if PY2 and hasattr(value, "__native__"): value = value.__native__()
            # if PY2 and hasattr(key, "__native__"): key = key.__native__()
            res[key] = value
        return res


    @staticmethod
    def _prepare_file_payload(filename):
        """
        Prepare `filename` to be sent to the server.

        The "preparation" consists of creating a data structure suitable
        for passing to requests.request().
        """
        if not filename: return None
        absfilename = os.path.abspath(filename)
        if not os.path.exists(absfilename):
            raise H2OValueError("File %s does not exist" % filename, skip_frames=1)
        return {os.path.basename(absfilename): open(absfilename, "rb")}


    def _log_start_transaction(self, endpoint, data, json, files, params):
        """Log the beginning of an API request."""
        # TODO: add information about the caller, i.e. which module + line of code called the .request() method
        #       This can be done by fetching current traceback and then traversing it until we find the request function
        self._requests_counter += 1
        if not self._is_logging: return
        msg = "\n---- %d --------------------------------------------------------\n" % self._requests_counter
        msg += "[%s] %s\n" % (time.strftime("%H:%M:%S"), endpoint)
        if params is not None: msg += "     params: {%s}\n" % ", ".join("%s:%s" % item for item in viewitems(params))
        if data is not None:   msg += "     body: {%s}\n" % ", ".join("%s:%s" % item for item in viewitems(data))
        if json is not None:   msg += "     json: %s\n" % json.dumps(json)
        if files is not None:  msg += "     file: %s\n" % ", ".join(f.name for f in viewvalues(files))
        self._log_message(msg + "\n")


    def _log_end_transaction(self, start_time, response):
        """Log response from an API request."""
        if not self._is_logging: return
        elapsed_time = int((time.time() - start_time) * 1000)
        msg = "<<< HTTP %d %s   (%d ms)\n" % (response.status_code, response.reason, elapsed_time)
        if "Content-Type" in response.headers:
            msg += "    Content-Type: %s\n" % response.headers["Content-Type"]
        msg += response.text
        self._log_message(msg + "\n\n")


    def _log_end_exception(self, exception):
        """Log API request that resulted in an exception."""
        if not self._is_logging: return
        self._log_message(">>> %s\n\n" % str(exception))


    def _log_message(self, msg):
        """
        Log the message `msg` to the destination `self._logging_dest`.

        If this destination is a file name, then we append the message to the file and then close the file
        immediately. If the destination is an open file handle, then we simply write the message there and do not
        attempt to close it.
        """
        if is_str(self._logging_dest):
            with open(self._logging_dest, "at", encoding="utf-8") as f:
                f.write(msg)
        else:
            self._logging_dest.write(msg)


    @staticmethod
    def _process_response(response):
        """
        Given a response object, prepare it to be handed over to the external caller.

        Preparation steps include:
           * detect if the response has error status, and convert it to an appropriate exception;
           * detect Content-Type, and based on that either parse the response as JSON or return as plain text.
        """
        content_type = response.headers["Content-Type"] if "Content-Type" in response.headers else ""
        if ";" in content_type:  # Remove a ";charset=..." part
            content_type = content_type[:content_type.index(";")]
        status_code = response.status_code

        # Auto-detect response type by its content-type. Decode JSON, all other responses pass as-is.
        if content_type == "application/json":
            try:
                data = response.json(object_pairs_hook=H2OResponse)
            except (JSONDecodeError, requests.exceptions.ContentDecodingError) as e:
                raise H2OServerError("Malformed JSON from server (%s):\n%s" % (str(e), response.text))
        else:
            data = response.text

        # Success (200 = "Ok", 201 = "Created", 202 = "Accepted", 204 = "No Content")
        if status_code in {200, 201, 202, 204}:
            return data

        # Client errors (400 = "Bad Request", 404 = "Not Found", 412 = "Precondition Failed")
        if status_code in {400, 404, 412} and isinstance(data, (H2OErrorV3, H2OModelBuilderErrorV3)):
            raise H2OResponseError(data)

        # Server errors (notably 500 = "Server Error")
        # Note that it is possible to receive valid H2OErrorV3 object in this case, however it merely means the server
        # did not provide the correct status code.
        raise H2OServerError("HTTP %d %s:\n%r" % (status_code, response.reason, data))



    def _print(self, msg, flush=False, end="\n"):
        """Helper function to print connection status messages when in verbose mode."""
        if self._verbose:
            print2(msg, end=end, flush=flush)


    def __repr__(self):
        if self._stage == 0:
            return "<H2OConnection uninitialized>"
        elif self._stage == 1:
            sess = "session %s" % self._session_id if self._session_id else "no session"
            return "<H2OConnection to %s, %s>" % (self._base_url, sess)
        else:
            return "<H2OConnection closed>"

    def __enter__(self):
        """Called when an H2OConnection object is created within the ``with ...`` statement."""
        return self

    def __exit__(self, *args):
        """Called at the end of the ``with ...`` statement."""
        self.close()
        assert len(args) == 3  # Avoid warning about unused args...
        return False  # ensure that any exception will be re-raised



    #-------------------------------------------------------------------------------------------------------------------
    # DEPRECATED
    #
    # Access to any of these vars / methods will produce deprecation warnings.
    # Consult backwards_compatible.py for the description of these vars.
    #
    # These methods are deprecated since July 2016. Please remove them if it's 2017 already...
    #-------------------------------------------------------------------------------------------------------------------

    _bcsv = {"__ENCODING__": "utf-8", "__ENCODING_ERROR__": "replace"}
    _bcsm = {
        "default": lambda: _deprecated_default(),
        "jar_paths": lambda: list(getattr(H2OLocalServer, "_jar_paths")()),
        "rest_version": lambda: 3,
        "https": lambda: __H2OCONN__.base_url.split(":")[0] == "https",
        "ip": lambda: __H2OCONN__.base_url.split(":")[1][2:],
        "port": lambda: __H2OCONN__.base_url.split(":")[2],
        "username": lambda: _deprecated_username(),
        "password": lambda: _deprecated_password(),
        "insecure": lambda: not getattr(__H2OCONN__, "_verify_ssl_cert"),
        "current_connection": lambda: __H2OCONN__,
        "check_conn": lambda: _deprecated_check_conn(),
        # "cluster_is_up" used to be static (required `conn` parameter) -> now it's non-static w/o any patching needed
        "make_url": lambda url_suffix, _rest_version=3: __H2OCONN__.make_url(url_suffix, _rest_version),
        "get": lambda url_suffix, **kwargs: _deprecated_get(__H2OCONN__, url_suffix, **kwargs),
        "post": lambda url_suffix, file_upload_info=None, **kwa:
            _deprecated_post(__H2OCONN__, url_suffix, file_upload_info=file_upload_info, **kwa),
        "delete": lambda url_suffix, **kwargs: _deprecated_delete(__H2OCONN__, url_suffix, **kwargs),
        "get_json": lambda url_suffix, **kwargs: _deprecated_get(__H2OCONN__, url_suffix, **kwargs),
        "post_json": lambda url_suffix, file_upload_info=None, **kwa:
            _deprecated_post(__H2OCONN__, url_suffix, file_upload_info=file_upload_info, **kwa),
        "rest_ctr": lambda: __H2OCONN__.requests_count,
    }
    _bcim = {
        "make_url": lambda self, url_suffix, _rest_version=3:
            "/".join([self._base_url, str(_rest_version), url_suffix]),
        "get": lambda *args, **kwargs: _deprecated_get(*args, **kwargs),
        "post": lambda *args, **kwargs: _deprecated_post(*args, **kwargs),
        "delete": lambda *args, **kwargs: _deprecated_delete(*args, **kwargs),
        "get_json": lambda *args, **kwargs: _deprecated_get(*args, **kwargs),
        "post_json": lambda *args, **kwargs: _deprecated_post(*args, **kwargs),
    }




class H2OResponse(dict):
    """Temporary..."""

    def __new__(cls, keyvals):
        # This method is called by the simplejson.json(object_pairs_hook=<this>)
        # `keyvals` is a list of (key,value) tuples. For example:
        #    [("schema_version", 3), ("schema_name", "InitIDV3"), ("schema_type", "Iced")]
        schema = None
        for k, v in keyvals:
            if k == "__meta" and isinstance(v, dict):
                schema = v["schema_name"]
                break
            if k == "__schema" and is_str(v):
                schema = v
                break
        if schema == "CloudV3": return H2OCluster(keyvals)
        if schema == "H2OErrorV3": return H2OErrorV3(keyvals)
        if schema == "H2OModelBuilderErrorV3": return H2OModelBuilderErrorV3(keyvals)
        if schema == "TwoDimTableV3": return H2OTwoDimTable.make(keyvals)
        if schema == "ModelMetricsRegressionV3": return H2ORegressionModelMetrics.make(keyvals)
        if schema == "ModelMetricsClusteringV3": return H2OClusteringModelMetrics.make(keyvals)
        if schema == "ModelMetricsBinomialV3": return H2OBinomialModelMetrics.make(keyvals)
        if schema == "ModelMetricsMultinomialV3": return H2OMultinomialModelMetrics.make(keyvals)
        if schema == "ModelMetricsAutoEncoderV3": return H2OAutoEncoderModelMetrics.make(keyvals)
        return super(H2OResponse, cls).__new__(cls, keyvals)

    # def __getattr__(self, key):
    #     """This gets invoked for any attribute "key" that is NOT yet defined on the object."""
    #     if key in self:
    #         return self[key]
    #     return None


# Find the exception that occurs on invalid JSON input
JSONDecodeError, _r = None, None
try:
    _r = requests.Response()
    _r._content = b"haha"
    _r.json()
except Exception as exc:
    JSONDecodeError = type(exc)
    del _r


#-----------------------------------------------------------------------------------------------------------------------
# Deprecated method implementations
#-----------------------------------------------------------------------------------------------------------------------

__H2OCONN__ = H2OConnection()  # Latest instantiated H2OConnection object. Do not use in any new code!
__H2O_REST_API_VERSION__ = 3   # Has no actual meaning

def _deprecated_default():
    H2OConnection.__ENCODING__ = "utf-8"
    H2OConnection.__ENCODING_ERROR__ = "replace"

def _deprecated_username():
    auth = getattr(__H2OCONN__, "_auth")
    return auth[0] if isinstance(auth, tuple) else None

def _deprecated_password():
    auth = getattr(__H2OCONN__, "_auth")
    return auth[1] if isinstance(auth, tuple) else None

def _deprecated_check_conn():
    if not __H2OCONN__:
        raise H2OConnectionError("No active connection to an H2O cluster. Try calling `h2o.connect()`")
    return __H2OCONN__

def _deprecated_get(self, url_suffix, **kwargs):
    restver = kwargs.pop("_rest_version") if "_rest_version" in kwargs else 3
    endpoint = "GET /%d/%s" % (restver, url_suffix)
    return self.request(endpoint, data=kwargs)

def _deprecated_post(self, url_suffix, **kwargs):
    restver = kwargs.pop("_rest_version") if "_rest_version" in kwargs else 3
    endpoint = "POST /%d/%s" % (restver, url_suffix)
    filename = None
    if "file_upload_info" in kwargs:
        filename = next(iter(viewvalues(kwargs.pop("file_upload_info"))))
    return self.request(endpoint, data=kwargs, filename=filename)

def _deprecated_delete(self, url_suffix, **kwargs):
    restver = kwargs.pop("_rest_version") if "_rest_version" in kwargs else 3
    endpoint = "DELETE /%d/%s" % (restver, url_suffix)
    return self.request(endpoint, data=kwargs)


def end_session():
    """Deprecated, use connection.close() instead."""
    print("Warning: end_session() is deprecated")
    __H2OCONN__.close()
