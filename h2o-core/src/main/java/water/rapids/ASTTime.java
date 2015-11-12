package water.rapids;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.MutableDateTime;
import org.joda.time.format.DateTimeFormatter;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.ParseTime;
import water.parser.BufferedString;

import java.util.Set;


class ASTGetTimeZone extends ASTPrim {
  @Override
  public String[] args() { return null; }
  @Override int nargs() { return 1; } // (getTimeZone)
  @Override
  public String str() { return "getTimeZone"; }
  @Override ValStr apply( Env env, Env.StackHelp stk, AST asts[] ) {
    return new ValStr(ParseTime.getTimezone().toString());
  }
}

class ASTListTimeZones extends ASTPrim {
  @Override
  public String[] args() { return null; }
  @Override int nargs() { return 1; } // (listTimeZones)
  @Override public String str() { return "listTimeZones"; }
  @Override ValFrame apply( Env env, Env.StackHelp stk, AST asts[] ) {
    String[] domain = ParseTime.listTimezones().split("\n");
    double ds[] = new double[domain.length];
    for( int i=0; i<domain.length; i++ ) ds[i] = i;
    Vec vec = Vec.makeVec(ds,Vec.VectorGroup.VG_LEN1.addVec());
    vec.setDomain(domain);
    return new ValFrame(new Frame(new String[]{"Timezones"}, new Vec[]{vec}));
  }
}

class ASTSetTimeZone extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"tz"}; }
  @Override int nargs() { return 1+1; } // (setTimeZone "TZ")
  @Override public String str() { return "setTimeZone"; }
  @Override ValNum apply( Env env, Env.StackHelp stk, AST asts[] ) {
    final String tz = asts[1].exec(env).getStr();
    Set<String> idSet = DateTimeZone.getAvailableIDs();
    if(!idSet.contains(tz))
      throw new IllegalArgumentException("Unacceptable timezone "+tz+" given.  For a list of acceptable names, use listTimezone().");
    new MRTask() { @Override public void setupLocal() { ParseTime.setTimezone(tz); }    }.doAllNodes();
    return new ValNum(Double.NaN);
  }
}

/** Basic time accessors; extract hours/days/years/etc from H2O's internal
 *  msec-since-Unix-epoch time */
abstract class ASTTime extends ASTPrim {
  @Override public String[] args() { return new String[]{"time"}; }
  @Override int nargs() { return 1+1; } // (op time)
  // Override for e.g. month and day-of-week
  protected String[][] factors() { return null; }
  abstract long op( MutableDateTime dt );
  private double op( MutableDateTime dt, double d ) {
    dt.setMillis((long)d);
    return op(dt);
  }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Val val = asts[1].exec(env);
    switch( val.type() ) {
    case Val.NUM: 
      double d = val.getNum();
      return new ValNum(Double.isNaN(d) ? d : op(new MutableDateTime(0),d));
    case Val.FRM: 
      Frame fr = stk.track(val).getFrame();
      if( fr.numCols() > 1 ) throw water.H2O.unimpl();
      return new ValFrame(new MRTask() {
          @Override public void map( Chunk chk, NewChunk cres ) {
            MutableDateTime mdt = new MutableDateTime(0,ParseTime.getTimezone());
            for( int i=0; i<chk._len; i++ )
              cres.addNum(chk.isNA(i) ? Double.NaN : op(mdt,chk.at8(i)));
          }
        }.doAll(1, Vec.T_NUM, fr).outputFrame(fr._names, factors()));
    default: throw water.H2O.fail();
    }
  }
}
class ASTYear   extends ASTTime { public String str(){ return "year" ; } long op(MutableDateTime dt) { return dt.getYear();}}
class ASTDay    extends ASTTime { public String str(){ return "day"  ; } long op(MutableDateTime dt) { return dt.getDayOfMonth();}}
class ASTHour   extends ASTTime { public String str(){ return "hour" ; } long op(MutableDateTime dt) { return dt.getHourOfDay();}}
class ASTMinute extends ASTTime { public String str(){ return "minute";} long op(MutableDateTime dt) { return dt.getMinuteOfHour();}}
class ASTSecond extends ASTTime { public String str(){ return "second";} long op(MutableDateTime dt) { return dt.getSecondOfMinute();}}
class ASTMillis extends ASTTime { public String str(){ return "millis";} long op(MutableDateTime dt) { return dt.getMillisOfSecond();}}
class ASTMonth  extends ASTTime { public String str(){ return "month"; } long op(MutableDateTime dt) { return dt.getMonthOfYear();}}
class ASTWeek   extends ASTTime { public String str(){ return "week";  } long op(MutableDateTime dt) { return dt.getWeekOfWeekyear();}}

class ASTDayOfWeek extends ASTTime {
  static private final String[][] FACTORS = new String[][]{{"Mon","Tue","Wed","Thu","Fri","Sat","Sun"}}; // Order comes from Joda
  @Override protected String[][] factors() { return FACTORS; }
  @Override
  public String str(){ return "dayOfWeek"; }
  @Override long op(MutableDateTime dt) { return dt.getDayOfWeek()-1;}
}

/** Convert a String to a Time (msec since Unix Epoch) via a given parse format */
class ASTasDate extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"time", "format"}; }
  @Override int nargs() { return 1+2; } // (as.Date time format)
  @Override
  public String str() { return "as.Date"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Vec vec = fr.vecs()[0];
    if( fr.vecs().length != 1 || !(vec.isCategorical() || vec.isString()))
      throw new IllegalArgumentException("as.Date requires a single column of factors or strings");

    final String format = asts[2].exec(env).getStr();
    if( format.isEmpty() ) throw new IllegalArgumentException("as.Date requires a non-empty format string");
    // check the format string more?

    final String[] dom  = vec.domain();
    final boolean isStr = dom==null && vec.isString();
    assert isStr || dom!=null : "as.Date error: domain is null, but vec is not String";

    Frame fr2 = new MRTask() {
      private transient DateTimeFormatter _fmt;
      @Override public void setupLocal() { _fmt=ParseTime.forStrptimePattern(format).withZone(ParseTime.getTimezone()); }
      @Override public void map( Chunk c, NewChunk nc ) {
        //done on each node in lieu of rewriting DateTimeFormatter as Iced
        String date;
        BufferedString tmpStr = new BufferedString();
        for( int i=0; i<c._len; ++i ) {
          if( !c.isNA(i) ) {
            if( isStr ) date = c.atStr(tmpStr, i).toString();
            else        date = dom[(int)c.at8(i)];
            nc.addNum(DateTime.parse(date,_fmt).getMillis(),0);
          } else nc.addNA();
        }
      }
    }.doAll(1, Vec.T_NUM, fr).outputFrame(fr._names, null);
    return new ValFrame(fr2);
  }
}


// Convert year, month, day, hour, minute, sec, msec to Unix epoch time
class ASTMktime extends ASTPrim {
  @Override
  public String[] args() { return new String[]{"yr", "mo", "dy", "hr", "mi", "se", "ms"}; }
  @Override int nargs() { return 1+7; } // (mktime yr mo dy hr mi se ms)
  @Override
  public String str() { return "mktime"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    // Seven args, all required.  See if any are arrays.
    Frame fs[] = new Frame[nargs()-1];
    int   is[] = new int  [nargs()-1];
    Frame x = null;             // Sample frame (for auto-expanding constants)
    for( int i=1; i<nargs(); i++ )
      if( asts[i] instanceof ASTId || asts[i] instanceof ASTExec )    fs[i-1] = x = stk.track(asts[i].exec(env)).getFrame();
      else                                                            is[i-1] = (int)asts[i].exec(env).getNum();

    if( x==null ) {                            // Single point
      long msec = new MutableDateTime(
              is[0],   // year
              is[1]+1, // month
              is[2]+1, // day
              is[3],   // hour
              is[4],   // minute
              is[5],   // second
              is[6])   // msec
              .getMillis();
      return new ValNum(msec);
    }

    // Make constant Vecs for the constant args.  Commonly, they'll all be zero
    Vec vecs[] = new Vec[7];
    for( int i=0; i<7; i++ ) {
      if( fs[i] == null ) {
        vecs[i] = x.anyVec().makeCon(is[i]);
      } else {
        if( fs[i].numCols() != 1 ) throw new IllegalArgumentException("Expect single column");
        vecs[i] = fs[i].anyVec();
      }
    }

    // Convert whole column to epoch msec
    Frame fr2 = new MRTask() {
      @Override public void map( Chunk chks[], NewChunk nchks[] ) {
        MutableDateTime dt = new MutableDateTime(0);
        NewChunk n = nchks[0];
        int rlen = chks[0]._len;
        for( int r=0; r<rlen; r++ ) {
          dt.setDateTime(
                  (int)chks[0].at8(r),  // year
                  (int)chks[1].at8(r)+1,// month
                  (int)chks[2].at8(r)+1,// day
                  (int)chks[3].at8(r),  // hour
                  (int)chks[4].at8(r),  // minute
                  (int)chks[5].at8(r),  // second
                  (int)chks[6].at8(r)); // msec
          n.addNum(dt.getMillis());
        }
      }
    }.doAll(new byte[]{Vec.T_NUM},vecs).outputFrame(new String[]{"msec"},null);
    // Clean up the constants
    for( int i=0; i<nargs()-1; i++ )
      if( fs[i] == null )
        vecs[i].remove();
    return new ValFrame(fr2);
  }
}
