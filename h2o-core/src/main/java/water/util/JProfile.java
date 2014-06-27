package water.util;

import water.H2O;
import water.Iced;

import java.text.DateFormat;
import java.util.Date;

public class JProfile extends Iced {
  public final String node_name;
  public final String time;
  public final int depth;
  public JProfile(int d) {
    depth = d;
    node_name = H2O.SELF.toString();
    time = DateFormat.getInstance().format(new Date());
  }
  public static class ProfileSummary extends Iced {
    public ProfileSummary( String name, ProfileCollectorTask.NodeProfile profile) { this.name=name; this.profile=profile; }
    public final String name;
    public final ProfileCollectorTask.NodeProfile profile;
  }
  public ProfileSummary nodes[];

  public JProfile execImpl() {
    ProfileCollectorTask.NodeProfile profiles[] = new ProfileCollectorTask(depth).doAllNodes()._result;
    nodes = new ProfileSummary[H2O.CLOUD.size()];
    for( int i=0; i<nodes.length; i++ ) {
      assert(profiles[i] != null);
      nodes[i] = new ProfileSummary(H2O.CLOUD._memary[i].toString(), profiles[i]);
    }
    for (ProfileSummary node : nodes) Log.debug(node.name, node.profile);
    return this;
  }

  public boolean toHTML( StringBuilder sb ) {
    // build tab list
    sb.append("<div class='tabbable tabs-left'>\n");
    sb.append(" <ul class='nav nav-tabs' id='nodesTab'>\n");
    for( int i = 0; i < nodes.length; ++i ) {
      sb.append("<li class='").append(i == 0 ? "active" : "").append("'>\n");
      sb.append("<a href='#tab").append(i).append("' data-toggle='tab'>");
      sb.append(nodes[i].name).append("</a>\n");
      sb.append("</li>");
    }
    sb.append("</ul>\n");

    // build the tab contents
    sb.append(" <div class='tab-content' id='nodesTabContent'>\n");
    for( int i = 0; i < nodes.length; ++i ) {
      sb.append("<div class='tab-pane").append(i == 0 ? " active": "").append("' ");
      sb.append("id='tab").append(i).append("'>\n");
      for (int j=0; j<nodes[i].profile.counts.length; ++j) {
        sb.append("<pre>").append(nodes[i].profile.counts[j]).append("\n").append(nodes[i].profile.stacktraces[j]).append("</pre>");
      }
      sb.append("</div>");
    }
    sb.append("  </div>");
    sb.append("</div>");

    sb.append("<script type='text/javascript'>" +
            "$(document).ready(function() {" +
            "  $('#nodesTab a').click(function(e) {" +
            "    e.preventDefault(); $(this).tab('show');" +
            "  });" +
            "});" +
            "</script>");
    return true;
  }
}