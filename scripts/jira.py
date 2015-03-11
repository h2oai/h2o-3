#!/usr/bin/python

import sys
import os
import requests
import urllib


g_user = None
g_pass = None
g_sprint = None


def usage():
    print("")
    print("usage:  " + g_script_name + " --user username --pass password --sprint sprintname")
    print("")
    sys.exit(1)


def unknown_arg(s):
    print("")
    print("ERROR: Unknown argument: " + s)
    print("")
    usage()


def parse_args(argv):
    global g_user
    global g_pass
    global g_sprint

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
        elif (s == "--sprint"):
            i += 1
            if (i > len(argv)):
                usage()
            g_sprint = argv[i]
        elif (s == "-h" or s == "--h" or s == "-help" or s == "--help"):
            usage()
        else:
            unknown_arg(s)

        i += 1

    if (g_user is None):
        usage()

    if (g_pass is None):
        usage()

    if (g_sprint is None):
        usage()


def main(argv):
    """
    Main program.

    @return: none
    """
    global g_script_name

    g_script_name = os.path.basename(argv[0])
    parse_args(argv)

    url = 'https://0xdata.atlassian.net/rest/api/2/search?jql=sprint="' + urllib.quote(g_sprint) + '"&maxResults=1000'
    r = requests.get(url, auth=(g_user, g_pass))
    if (r.status_code != 200):
        print("ERROR: status code is " + str(r.status_code))
        sys.exit(1)
    j = r.json()
    issues = j[u'issues']
    story_points_map = {}
    for issue in issues:
        name = issue[u'fields'][u'assignee'][u'name']
        story_points = issue[u'fields'][u'customfield_10004']
        if story_points is None:
            story_points = 0
        else:
            story_points = float(story_points)
        if name in story_points_map:
            n = story_points_map[name]
            story_points_map[name] = n + story_points
        else:
            story_points_map[name] = story_points

    for key in sorted(story_points_map.keys()):
        value = story_points_map[key]
        print("{}: {}").format(key, value)


if __name__ == "__main__":
    main(sys.argv)
