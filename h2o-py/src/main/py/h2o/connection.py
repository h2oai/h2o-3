"""
An H2OConnection represents the latest active handle to a cloud. No more than a single
H2OConnection object will be active at any one time.
"""

import abc
import requests
import time
from two_dim_table import H2OTwoDimTable

__H2OCONN__ = None                   # the single active connection to H2O cloud
__H2O_REST_API_VERSION__ = "LATEST"  # const for the version of the rest api


class H2OConnectionException(Exception):
    pass


# Python has no private classes; abuse the abc package to fake it.
class H2OConnectionBase(object):
    """
    H2OConnection is a class that represents a connection to the H2O cluster.
    It is specified by an IP address and a port number.

    Objects of type H2OConnection are not instantiated directly!

    This class contains static methods for performing the common REST methods
    GET, POST, and DELETE.
    """
    __metaclass__ = abc.ABCMeta

    def __init__(self, ip="localhost", port=54321):
        """
        Wipes out the current __H2OCONN__, expects __make__ to be called in conjunction.
        :return: None
        """
        global __H2OCONN__
        self._ip = None
        self._port = None
        self._session_id = None
        self._rest_version = __H2O_REST_API_VERSION__
        __H2OCONN__ = self
        H2OConnectionBase.__make__(ip, port)

    @staticmethod
    def __make__(ip="localhost", port=54321):
        """
        Instantiate the package handle to the H2O cluster.
        :param ip: An IP address, default is "localhost"
        :param port: A port, default is 54321
        :return: None
        """
        global __H2OCONN__
        assert isinstance(port, int) and 0 <= port <= 65535
        __H2OCONN__._ip = ip
        __H2OCONN__._port = port

        cld = H2OConnectionBase.connect()
        ncpus = sum([n['num_cpus'] for n in cld['nodes']])
        mmax = sum([n['max_mem'] for n in cld['nodes']])

        print "Connected to cloud '" + cld['cloud_name'] + "' size", \
            cld['cloud_size'], "ncpus", ncpus, "maxmem", \
            H2OConnectionBase._get_human_readable_size(mmax)

        __H2OCONN__._session_id = H2OConnectionBase.get_session_id()
        return None

    @staticmethod
    def get_session_id():
        return H2OConnectionBase.do_safe_get_json(url_suffix="InitID")["session_key"]

    @staticmethod
    def connect(size=1):
        """
        Does not actually "connect", instead simply tests that the cluster can be reached,
        is of a certain size, and is taking basic status commands.
        :param size: The number of H2O instances in the cloud.
        :return: The JSON response from a "stable" cluster.
        """
        max_retries = 30
        retries = 0
        while True:
            retries += 1
            cld = H2OConnectionBase.do_safe_get_json(url_suffix="Cloud")
            if not cld['cloud_healthy']:
                raise ValueError("Cluster reports unhealthy status", cld)
            if cld['cloud_size'] >= size and cld['consensus']:
                return cld
            # Cloud too small or voting in progress; sleep; try again
            time.sleep(0.1)
            if retries > max_retries:
                raise H2OConnectionException("Max retries exceeded. Could not establish "
                                             "link to the H2O cloud @ "
                                             + H2OConnectionBase.ip() + ":"
                                             + str(H2OConnectionBase.port()))

    @staticmethod
    def get_attr(name):
        """
        A helper function for static methods
            rest_version, session_id, port, ip
        :param name: The attribute to lookup in __H2OCONN__
        :return: The desired attribute
        """
        global __H2OCONN__
        H2OConnectionBase.check_conn()
        return getattr(__H2OCONN__, name)

    @staticmethod
    def rest_version(): return H2OConnection.get_attr("_rest_version")

    @staticmethod
    def session_id(): return H2OConnection.get_attr("_session_id")

    @staticmethod
    def port(): return H2OConnection.get_attr("_port")

    @staticmethod
    def ip(): return H2OConnection.get_attr("_ip")

    @staticmethod
    def current_connection():
        global __H2OCONN__
        return __H2OCONN__

    @staticmethod
    def _get_human_readable_size(num):
        exp_str = [(0, 'B'), (10, 'KB'), (20, 'MB'), (30, 'GB'), (40, 'TB'), (50, 'PB'), ]
        i = 0
        rounded_val = 0
        while i + 1 < len(exp_str) and num >= (2 ** exp_str[i + 1][0]):
            i += 1
            rounded_val = round(float(num) / 2 ** exp_str[i][0], 2)
        return '%s %s' % (rounded_val, exp_str[i][1])

    @staticmethod
    def check_conn():
        global __H2OCONN__
        if not __H2OCONN__:
            raise EnvironmentError("No active connection to an H2O cluster. " +
                                   "Try calling `h2o.init()`")
        return __H2OCONN__

    """
    Below is the REST implementation layer:
        _attempt_rest -- GET, POST, DELETE

        do_raw_rest
        do_raw_get
        do_raw_post

        do_rest
        do_get
        do_post

        do_safe_rest
        do_safe_get
        do_safe_post

    All methods are static and rely on an active __H2OCONN__ object.
    """

    @staticmethod
    def _do_raw_rest(url_suffix=None, params=None,
                     method=None, file_upload_info=None, **kwargs):
        H2OConnection.check_conn()  # stops if no connection
        if not params:
            params = {}

        url = H2OConnection._calc_base_url(url_suffix)
        parts = {}  # for building the query_string below
        for k in params:
            if isinstance(params[k], list):
                parts[k] = '['
                parts[k] += ','.join([str(l).encode("utf-8") for l in params[k]])
                parts[k] += ']'
            else:
                parts[k] = str(params[k]).encode("utf-8")

        query_string = '&'.join(['%s=%s' % (k, v) for (k, v) in parts.items()])
        post_body = ""

        if not file_upload_info:
            #  this is the typical case
            if method == "POST":
                post_body = query_string
            elif query_string != '':
                url = "{}?{}".format(url, query_string)
        else:
            if not method == "POST":
                raise ValueError("Received file upload info "
                                 "and expected method to be POST. Got: " + method)
            if query_string != '':
                url = "{}?{}".format(url, query_string)

        begin_time_seconds = time.time()
        http_result = H2OConnectionBase._attempt_rest(url=url,
                                                      method=method,
                                                      post_body=post_body,
                                                      file_upload_info=file_upload_info,
                                                      params=params,
                                                      **kwargs)
        end_time_seconds = time.time()
        elapsed_time_seconds = end_time_seconds - begin_time_seconds
        elapsed_time_millis = elapsed_time_seconds * 1000

        # TODO: is.logging? -> write to logs
        # print "Time to perform REST call (millis): " + str(elapsed_time_millis)

        return http_result

    @staticmethod
    def _do_raw_get(url_suffix=None, params=None, **kwargs):
        return H2OConnectionBase._do_raw_rest(url_suffix=url_suffix,
                                              params=params,
                                              method="GET",
                                              **kwargs)

    @staticmethod
    def _do_raw_post(url_suffix=None, params=None, file_upload_info=None, **kwargs):
        return H2OConnectionBase._do_raw_rest(url_suffix=url_suffix,
                                              params=params,
                                              method="POST",
                                              file_upload_info=file_upload_info,
                                              **kwargs)

    @staticmethod
    def _do_rest(url_suffix=None, params=None, method=None,
                 file_upload_info=None, **kwargs):
        return H2OConnectionBase._do_raw_rest(url_suffix=url_suffix,
                                              params=params,
                                              method=method,
                                              file_upload_info=file_upload_info,
                                              **kwargs)

    @staticmethod
    def _do_get(url_suffix=None, params=None, **kwargs):
        return H2OConnectionBase._do_rest(url_suffix=url_suffix,
                                          params=params,
                                          method="GET",
                                          **kwargs)

    @staticmethod
    def _do_post(url_suffix=None, params=None, **kwargs):
        return H2OConnectionBase._do_rest(url_suffix=url_suffix,
                                          params=params,
                                          method="POST",
                                          **kwargs)

    @staticmethod
    def do_safe_rest(url_suffix=None, params=None, method=None,
                     file_upload_info=None, **kwargs):
        res = H2OConnectionBase._do_rest(url_suffix=url_suffix,
                                         params=params,
                                         method=method,
                                         file_upload_info=file_upload_info,
                                         **kwargs)

        if res["http_error"]:
            raise EnvironmentError("h2o-py encountered an unexpected HTTP error:\n {}"
                                   .format(res["http_error_message"]))
        elif res["http_status_code"] != 200:
            raise EnvironmentError("h2o-py got an unexpected HTTP status code:\n"
                                   " {} {} (url = {})"
                                   .format(res["http_status_code"],
                                           res["http_status_message"],
                                           res["url"]))
        return res["http_payload"]

    @staticmethod
    def do_safe_get(url_suffix=None, params=None, **kwargs):
        return H2OConnectionBase.do_safe_rest(url_suffix=url_suffix,
                                              params=params,
                                              method="GET",
                                              **kwargs)

    @staticmethod
    def do_safe_post(url_suffix=None, params=None, file_upload_info=None, **kwargs):
        return H2OConnectionBase.do_safe_rest(url_suffix=url_suffix,
                                              params=params,
                                              method="POST",
                                              file_upload_info=file_upload_info,
                                              **kwargs)

    @staticmethod
    def do_safe_rest_json(url_suffix=None, params=None, method=None,
                          file_upload_info=None, **kwargs):
        raw_txt = H2OConnectionBase.do_safe_rest(url_suffix=url_suffix,
                                                 params=params,
                                                 method=method,
                                                 file_upload_info=file_upload_info,
                                                 **kwargs)
        return H2OConnectionBase._from_json(raw_txt, **kwargs)

    @staticmethod
    def do_safe_get_json(url_suffix=None, params=None, **kwargs):
        return H2OConnectionBase.do_safe_rest_json(url_suffix=url_suffix,
                                                   params=params,
                                                   method="GET",
                                                   **kwargs)

    @staticmethod
    def do_safe_post_json(url_suffix=None, params=None, file_upload_info=None, **kwargs):
        return H2OConnectionBase.do_safe_rest_json(url_suffix=url_suffix,
                                                   params=params,
                                                   file_upload_info=file_upload_info,
                                                   method="POST",
                                                   **kwargs)

    @staticmethod
    def _attempt_rest(url="", method="GET", post_body=None, file_upload_info=None,
                      params=None, **kwargs):

        # TODO: post_body not used

        http_result = {"url": url,
                       "post_body": post_body,
                       "http_error": False,
                       "http_error_message": "",
                       "http_status_code": -1,
                       "http_status_message": "",
                       "http_payload": None,
                       }

        if method == "GET":
            try:
                http_result["http_payload"] = requests.get(url, **kwargs)
            except requests.ConnectionError as e:
                http_result["http_error"] = True
                http_result["http_error_message"] = e.message
            except requests.HTTPError as e2:
                http_result["http_error"] = True
                http_result["http_error_message"] = e2.message

        elif file_upload_info:
            if not method == "POST":
                raise ValueError("Recieved file upload info "
                                 "and expected method to be POST. Got: " + method)
            try:
                files = {file_upload_info["file"] : open(file_upload_info["file"], "rb")}
                http_result["http_payload"] = requests.post(url, files=files, **kwargs)
            except requests.ConnectionError as e:
                http_result["http_error"] = True
                http_result["http_error_message"] = e.message
            except requests.HTTPError as e2:
                http_result["http_error"] = True
                http_result["http_error_message"] = e2.message

        elif method == "POST":
            try:
                http_result["http_payload"] = requests.post(url, params=post_body, **kwargs)
            except requests.ConnectionError as e:
                http_result["http_error"] = True
                http_result["http_error_message"] = e.message
            except requests.HTTPError as e2:
                http_result["http_error"] = True
                http_result["http_error_message"] = e2.message

        elif method == "DELETE":
            try:
                http_result["http_payload"] = requests.delete(url, **kwargs)
            except requests.ConnectionError as e:
                http_result["http_error"] = True
                http_result["http_error_message"] = e.message
            except requests.HTTPError as e2:
                http_result["http_error"] = True
                http_result["http_error_message"] = e2.message
        else:
            raise ValueError("Unkown HTTP method " + method)

        if not http_result["http_error"]:
            http_result["http_status_code"] = http_result["http_payload"].status_code
            http_result["http_status_message"] = http_result["http_payload"].reason

        return http_result

    @staticmethod
    def _from_json(txt=None, **kwargs):
        # res = H2OConnectionBase._process_matrices(txt.json(**kwargs))
        return H2OConnectionBase._process_tables(txt.json(**kwargs))

    # TODO:
    # @staticmethod
    # def _process_matrices(x=None):
    #     if x:
    #         if isinstance(x, "dict"):
    #
    #     return x

    @staticmethod
    def _process_tables(x=None):
        elts = ["tableHeader", "rowHeaders", "colHeaders",
                "colTypes", "colFormats", "cellValues"]

        if x:
            if isinstance(x, dict):
                have_table = all([True if i in elts else False for i in x.keys()])
                have_table &= len(x) == len(elts)
                if have_table:
                    tbl = x["cellValues"]
                    tbl = H2OTwoDimTable(x["rowHeaders"], x["colHeaders"], x["colTypes"],
                                         x["tableHeader"], x["cellValues"],
                                         x["colFormats"])
                    x = tbl
                else:
                    for k in x:
                        x[k] = H2OConnectionBase._process_tables(x[k])
            if isinstance(x, list):
                for it in range(len(x)):
                    x[it] = H2OConnectionBase._process_tables(x[it])
        return x

    @staticmethod
    def _calc_base_url(url_suffix=None):
        url_base = "http://{}:{}/{}/{}"
        if not url_suffix:
            raise ValueError("No url suffix supplied.")
        url_suffix += ".json"
        return url_base.format(H2OConnection.ip(),
                               H2OConnection.port(),
                               H2OConnection.rest_version(),
                               url_suffix)


class H2OConnection(H2OConnectionBase):

    def __init__(self, ip="localhost", port=54321):
        """
        Instantiate a connection to the H2O cluster. The connection object is stashed
        as a global in __H2OCONN__.
        :param ip: An IP address, default is "localhost"
        :param port: A port, default is 54321
        :return: None
        """
        super(H2OConnection, self).__init__(ip, port)