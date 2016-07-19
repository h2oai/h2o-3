# -*- encoding: utf-8 -*-
"""
Collection of methods for communication with H₂O servers.

`H2OConnection` is the main class of this module, and it handles the connection itself. Public interface:
    hc = H2OConnection.open() : open a new connection
    hc.request(endpoint, [data|json|filename]) : make a REST API request to the server
    hc.info() : return information about the current connection
    hc.close() : close the connection
    hc.session_id() : return the current session id

`H2OLocalServer`
    hs = H2OLocalServer.start() : start a new local server
    assert hs.is_running() : check if the server is running
    hs.shutdown() : shut down the server

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import atexit
import os
import subprocess
import sys
import tempfile
import time
from random import choice
from sysconfig import get_config_var
from warnings import warn

import requests
from requests.auth import AuthBase

from .compatibility import *  # NOQA
from .schemas.cloud import CloudV3
from .schemas.error import H2OErrorV3, H2OModelBuilderErrorV3
from .two_dim_table import H2OTwoDimTable
from .utils.backward_compatibility import backwards_compatible, CallableString
from .utils.shared_utils import stringify_list

__all__ = ("H2OConnection", "H2OLocalServer", "H2OStartupError", "H2OConnectionError", "H2OServerError",
           "H2OResponseError")



#----------------------------------------------------------------------------------------------------------------------#
#   H2OConnection
#----------------------------------------------------------------------------------------------------------------------#

class H2OConnection(backwards_compatible()):
    """
    Single connection to an H₂O server.

    Instances of this class are created through a static method `.open()`:
        conn = H2OConnection.open(...)    connect to an existing H₂O server;
    We will autom
    You can also use this class as a context manager:
        with H2OConnection.connect() as conn:
            conn.info().pprint()
    The connection will be automatically closed at the end of the `with ...` block.

    This class contains methods for performing the common REST methods GET, POST, and DELETE.

    TODO: Maybe move start() and 4 private methods _jar_paths, _launch_server, _find_java, _tmp_file into a separate
    class H2OLocalServer. Better communicate with the subprocess. Right now if the server dies, no exception is
    raised anywhere. Also if the server decides to start listening to a port other than 54321, we have no way of
    knowing this.
    """

    @staticmethod
    def open(server=None, url=None, ip=None, port=None, https=None, verify_ssl_certificates=True, auth=None,
             proxy=None, cluster_name=None, verbose=True):
        """
        Establish connection to an existing H₂O server at address ip:port.

        The connection is not kept alive, so what this method actually does is it attempts to connect to the
        specified server, and checks that the server is healthy and responds to REST API requests. If the H₂O server
        cannot be reached, an `H2OConnectionError` will be raised. On success this method returns a new
        `H2OConnection` object, and it is the only "official" way to create instances of this class.

        There are 3 ways to specify which server to connect to (each of these settings are exclusive):
            * Either passing a `server` option,
            * Or passing the full `url` for the connection,
            * Or providing a triple of parameters `ip`, `port`, `https`.

        :param server: (H2OLocalServer) connect to the specified local server instance. There is a slight difference
            between connecting to a local server by specifying its ip and address, and connecting through
            an H2OLocalServer instance: if the server becomes unresponsive, then having access to its process handle
            will allow us to query the server status through OS, and potentially provide snapshot of the server's
            error log in the exception information.
        :param url: Full URL of the server to connect to.
        :param ip: Target server's IP address or hostname (default "localhost").
        :param port: H₂O server's port (default 54321).
        :param https: If True then connect using https instead of http (default False).
        :param verify_ssl_certificates: If False then SSL certificate checking will be disabled (default True). This
            setting should rarely be disabled, as it makes your connection vulnerable to man-in-the-middle attacks. When
            used, it will generate a warning from the requests library. Has no effect when `https` is False.
        :param auth: Authentication token for connecting to the remote server. This can be either a
            (username, password) tuple, or an authenticator (AuthBase) object. Please refer to the documentation in
            the `requests.auth` module.
        :param proxy: (str) URL address of a proxy server. If you do not specify the proxy, then the requests module
            will attempt to use a proxy specified in the environment (in HTTP_PROXY / HTTPS_PROXY variables). We
            check for the presence of these variables and issue a warning if they are found. In order to suppress
            that warning and use proxy from the environment, pass `proxy`="(default)".
        :param cluster_name: Name of the H₂O cluster to connect to. This option is used from Steam only.
        :param verbose: If True (default), then connection progress info will be printed to the stdout.
        :return A new H2OConnection instance.
        :raise H2OConnectionError if the server cannot be reached.
        :raise H2OServerError if the server is in an unhealthy state (although this might be a recoverable error, the
            client itself should decide whether it wants to retry or not).
        """
        if server is not None:
            assert_is_type(server, "server", H2OLocalServer)
            assert ip is None and port is None and https is None and url is None, \
                "`url`, `ip`, `port` and `https` parameters cannot be used together with `server`"
            ip = server.ip
            port = server.port
            scheme = server.scheme
        elif url is not None:
            assert_is_str(url, "url")
            assert ip is None and port is None and https is None and server is None, \
                "`server`, `ip`, `port` and `https` parameters cannot be used together with `url`"
            parts = url.rstrip("/").split(":")
            assert len(parts) == 3 and (parts[0] in {"http", "https"}) and parts[2].isdigit(), \
                "Invalid URL parameter '%s'" % url
            scheme = parts[0]
            ip = parts[1][2:]
            port = int(parts[2])
        else:
            if ip is None: ip = str("localhost")
            if port is None: port = 54321
            if https is None: https = False
            if is_str(port) and port.isdigit(): port = int(port)
            assert_is_str(ip, "ip")
            assert_is_int(port, "port")
            assert_is_bool(https, "https")
            assert 1 <= port <= 65535, "Invalid `port` number: %d" % port
            scheme = "https" if https else "http"

        if verify_ssl_certificates is None: verify_ssl_certificates = True
        assert_is_bool(verify_ssl_certificates, "verify_ssl_certificates")
        assert_maybe_str(proxy, "proxy")
        assert auth is None or isinstance(auth, tuple) and len(auth) == 2 or isinstance(auth, AuthBase), \
            "Invalid authentication token of type %s" % type(auth)
        assert_maybe_str(cluster_name, "cluster_name")

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
        Perform a REST API request to the backend H₂O server.

        :param endpoint: (str) The endpoint's URL, for example "GET /4/schemas/KeyV4"
        :param data: data payload for POST (and sometimes GET) requests. This should be a dictionary of simple
            key/value pairs (values can also be arrays), which will be sent over in x-www-form-encoded format.
        :param json: also data payload, but it will be sent as a JSON body. Cannot be used together with `data`.
        :param filename: file to upload to the server. Cannot be used with `data` or `json`.
        :return: an H2OResponse object representing the server's response
        :raise ValueError if the endpoint's URL is invalid
        :raise H2OConnectionError if the H₂O server cannot be reached (or connection is not initialized)
        :raise H2OServerError if there was a server error (http 500), or server returned malformed JSON
        :raise H2OResponseError if the server returned an H2OErrorV3 response (e.g. if the parameters were invalid)
        """
        if self._stage == 0: raise H2OConnectionError("Connection not initialized; run .connect() first.")
        if self._stage == -1: raise H2OConnectionError("Connection was closed, and can no longer be used.")

        # Prepare URL
        assert_is_str(endpoint, "endpoint")
        if endpoint.count(" ") != 1:
            raise ValueError("Incorrect endpoint '%s': must be of the form 'METHOD URL'." % endpoint)
        method, urltail = str(endpoint).split(" ", 2)
        if method not in {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD"}:
            raise ValueError("Incorrect method in endpoint '%s'" % endpoint)
        if urltail[0] != "/":
            raise ValueError("Incorrect url in endpoint '%s': should start with '/'" % endpoint)
        url = self._base_url + urltail

        # Prepare data
        if bool(data) + bool(json) + bool(filename) > 1:
            raise ValueError("Only one of parameters `json`, `data`, `file` can be supplied.")
        if filename is not None and method != "POST":
            raise ValueError("File uploads can only be done via POST method, got %s" % endpoint)
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
        :return: CloudV3 object.
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
        """Base URL of the server, without trailing '/'. For example: "https://example.com:54321"."""
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
        assert_maybe_numeric(v, "timeout_interval")
        self._timeout = v


    def shutdown_server(self, prompt):
        """
        Shut down the specified server.

        This method checks if H2O is running at the specified IP address and port, and if it is, shuts down that H2O
        instance. All data will be lost.
        :param self: An H2OConnection object containing the IP address and port of the server running H2O.
        :param prompt: A logical value indicating whether to prompt the user before shutting down the H2O server.
        :return: None
        """
        try:
            if not self.cluster_is_up():
                raise ValueError("There is no H2O instance running at " + self._base_url)
        except:
            # H2O is already shutdown on the java side
            raise ValueError("The H2O instance running at %s has already been shutdown." % self._base_url)
        assert_is_bool(prompt, "prompt")
        if prompt:
            question = "Are you sure you want to shutdown the H2O instance running at %s (Y/N)? " % self._base_url
            response = input(question)  # works in Py2 & Py3 because it's future.builtins.input
        else:
            response = "Y"
        if response == "Y" or response == "y":
            self.request("POST /3/Shutdown")
            self.close()


    def cluster_is_up(self):
        """
        Determine if an H₂O cluster is running or not.

        :return: True if the cluster is up; False otherwise
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
        if dest is None:
            dest = os.path.join(tempfile.mkdtemp(), "h2o-connection.log")
        if not (isinstance(dest, type(sys.stdout)) or is_str(dest)):
            raise ValueError("Logging destination should be either a string (filename), or an open file handle")
        name = dest if is_str(dest) else dest.name
        self._print("Start logging H2OConnection.request() requests into file %s" % name)
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
        """[Internal] Please use H2OConnection.connect() or H2OConnection.start() to create H2OConnection objects."""
        super(H2OConnection, self).__init__()
        globals()["__H2OCONN__"] = self  # for backward-compatibility
        self._stage = 0             # 0 = not connected, 1 = connected, -1 = disconnected
        self._session_id = None     # Rapids session id. Connection is considered established when this is not null
        self._base_url = None       # "{scheme}://{ip}:{port}"
        self._verify_ssl_cert = None
        self._auth = None           # Authentication token
        self._proxies = None        # `proxies` dictionary in the format required by the requests module
        self._cluster_name = None
        self._cluster_info = None   # Latest result of "GET /3/Cloud" request
        self._verbose = None
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
        :return Cloud information (a CloudV3 object)
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
        if not isinstance(data, dict): raise ValueError("Invalid `data` argument, should be a dict: %r" % data)
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
        assert_is_str(filename, "filename")
        absfilename = os.path.abspath(filename)
        if not os.path.exists(absfilename):
            raise ValueError("File %s does not exist" % filename)
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



    def _print(self, *args, **kwargs):
        """Helper function to print connection status messages when in "verbose" mode."""
        if self._verbose:
            flush = False
            if "flush" in kwargs: flush = kwargs.pop("flush")
            print(*args, **kwargs)
            if flush: sys.stdout.flush()


    def __repr__(self):
        if self._stage == 0:
            return "<H2OConnection uninitialized>"
        elif self._stage == 1:
            sess = "session %s" % self._session_id if self._session_id else "no session"
            return "<H2OConnection to %s, %s>" % (self._base_url, sess)
        else:
            return "<H2OConnection closed>"

    def __enter__(self):
        # Called when an H2OConnection object is created within the `with ...` statement.
        return self

    def __exit__(self, *args):
        self.close()
        assert len(args) == 3  # Avoid warning about unused args...
        return False  # ensure that any exception will be re-raised

    def __del__(self):
        # Called when the object is being garbage-collected, but not always...
        self.close()


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



#-----------------------------------------------------------------------------------------------------------------------
#   H2OLocalServer
#-----------------------------------------------------------------------------------------------------------------------

class H2OLocalServer(object):
    """
    Handle to an H₂O server launched locally.

    Public interface:
        hs = H2OLocalServer.start(...)   launch a new local H₂O server
    """

    _TIME_TO_START = 10  # Maximum time we wait for the server to start up (in seconds)
    _TIME_TO_KILL = 3    # Maximum time we wait for the server to shut down until we kill it (in seconds)


    @staticmethod
    def start(jar_path=None, nthreads=-1, enable_assertions=True, max_mem_size=None, min_mem_size=None,
              ice_root=None, port="54321+", verbose=True):
        """
        Start new H₂O server on the local machine.

        :param jar_path: Path to the h2o.jar executable. If not given, then we will search for h2o.jar in the
            locations returned by `._jar_paths()`.
        :param nthreads: Number of threads in the thread pool. This should be related to the number of CPUs used.
            -1 means use all CPUs on the host. A positive integer specifies the number of CPUs directly.
        :param enable_assertions: If True, pass `-ea` option to the JVM.
        :param max_mem_size: Maximum heap size (jvm option Xmx), in bytes.
        :param min_mem_size: Minimum heap size (jvm option Xms), in bytes.
        :param ice_root: A directory where H₂O stores its temporary files. Default location is determined by
            tempfile.mkdtemp().
        :param port: Port where to start the new server. This could be either an integer, or a string of the form
            "DDDDD+", indicating that the server should start looking for an open port starting from DDDDD and up.
        :param verbose: If True, then connection info will be printed to the stdout.
        :return a new H2OLocalServer instance
        """
        assert jar_path is None or is_str(jar_path), "`jar_path` should be string, got %s" % type(jar_path)
        assert jar_path is None or jar_path.endswith("h2o.jar"), \
            "`jar_path` should be a path to an h2o.jar executable, got %s" % jar_path
        assert is_int(nthreads), "`nthreads` should be integer, got %s" % type(nthreads)
        assert nthreads == -1 or 1 <= nthreads <= 4096, "`nthreads` is out of bounds: %d" % nthreads
        assert isinstance(enable_assertions, bool), \
            "`enable_assertions` should be bool, got %s" % type(enable_assertions)
        assert max_mem_size is None or is_int(max_mem_size), \
            "`max_mem_size` should be integer, got %s" % type(max_mem_size)
        assert max_mem_size is None or max_mem_size >= 1 << 25, "`max_mem_size` too small: %d" % max_mem_size
        assert min_mem_size is None or is_int(min_mem_size), \
            "`min_mem_size` should be integer, got %s" % type(min_mem_size)
        assert min_mem_size is None or max_mem_size is None or min_mem_size <= max_mem_size, \
            "`min_mem_size`=%d is larger than the `max_mem_size`=%d" % (min_mem_size, max_mem_size)
        if ice_root:
            assert is_str(ice_root), "`ice_root` should be string, got %r" % type(ice_root)
            assert os.path.isdir(ice_root), "`ice_root` is not a valid directory: %s" % ice_root
        if port is None: port = "54321+"
        baseport = None
        if is_str(port):
            if port.isdigit():
                port = int(port)
            else:
                assert port[-1] == "+" and port[:-1].isdigit(), \
                    "`port` should be of the form 'DDDD+', where D is a digit. Got: %s" % port
                baseport = int(port[:-1])
                port = 0
        assert is_int(port), "`port` should be integer (or string). Got: %s" % type(port)

        hs = H2OLocalServer()
        hs._verbose = bool(verbose)
        hs._jar_path = hs._find_jar(jar_path)
        hs._ice_root = ice_root
        if not ice_root:
            hs._ice_root = tempfile.mkdtemp()
            hs._tempdir = hs._ice_root

        if verbose: print("Attempting to start a local H2O server...")
        hs._launch_server(port=port, baseport=baseport, nthreads=int(nthreads), ea=enable_assertions,
                          mmax=max_mem_size, mmin=min_mem_size)
        if verbose: print("Server is running at %s://%s:%d" % (hs.scheme, hs.ip, hs.port))
        atexit.register(lambda: hs.shutdown())
        return hs


    def is_running(self):
        """Return True if the server process is still running, False otherwise."""
        return self._process is not None and self._process.poll() is None


    def shutdown(self):
        """
        Shut down the server by trying to terminate/kill its process.

        First we attempt to terminate the server process gracefully (sending SIGTERM signal). However after
        _TIME_TO_KILL seconds if the process didn't shutdown, we forcefully kill it with a SIGKILL signal.
        """
        if not self._process: return
        try:
            kill_time = time.time() + self._TIME_TO_KILL
            while self._process.poll() is None and time.time() < kill_time:
                self._process.terminate()
                time.sleep(0.2)
            if self._process().poll() is None:
                self._process.kill()
                time.sleep(0.2)
            if self._verbose:
                print("Local H2O server %s:%s stopped." % (self.ip, self.port))
        except:
            pass
        self._process = None


    @property
    def scheme(self):
        """Connection scheme, 'http' or 'https'."""
        return self._scheme

    @property
    def ip(self):
        """IP address of the server."""
        return self._ip

    @property
    def port(self):
        """Port that the server is listening to."""
        return self._port


    #-------------------------------------------------------------------------------------------------------------------
    # Private
    #-------------------------------------------------------------------------------------------------------------------

    def __init__(self):
        """[Internal] please use H2OLocalServer.start() to launch a new server."""
        self._scheme = None   # "http" or "https"
        self._ip = None
        self._port = None
        self._process = None
        self._verbose = None
        self._jar_path = None
        self._ice_root = None
        self._stdout = None
        self._stderr = None
        self._tempdir = None


    def _find_jar(self, path0=None):
        """
        Return the location of an h2o.jar executable.

        :param path0: Explicitly given h2o.jar path. If provided, then we will simply check whether the file is there,
            otherwise we will search for an executable in locations returned by ._jar_paths().
        :raise H2OStartupError if no h2o.jar executable can be found.
        """
        jar_paths = [path0] if path0 else list(self._jar_paths())
        for jp in jar_paths:
            if os.path.exists(jp):
                return jp
        if self._verbose:
            print("  No jar file found. Paths searched:")
            print("  " + "".join("    %s\n" % jar_paths))
        raise H2OStartupError("Cannot start local server: h2o.jar not found.")

    @staticmethod
    def _jar_paths():
        """Produce potential paths for an h2o.jar executable."""
        # Check if running from an h2o-3 src folder, in which case use the freshly-built h2o.jar
        cwd_chunks = os.path.abspath(".").split(os.path.sep)
        for i in range(len(cwd_chunks), 0, -1):
            if cwd_chunks[i - 1] == "h2o-3":
                yield os.path.sep.join(cwd_chunks[:i] + ["build", "h2o.jar"])
        # Finally try several alternative locations where h2o.jar might be installed
        prefix1 = prefix2 = sys.prefix
        # On Unix-like systems Python typically gets installed into /Library/... or /System/Library/... If one of
        # those paths is sys.prefix, then we also build its counterpart.
        if prefix1.startswith(os.path.sep + "Library"):
            prefix2 = os.path.join("", "System", prefix1)
        elif prefix1.startswith(os.path.sep + "System"):
            prefix2 = prefix1[len(os.path.join("", "System")):]
        yield os.path.join(prefix1, "h2o_jar", "h2o.jar")
        yield os.path.join("", "usr", "local", "h2o_jar", "h2o.jar")
        yield os.path.join(prefix1, "local", "h2o_jar", "h2o.jar")
        yield os.path.join(get_config_var("userbase"), "h2o_jar", "h2o.jar")
        yield os.path.join(prefix2, "h2o_jar", "h2o.jar")


    def _launch_server(self, port, baseport, mmax, mmin, ea, nthreads):
        """Actually start the h2o.jar executable (helper method for `.start()`)."""
        self._ip = "127.0.0.1"

        # Find Java and check version. (Note that subprocess.check_output returns the output as a bytes object)
        java = self._find_java()
        jver_bytes = subprocess.check_output([java, "-version"], stderr=subprocess.STDOUT)
        jver = jver_bytes.decode(encoding="utf-8", errors="ignore")
        if self._verbose:
            print("  Java Version: " + jver.strip().replace("\n", "; "))
        if "GNU libgcj" in jver:
            raise H2OStartupError("Sorry, GNU Java is not supported for H2O.\n"
                                  "Please download the latest 64-bit Java SE JDK from Oracle.")
        if "Client VM" in jver:
            warn("  You have a 32-bit version of Java. H2O works best with 64-bit Java.\n"
                 "  Please download the latest 64-bit Java SE JDK from Oracle.\n")

        if self._verbose:
            print("  Starting server from " + self._jar_path)
            print("  Ice root: " + self._ice_root)

        # Construct java command to launch the process
        cmd = [java]

        # ...add JVM options
        cmd += ["-ea"] if ea else []
        for (mq, num) in [("-Xms", mmin), ("-Xmx", mmax)]:
            if num is None: continue
            numstr = "%dG" % (num >> 30) if num == (num >> 30) << 30 else \
                     "%dM" % (num >> 20) if num == (num >> 20) << 20 else \
                     str(num)
            cmd += [mq + numstr]
        cmd += ["-verbose:gc", "-XX:+PrintGCDetails", "-XX:+PrintGCTimeStamps"]
        cmd += ["-jar", self._jar_path]  # This should be the last JVM option

        # ...add H2O options
        cmd += ["-ip", self._ip]
        cmd += ["-port", str(port)] if port else []
        cmd += ["-baseport", str(baseport)] if baseport else []
        cmd += ["-ice_root", self._ice_root]
        cmd += ["-nthreads", str(nthreads)] if nthreads > 0 else []
        cmd += ["-name", "H2O_from_python_%s" % self._tmp_file("salt")]
        # Warning: do not change to any higher log-level, otherwise we won't be able to know which port the
        # server is listening to.
        cmd += ["-log_level", "INFO"]

        # Create stdout and stderr files
        self._stdout = self._tmp_file("stdout")
        self._stderr = self._tmp_file("stderr")
        cwd = os.path.abspath(os.getcwd())
        out = open(self._stdout, "w")
        err = open(self._stderr, "w")
        if self._verbose:
            print("  JVM stdout: " + out.name)
            print("  JVM stderr: " + err.name)

        # Launch the process
        win32 = sys.platform == "win32"
        flags = getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0) if win32 else 0
        prex = os.setsid if not win32 else None
        try:
            proc = subprocess.Popen(args=cmd, stdout=out, stderr=err, cwd=cwd, creationflags=flags, preexec_fn=prex)
        except OSError as e:
            traceback = getattr(e, "child_traceback", None)
            raise H2OServerError("Unable to start server: %s" % e, traceback)

        # Wait until the server is up-and-running
        giveup_time = time.time() + self._TIME_TO_START
        while True:
            if proc.poll() is not None:
                raise H2OServerError("Server process terminated with error code %d" % proc.returncode)
            ret = self._get_server_info_from_logs()
            if ret:
                self._scheme = ret[0]
                self._ip = ret[1]
                self._port = ret[2]
                self._process = proc
                break
            if time.time() > giveup_time:
                elapsed_time = time.time() - (giveup_time - self._TIME_TO_START)
                raise H2OServerError("Server wasn't able to start in %f seconds." % elapsed_time)
            time.sleep(0.2)


    @staticmethod
    def _find_java():
        """
        Find location of the java executable (helper for `._launch_server()`).

        This method is not particularly robust, and may require additional tweaking for different platforms...
        :return: Path to the java executable.
        :raises H2OStartupError if java cannot be found.
        """
        # is java in PATH?
        if os.access("java", os.X_OK):
            return "java"
        for path in os.getenv("PATH").split(os.pathsep):  # not same as os.path.sep!
            full_path = os.path.join(path, "java")
            if os.access(full_path, os.X_OK):
                return full_path

        # check if JAVA_HOME is set (for Windows)
        if os.getenv("JAVA_HOME"):
            return os.path.join(os.getenv("JAVA_HOME"), "bin", "java.exe")

        # check "/Program Files" and "/Program Files (x86)" on Windows
        if sys.platform == "win32":
            program_folders = [os.path.join("C:", "Program Files", "Java"),
                               os.path.join("C:", "Program Files (x86)", "Java")]
            # Look for JDK
            for folder in program_folders:
                for jdk in os.listdir(folder):
                    if "jdk" not in jdk.lower(): continue
                    path = os.path.join(folder, jdk, "bin", "java.exe")
                    if os.path.exists(path):
                        return path
            # check for JRE and warn
            for folder in program_folders:
                path = os.path.join(folder, "jre7", "bin", "java.exe")
                if os.path.exists(path):
                    warn("Found JRE at " + path + "; but H2O requires the JDK to run.")
        # not found...
        raise H2OStartupError("Cannot find Java. Please install the latest JDK from\n"
                              "http://www.oracle.com/technetwork/java/javase/downloads/index.html")


    def _tmp_file(self, kind):
        """
        Generate names for temporary files (helper method for `._launch_server()`).

        :param kind: one of "stdout", "stderr" or "salt". The "salt" kind is used for process name, not for a
            file, so it doesn't contain a path. All generated names are based on the user name of the currently
            logged-in user.
        """
        if sys.platform == "win32":
            username = os.getenv("USERNAME")
        else:
            username = os.getenv("USER")
        if not username:
            username = "unknownUser"
        usr = "".join(ch if ch.isalnum() else "_" for ch in username)

        if kind == "salt":
            return usr + "_" + "".join(choice("0123456789abcdefghijklmnopqrstuvwxyz") for _ in range(6))
        else:
            if not self._tempdir:
                self._tempdir = tempfile.mkdtemp()
            return os.path.join(self._tempdir, "h2o_%s_started_from_python.%s" % (usr, kind[3:]))


    def _get_server_info_from_logs(self):
        """
        Check server's output log, and determine its scheme / IP / port (helper method for `._launch_server()`).

        This method is polled during process startup. It looks at the server output log and checks for a presence of
        a particular string ("INFO: Open H2O Flow in your web browser:") which indicates that the server is
        up-and-running. If the method detects this string, it extracts the server's scheme, ip and port and returns
        them; otherwise it returns None.

        :returns: (scheme, ip, port) tuple if the server has already started, None otherwise.
        """
        searchstr = "INFO: Open H2O Flow in your web browser:"
        with open(self._stdout, "rt") as f:
            for line in f:
                if searchstr in line:
                    url = line[line.index(searchstr) + len(searchstr):].strip().rstrip("/")
                    parts = url.split(":")
                    assert len(parts) == 3 and (parts[0] == "http" or parts[1] == "https") and parts[2].isdigit(), \
                        "Unexpected URL: %s" % url
                    return parts[0], parts[1][2:], int(parts[2])
        return None


    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.shutdown()
        assert len(args) == 3  # Avoid warning about unused args...
        return False  # ensure that any exception will be re-raised

    # Do not stop child process when the object is garbage collected!
    # This ensures that simple code such as
    #     for _ in range(5):
    #         h2o.H2OConnection.start()
    # will launch 5 servers, and they will not be closed down immediately (only when the program exits).



#-----------------------------------------------------------------------------------------------------------------------
#   Exceptions
#-----------------------------------------------------------------------------------------------------------------------

class H2OStartupError(Exception):
    """Raised by H2OLocalServer when the class fails to launch a server."""


class H2OConnectionError(Exception):
    """
    Raised when connection to an H₂O server cannot be established.

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
        if schema == "CloudV3": return CloudV3(keyvals)
        if schema == "H2OErrorV3": return H2OErrorV3(keyvals)
        if schema == "H2OModelBuilderErrorV3": return H2OModelBuilderErrorV3(keyvals)
        if schema == "TwoDimTableV3": return H2OTwoDimTable.make(keyvals)
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
