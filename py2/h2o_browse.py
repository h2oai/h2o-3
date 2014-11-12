import h2o_nodes
import webbrowser, re, getpass, urllib, time

import h2o_args
from h2o_test import verboseprint, log
# just some things useful for debugging or testing. pops the brower and let's you look at things
# like the confusion matrix by matching the RFView json (h2o keeps the json history for us)

# always nice to have a browser up on the cloud while running at test. You can see the fork/join task state
# and browse to network stats, or look at the time line. Starting from the cloud page is sufficient.
def browseTheCloud():
    # disable browser stuff for jenkins
    if not h2o_args.browse_disable:
        # after cloud building, node[0] should have the right info for us
        port = h2o_nodes.nodes[0].port
        cloud_url = "http://" + h2o_nodes.nodes[0].http_addr + ":" + str(port) + "/steam/index.html"

        # Open URL in new window, raising the window if possible.
        print "\nbrowseTheCloud using this url:", cloud_url
        webbrowser.open_new(cloud_url)

# match the first, swap the 2nd
def browseJsonHistoryAsUrlLastMatch(matchme, swapme=None):
    if not h2o_args.browse_disable:
        # get rid of the ".json" from the last url used by the test framework.
        # if we hit len(), we point to 0, so stop
        len_history= len(h2o_nodes.json_url_history)
        i = -1
        while (len_history+i!=0 and not re.search(matchme, h2o_nodes.json_url_history[i]) ):
            i = i - 1
        url = h2o_nodes.json_url_history[i]

        # chop out the .json to get a browser-able url (can look at json too)
        # Open URL in new window, raising the window if possible.
        # webbrowser.open_new_tab(json_url)
        # UPDATE: with the new API port, the browser stuff has .html
        # but we've not switched everything to new. So do it selectively

        if swapme is not None: url = re.sub(matchme, swapme, url)
        url = re.sub("ProgressPage","ProgressPage.html",url)
        url = re.sub("Progress?!Page","Progress.html",url)
        url = re.sub(".json",".html",url)

        verboseprint("browseJsonHistoryAsUrlLastMatch:", url)
        print "browseJsonHistoryAsUrlLastMatch,  decoded:", urllib.unquote(url)
        webbrowser.open_new_tab(url)

# maybe not useful, but something to play with.
# go from end, backwards and see what breaks! (in json to html hack url transform)
# note that put/upload  and rf/rfview methods are different for html vs json
def browseJsonHistoryAsUrl(retryDelaySecs=0.25):
    if not h2o_args.browse_disable:
        # stop if you get to -50, don't want more than 50 tabs on browser
        tabCount = 0
        for url in h2o_nodes.json_url_history:
            # ignore the Cloud "alive" views
            # FIX! we probably want to expand ignoring to more than Cloud?
            if not re.search('Cloud', url):
                # url = re.sub("GLMGridProgress","GLMGridProgress.html",url)
                # url = re.sub("Progress","Progress.html",url)
                url = re.sub("ProgressPage","ProgressPage.html",url)
                url = re.sub("Progress?!Page","Progress.html",url)
                url = re.sub("Progress\?","Progress.html?",url)
                url = re.sub(".json",".html",url)
                print "browseJsonHistoryAsUrl:", url
                print "same, decoded:", urllib.unquote(url)
                # does this open in same window?
                log(url, comment="From browseJsonHistoryAsUrl")
                webbrowser.open(url, new=0)
                time.sleep(retryDelaySecs)
                tabCount += 1

            if tabCount==50: 
                break;
