package water.api;

import water.H2O;
import water.api.ProfilerHandler.Profiler;
import water.util.DocGen;

abstract class ProfilerBase<I extends Profiler, S extends Schema<I, S>> extends Schema<I, S> {
  // Input
  @API(help="Stack trace depth", required=true)
  public int depth = 5;

  // Output
  @API(help="Array of Profiles, one per Node in the Cluster", direction=API.Direction.OUTPUT)
  public String[][] stacktraces;
  @API(help="Array of Profile Counts, one per Node in the Cluster", direction=API.Direction.OUTPUT)
  public int[][] counts;

  @Override public I fillImpl(I profiler) {
    if (depth < 1) throw new IllegalArgumentException("depth must be >= 1.");

    super.fillImpl(profiler);
    return profiler;
  }

  @Override public DocGen.HTML writeHTML_impl( DocGen.HTML ab ) {
    StringBuilder sb = new StringBuilder();
    // build tab list
    sb.append("<div class='tabbable tabs-left'>\n");
    sb.append(" <ul class='nav nav-tabs' id='nodesTab'>\n");
    for( int i = 0; i < stacktraces.length; ++i ) {
      sb.append("<li class='").append(i == 0 ? "active" : "").append("'>\n");
      sb.append("<a href='#tab").append(i).append("' data-toggle='tab'>");
      sb.append(H2O.CLOUD._memary[i].toString()).append("</a>\n");
      sb.append("</li>");
    }
    sb.append("</ul>\n");

    // build the tab contents
    sb.append(" <div class='tab-content' id='nodesTabContent'>\n");
    for( int i = 0; i < stacktraces.length; ++i ) {
      sb.append("<div class='tab-pane").append(i == 0 ? " active": "").append("' ");
      sb.append("id='tab").append(i).append("'>\n");
      for (int j=0; j<stacktraces[i].length; ++j) {
        sb.append("<pre>").append(counts[i][j]).append("\n").append(stacktraces[i][j]).append("</pre>");
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
    ab.p(sb.toString());
    return ab;
  }
}
