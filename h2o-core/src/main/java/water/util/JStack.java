package water.util;

import water.Iced;
import water.H2O;

public class JStack extends Iced {
  public JStackCollectorTask.DStackTrace _traces[];

  public JStack execImpl() {
    _traces = new JStackCollectorTask().doAllNodes()._traces;
    return this;                // flow coding
  }

  public boolean toHTML( StringBuilder sb ) {
    // build tab list
    sb.append("<div class='tabbable tabs-left'>\n");
    sb.append(" <ul class='nav nav-tabs' id='nodesTab'>\n");
    for( int i = 0; i < _traces.length; ++i ) {
      sb.append("<li class='").append(i == 0 ? "active" : "").append("'>\n");
      sb.append("<a href='#tab").append(i).append("' data-toggle='tab'>");
      sb.append(H2O.CLOUD._memary[i].toString()).append("</a>\n");
      sb.append("</li>");
    }
    sb.append("</ul>\n");

    // build the tab contents
    sb.append(" <div class='tab-content' id='nodesTabContent'>\n");
    for( int i = 0; i < _traces.length; ++i ) {
      sb.append("<div class='tab-pane").append(i == 0 ? " active": "").append("' ");
      sb.append("id='tab").append(i).append("'>\n");
      sb.append("<pre>").append(_traces[i]).append("</pre>");
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
