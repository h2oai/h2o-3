package water.currents;

import java.util.Set;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.MutableDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.MRTask;
import water.fvec.*;
import water.parser.ParseTime;

class ASTListTimeZones extends ASTPrim {
  @Override int nargs() { return 1; } // (listTimeZones)
  @Override public String str() { return "listTimeZones"; }
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    String[] domain = ParseTime.listTimezones().split("\n");
    double ds[] = new double[domain.length];
    for( int i=0; i<domain.length; i++ ) ds[i] = i;
    Vec vec = Vec.makeVec(ds,Vec.VectorGroup.VG_LEN1.addVec());
    return new ValFrame(new Frame(new String[]{"Timezones"}, new Vec[]{vec}));
  }
}

class ASTSetTimeZone extends ASTPrim {
  @Override int nargs() { return 1+1; } // (setTimeZone "TZ")
  @Override public String str() { return "setTimeZone"; }
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    final String tz = asts[1].exec(env).getStr();
    Set<String> idSet = DateTimeZone.getAvailableIDs();
    if(!idSet.contains(tz))
      throw new IllegalArgumentException("Unacceptable timezone name given.  For a list of acceptable names, use listTimezone().");
    new MRTask() { @Override public void setupLocal() { ParseTime.setTimezone(tz); }    }.doAllNodes();
    return new ValNum(Double.NaN);
  }
}

