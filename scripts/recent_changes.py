#!/usr/bin/python

import sys
import os
import requests
import re
import dateutil.parser


g_user = None
g_pass = None
g_start_date = None


def get_issue_key(issue):
    return issue[u'key']


def get_issue_type_name(issue):
    name = issue[u'fields'][u'issuetype'][u'name']
    return name


def get_issue_summary(issue):
    summary = issue[u'fields'][u'summary']
    summary = summary.encode('ascii', 'ignore')
    return summary


def get_issue_component_name(issue):
    components = issue[u'fields'][u'components']
    if (components is None):
        return "No component"
    if (len(components) == 0):
        return "No component"
    name = components[0][u'name']
    return name


def usage():
    print("")
    print("usage:  " + g_script_name + " --user username --pass password --startdate startdate")
    print("")
    print("e.g.")
    print("")
    print("./" + g_script_name + " --startdate 2015-01-30")
    print("")
    sys.exit(1)


def unknown_arg(s):
    print("")
    print("ERROR: Unknown argument: " + s)
    print("")
    usage()


def parse_config_file():
    global g_user
    global g_pass
    global g_sprint
    global g_start_date
    home = os.path.expanduser("~")
    config_file = os.path.join(home, ".h2o_jira")
    if (os.path.exists(config_file)):
        with open(config_file) as f:
            for line in f:
                match_groups = re.search(r"\s*(\S+)\s*=\s*([\S\s]+)\s*", line.rstrip())
                if (match_groups is not None):
                    key = match_groups.group(1)
                    value = match_groups.group(2)
                    if (key == "user"):
                        g_user = value
                    elif (key == "pass"):
                        g_pass = value
                    elif (key == "sprint"):
                        g_sprint = value
                    elif (key == "startdate"):
                        g_start_date = dateutil.parser.parse(value)


def parse_args(argv):
    global g_user
    global g_pass
    global g_start_date

    i = 1
    while (i < len(argv)):
        s = argv[i]

        if (s == "--user"):
            i += 1
            if (i > len(argv)):
                usage()
            g_user = argv[i]
        elif (s == "--pass"):
            i += 1
            if (i > len(argv)):
                usage()
            g_pass = argv[i]
        elif (s == "--startdate"):
            i += 1
            if (i > len(argv)):
                usage()
            g_start_date = dateutil.parser.parse(argv[i])
        elif (s == "-h" or s == "--h" or s == "-help" or s == "--help"):
            usage()
        else:
            unknown_arg(s)

        i += 1

    if (g_user is None):
        print "ERROR: user is not specified"
        usage()

    if (g_pass is None):
        print "ERROR: pass is not specified"
        usage()

    if (g_start_date is None):
        print "ERROR: start_date is not specified"
        usage()


def main(argv):
    """
    Main program.

    @return: none
    """
    global g_script_name

    g_script_name = os.path.basename(argv[0])
    parse_config_file()
    parse_args(argv)

    url = 'https://0xdata.atlassian.net/rest/api/2/search?jql=' \
          + 'project+in+(PUBDEV,HEXDEV)' \
          + '+and+' \
          + 'resolutiondate+>=+' + g_start_date.strftime("%Y-%m-%d") \
          + '+and+' \
          + 'resolution+in+(Done,Fixed)' \
          + 'order+by+type,component,resolutiondate' \
          + '&maxResults=1000'

    r = requests.get(url, auth=(g_user, g_pass))
    if (r.status_code != 200):
        print("ERROR: status code is " + str(r.status_code))
        sys.exit(1)
    j = r.json()
    issues = j[u'issues']
    if len(issues) >= 1000:
        print("ERROR: len(issues) >= 1000.  Too many issues.")
        sys.exit(1)
    last_issue_type_name = ""
    last_component_name = ""
    for issue in issues:
        issue_type_name = get_issue_type_name(issue)
        component_name = get_issue_component_name(issue)
        if (issue_type_name != last_issue_type_name):
            print("")
            print "# " + issue_type_name
            last_issue_type_name = issue_type_name
        if (component_name != last_component_name):
            print("")
            print "## " + component_name
            last_component_name = component_name
        key = get_issue_key(issue)
        summary = get_issue_summary(issue)
        print "* " + key + ": " + summary

if __name__ == "__main__":
    main(sys.argv)
