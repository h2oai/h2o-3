package water.api;

import water.H2O;
import water.api.schemas3.AboutEntryV3;
import water.api.schemas3.AboutV3;

import java.util.ArrayList;

public class AboutHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public AboutV3 get(int version, AboutV3 s) {
    ArrayList<AboutEntryV3> entries = new ArrayList<>();
    entries.add(new AboutEntryV3("Build git branch",      H2O.ABV.branchName()));
    entries.add(new AboutEntryV3("Build git hash",        H2O.ABV.lastCommitHash()));
    entries.add(new AboutEntryV3("Build git describe",    H2O.ABV.describe()));
    entries.add(new AboutEntryV3("Build project version", H2O.ABV.projectVersion()));
    entries.add(new AboutEntryV3("Built by",              H2O.ABV.compiledBy()));
    entries.add(new AboutEntryV3("Built on",              H2O.ABV.compiledOn()));

    for (H2O.AboutEntry ae : H2O.getAboutEntries()) {
      entries.add(new AboutEntryV3(ae.getName(), ae.getValue()));
    }

    s.entries = entries.toArray(new AboutEntryV3[entries.size()]);
    return s;
  }
}
