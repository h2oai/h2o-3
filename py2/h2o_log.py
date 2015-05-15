
import re
import h2o_util
import h2o_nodes
from h2o_test import check_sandbox_for_errors, get_sandbox_name

def checkH2OLogs(timeoutSecs=3, expectedMinLines=12, suffix="-1-trace"):
    # download logs from node 0 (this will overwrite)
    h2o_nodes.nodes[0].log_download(timeoutSecs=timeoutSecs)

    # I guess we really don't need to get the list of nodes names from get_cloud any more
    # h2o_172.16.2.222_54321-1-trace.log
    # h2o_172.16.2.222_54321-2-debug.log
    # h2o_172.16.2.222_54321-3-info.log
    # h2o_172.16.2.222_54321-4-warn.log
    # h2o_172.16.2.222_54321-5-error.log
    # h2o_172.16.2.222_54321-6-fatal.log
    def checkit(suffix, expectedMinLines):
        logNameList = ["h2o_" + str(n.http_addr) + "_" + str(n.port) + suffix + ".log" for n in h2o_nodes.nodes]
        lineCountList = []
        for logName in logNameList:
            lineCount = h2o_util.file_line_count(get_sandbox_name() + "/" + logName)
            print logName, "lineCount:", lineCount
            lineCountList.append(lineCount)

        print logNameList

        if len(h2o_nodes.nodes) != len(logNameList):
            raise Exception("Should be %d logs, are %d" % len(h2o_nodes.nodes), len(logNameList))
        # line counts seem to vary..check for "too small"
        # variance in polling (cloud building and status)?
        for i, l in enumerate(lineCountList):
            if l < expectedMinLines:
                raise Exception("node %d %s log is too small" % (i, logNameList[i]))
        return (logNameList, lineCountList)

    # just asssume the main ones meet the min requirement..and the error ones are min 0
    (logNameList, lineCountList) = checkit("-1-trace", expectedMinLines)
    checkit("-2-debug", expectedMinLines)
    checkit("-3-info", expectedMinLines)
    checkit("-4-warn", 0)
    checkit("-5-error", 0)
    checkit("-6-fatal", 0)
    # now that all the logs are there
    check_sandbox_for_errors()
    return (logNameList, lineCountList)
