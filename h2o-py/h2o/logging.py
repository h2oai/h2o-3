# Functions for python logging

import tempfile
import os
import platform

__LOG_FILE_NAME__ = None
__IS_LOGGING__ = False

def _calc_log_file_name(): return tempfile.mkdtemp()+"/rest.log"

def _get_log_file_name(): return __LOG_FILE_NAME__ if __LOG_FILE_NAME__ != None else _calc_log_file_name()

def _is_logging(): return __IS_LOGGING__

def _log_rest(message):
    if _is_logging():
        name = _get_log_file_name()
        with open(name, "a") as f:
            f.write(message)
            f.close()

def start_logging(full_logfile_path=None):
    """
    Start Writing H2O R Logs
    Begin logging H2o R POST commands and error responses to local disk. Used primarily for debugging purposes.

    :param full_logfile_path: a character string name for the file, automatically generated
    :return: The path of the log file
    """
    if full_logfile_path == None: full_logfile_path = _calc_log_file_name()
    if not isinstance(full_logfile_path, str): raise ValueError("`full_logfile_path` argument must be a string by got "
                                                                "{0}: ".format(type(full_logfile_path)))
    global __IS_LOGGING__
    global __LOG_FILE_NAME__
    __LOG_FILE_NAME__ = full_logfile_path
    __IS_LOGGING__ = True
    print "Appending REST API transactions to log file " + full_logfile_path + "\n"
    return full_logfile_path

def stop_logging():
    """
    Stop Writing H2O R Logs
    Halt logging of H2O R POST commands and error responses to local disk. Used primarily for debugging purposes.

    :return: None
    """
    __IS_LOGGING__ = False
    print "Logging stopped\n"

def clear_log():
    """
    Delete All H2O R Logs
    Clear all H2O R command and error response logs from the local disk. Used primarily for debugging purposes.

    :return: None
    """
    os.remove(_get_log_file_name())
    print "Removed file " + _get_log_file_name() + "\n"

def open_log():
    """
    View H2O R Logs
    Open existing logs of H2O R POST commands and error resposnes on local disk. Used primarily for debugging purposes.

    :return: None
    """
    log_file_name = _get_log_file_name()
    if not os.path.isfile(log_file_name): raise RuntimeError(log_file_name + " does not exist")
    if platform.system() == "Windows": os.system("start " + log_file_name)
    else: os.system("open " + log_file_name)