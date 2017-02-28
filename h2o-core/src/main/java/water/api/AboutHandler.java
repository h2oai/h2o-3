package water.api;

import water.H2O;
import water.H2OSecurityManager;
import water.api.schemas3.AboutEntryV3;
import water.api.schemas3.AboutV3;
import water.util.PrettyPrint;

import java.util.ArrayList;
import java.util.Date;

public class AboutHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public AboutV3 get(int version, AboutV3 s) {
    ArrayList<AboutEntryV3> entries = new ArrayList<>();
    entries.add(new AboutEntryV3("Build git branch",      H2O.ABV.branchName()));
    entries.add(new AboutEntryV3("Build git hash",        H2O.ABV.lastCommitHash()));
    entries.add(new AboutEntryV3("Build git describe",    H2O.ABV.describe()));
    entries.add(new AboutEntryV3("Build project version", H2O.ABV.projectVersion()));
    entries.add(new AboutEntryV3("Build age",             PrettyPrint.toAge(H2O.ABV.compiledOnDate(), new Date())));
    entries.add(new AboutEntryV3("Built by",              H2O.ABV.compiledBy()));
    entries.add(new AboutEntryV3("Built on",              H2O.ABV.compiledOn()));
    entries.add(new AboutEntryV3("Internal Security", H2OSecurityManager.instance().securityEnabled ? "Enabled": "Disabled"));

    if (H2O.ABV.isTooOld()) {
      String latestH2OVersion = H2O.ABV.getLatestH2OVersion();
      entries.add(new AboutEntryV3("Version warning",
                                   "Your H2O version is too old! Please download the latest version "
                                   + latestH2OVersion
                                   + " from http://h2o.ai/download/"));
    }

    for (H2O.AboutEntry ae : H2O.getAboutEntries()) {
      entries.add(new AboutEntryV3(ae.getName(), ae.getValue()));
    }

    s.entries = entries.toArray(new AboutEntryV3[entries.size()]);
    return s;
  }
}
