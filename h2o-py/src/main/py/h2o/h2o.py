"""
This module implements the communication REST layer for the python <-> H2O connection.
"""

import os
import re
import urllib
from connection import H2OConnection as h2oConn
from job import H2OJob
from frame import H2OFrame


def import_file(path):
    """
    Import a single file.
    :param path: A path to a data file (remote or local)
    :return: Return an H2OFrame.
    """
    j = h2oConn.do_safe_get_json(url_suffix="ImportFiles", params={'path': path})
    if j['fails']:
        raise ValueError("ImportFiles of " + path + " failed on " + j['fails'])
    return j['keys'][0]


def upload_file(path, destination_key=""):
    """
    Upload a dataset at the path given from the local machine to the H2O cluster.
    :param path:    A path specifying the location of the data to upload.
    :param destination_key: The name of the H2O Frame in the H2O Cluster.
    :return:    A new H2OFrame
    """
    fui = {"file": os.path.abspath(path)}
    dest_key = H2OFrame.py_tmp_key() if destination_key == "" else destination_key
    p = {'destination_key': dest_key}
    h2oConn.do_safe_post_json(url_suffix="PostFile", params=p, file_upload_info=fui)
    return H2OFrame(raw_fname=dest_key)


def import_frame(path=None, vecs=None):
    """
    Import a frame.
    :param path:
    :return:
    """
    return H2OFrame(vecs=vecs) if vecs else H2OFrame(remote_fname=path)


def parse_setup(rawkey):
    """
    Unable to use 'requests.params=' syntax because it flattens array parameters,
    but ParseSetup really expects a real array of Keys.
    :param rawkey:
    :return: A ParseSetup "object"
    """

    # So the st00pid H2O backend only accepts things that are quoted (nasty Java)
    raw_key = _quoted(rawkey)
    j = h2oConn.do_safe_post_json(url_suffix="ParseSetup", params={'srcs': [raw_key]})
    if not j['isValid']:
        raise ValueError("ParseSetup not Valid", j)
    return j


def parse(setup, h2o_name, first_line_is_header=(-1, 0, 1)):
    """
    Trigger a parse; blocking; removeFrame just keep the Vec keys.
    :param setup: The result of calling parse_setup
    :param h2o_name: The name of the H2O Frame on the back end.
    :param first_line_is_header: -1 means data, 0 means guess, 1 means header
    :return: Return a new parsed object
    """
    if isinstance(first_line_is_header, tuple):
        first_line_is_header = 0
    # Parse parameters (None values provided by setup)
    p = {'delete_on_done': True,
         'blocking': True,
         'removeFrame': True,
         'hex': h2o_name,
         'ncols': None,
         'sep': None,
         'pType': None,
         'singleQuotes': None,
         'checkHeader' : None,
         }
    if setup["columnNames"]:
        setup["columnNames"] = [_quoted(name) for name in setup["columnNames"]]
        p["columnNames"] = None

    # update the parse parameters with the parse_setup values
    p.update({k: v for k, v in setup.iteritems() if k in p})

    p["checkHeader"] = first_line_is_header


    # Extract only 'name' from each src in the array of srcs
    p['srcs'] = [_quoted(src['name']) for src in setup['srcs']]

    # Request blocking parse
    j = H2OJob(h2oConn.do_safe_post_json(url_suffix="Parse", params=p)).poll()
    return j.jobs


def _quoted(key):
    is_quoted = len(re.findall(r'\"(.+?)\"', key)) != 0
    key = key if is_quoted  else "\"" + key + "\""
    return key


def remove(key):
    """
    Remove a key from H2O.
    :param key: The key pointing to the object to be removed.
    :return: void
    """
    h2oConn.do_safe_rest(url_suffix="Remove", params={"key": key}, method="DELETE")


def rapids(expr):
    """
    Fire off a Rapids expression
    :param expr: The rapids expression (ascii string)
    :return: The JSON response of the Rapids execution.
    """
    return h2oConn.do_safe_post_json(url_suffix="Rapids",
                                     params={"ast": urllib.quote(expr)})


def frame(key):
    """
    Retrieve metadata for a key that points to a Frame.
    :param key: A pointer to a Frame in H2O.
    :return: Meta information on the Frame.
    """

    return h2oConn.do_safe_get_json(url_suffix="Frames/" + key)


def init(ip="localhost", port=54321):
    """
    Initiate an H2O connection to the specified ip and port
    :param ip: An IP address, default is "localhost"
    :param port: A port, default is 54321
    :return: None
    """
    h2oConn(ip=ip, port=port)
    return None