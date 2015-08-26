package water.util;

import com.brsanthu.googleanalytics.EventHit;
import com.brsanthu.googleanalytics.ScreenViewHit;
import org.apache.commons.lang.StringUtils;
import water.H2O;
import water.H2ONode;

import java.util.Properties;

/**
 *  Collects all GA reporting methods in one spot
 */
public class GAUtils {
  public static void logRequest(String uri, Properties header) {
    if (H2O.GA != null) {
      // skip useless URIs
      if (uri.contains("/NodePersistentStorage") || uri.contains("/Metadata")) return;

      // clean URIs that include names eg. /3/DKV/random_key_name -> /3/DKV/
      if (uri.contains("/Frames/") ||
              uri.contains("/DKV/") ||
              uri.contains("/Models/") ||
              uri.contains("/Models.java/") ||
              uri.contains("/Predictions/")) {
        int idx = StringUtils.ordinalIndexOf(uri, "/", 3);
        if (idx > 0)
          uri = uri.substring(0, idx);
      }

      // post URI to GA
      if (header.getProperty("user-agent") != null)
        H2O.GA.postAsync(new ScreenViewHit(uri).customDimension(H2O.CLIENT_TYPE_GA_CUST_DIM, header.getProperty("user-agent")));
      else
        H2O.GA.postAsync(new ScreenViewHit(uri));
    }
  }

  public static void logParse(long totalParseSize, int fileCount, int colCount) {
    if (H2O.GA != null) {
      int parseSize = (int) (totalParseSize>>20);
      H2O.GA.postAsync(new EventHit("File I/O", "Read", "Total size (MB)", parseSize));
      postRange("File I/O", "Read", "Total size (MB)", new int[] {100, 500, 1000, 5000, 10000, 50000, 100000, 500000, 1000000, 5000000}, parseSize);

      H2O.GA.postAsync(new EventHit("File I/O", "Read", "File count", fileCount));
      H2O.GA.postAsync(new EventHit("File I/O", "Read", "Column count", colCount));
      postRange("File I/O", "Read", "Column count", new int[] {100, 500, 1000, 5000, 10000, 50000, 100000, 500000} , colCount);
    }
  }

  public static void logStartup() {
    if (H2O.GA != null) {
      if (H2O.SELF == H2O.CLOUD._memary[0]) {
        int cloudSize = H2O.CLOUD.size();

        H2O.GA.postAsync(new EventHit("System startup info", "Cloud", "Cloud size", cloudSize));
        if (cloudSize > 1)
          H2O.GA.postAsync(new EventHit("System startup info", "Cloud", "Multi-node cloud size", cloudSize));
        postRange("System startup info", "Cloud", "Cloud size", new int[]{2, 3, 4, 5, 10, 20, 30, 40, 50, 60}, cloudSize);

        if (H2O.ARGS.ga_hadoop_ver != null) {
          H2O.GA.postAsync(new EventHit("System startup info", "Hadoop version", H2O.ARGS.ga_hadoop_ver));
        } else if (H2O.CLOUD.size() > 1) {
          H2O.GA.postAsync(new EventHit("System startup info", "Hadoop version", "Non-hadoop cloud"));
        }

        // Figure out total memory usage
        int totMem = 0;
        H2ONode[] members = H2O.CLOUD.members();
        if (null != members) {
          // Sum at MB level
          for (int i = 0; i < members.length; i++) {
            totMem += (members[i].get_max_mem()>>20);
          }
        }
        //Simplfy to GB
        totMem = totMem>>10;
        H2O.GA.postAsync(new EventHit("System startup info", "Memory", "Total Cloud Memory (GB)", totMem));
        postRange("System startup info", "Memory", "Total Cloud Memory (GB)", new int[]{8,16,32,64,128,256,512,1024,2048,4096}, totMem);
      }
    }
  }

  private static void postRange(String category, String action, String label, int[] range, int value) {
    if (value < range[0]) {
      label = label + " < " + range[0];
    } else if ((value >= range[range.length-1])) {
      label = label + " >= " + range[0];
    } else {
      int i = 0;
      for(; i < range.length-2; i++) if (value >= range[i] && value < range[i+1]) break;
      label = label + " [" + range[i] + " " + range[i+1] + ")";
    }
    H2O.GA.postAsync(new EventHit(category, action, label, value));
  }
}
