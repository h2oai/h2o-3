package water.parser;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.Value;
import water.fvec.FileVec;
import water.util.Log;
import water.util.PrettyPrint;

import java.io.FileWriter;
import java.io.IOException;

public class ChunksizeTest extends TestUtil {
  @BeforeClass
  static public void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void run() throws IOException {
    FileWriter fw = new FileWriter("/tmp/chunksize.csv");
    String header = "\t" + String.format("%10s", "cloudSize")
            + "\t" + String.format("%8s", "cores")
            + "\t" + String.format("%8s", "numCols")
            + "\t" + String.format("%8s", "numRows")
            + "\t" + String.format("%16s", "maxLineLength")
            + "\t" + String.format("%13s", "totalSize")
            + "\t" + String.format("%13s", "chunkSize")
            + "\t" + String.format("%15s", "parseChunkCount")
            + "\t" + String.format("%20s", "bytesPerChunkPOJO")
            + "\t" + String.format("%15s", "totalChunks")
            +"\n";
    int[] toosmall=new int[2];
    int[] toolarge=new int[2];
    int[] toofew=new int[2];
    int[] toomany=new int[2];
    int[] counter=new int[2];
    int[] failed=new int[2];
    for (int oldheuristic : new int[]{0, 1}) {
      for (int cloudSize : new int[]{1,2,4,8,16,32,64,128,256,512,1024}) {
        for (int cores : new int[]{2,4,8,16,32,64}) { //per node
          for (int numCols : new int[]{1,2,4,8,16,32,64,128,256,512,1024,2048,4096,8192,16384,32768}) {
            for (long maxLineLength : new long[]{100,100,1000,10000,1000000}) {
              for (double totalSize : new double[]{1e4,1e5,1e6,1e7,1e8,1e9,1e10,1e11,1e12}) {

                int numRows = (int)(totalSize/maxLineLength);

                // exclude impossible stuff
                if (maxLineLength > totalSize) continue; //need at least 1 row
                if ((double)maxLineLength / numCols < 3) continue; //need at least 3 bytes per column
                if ((double)maxLineLength / numCols > 100) continue; //can't have more than 100 bytes per column

                // exclude unreasonable cases
                if (totalSize/(numCols*cores*cloudSize) > 1e9) continue; //no more than 1GB per column per core - unreasonably large overallocation
                if (totalSize/cloudSize > 2e11) continue; //no more than 200GB of data per node

                // Pretend to be in ParseSetup
                int chunkSize = FileVec.calcOptimalChunkSize((long) totalSize, numCols, maxLineLength, cores, cloudSize, oldheuristic==1);

                int parseChunkCount = (int) Math.max(1, totalSize/chunkSize);
                int parseChunkCountPerNode = parseChunkCount/cloudSize;
                double bytesPerChunkPOJO = totalSize/parseChunkCount/numCols;

                long totalChunks = (long)(totalSize/ bytesPerChunkPOJO);

                String log = "\t" + String.format("%10s", cloudSize)
                    + "\t" + String.format("%8s", cores)
                    + "\t" + String.format("%8s", numCols)
                    + "\t" + String.format("%8s", numRows)
                    + "\t" + String.format("%16s", maxLineLength)
                    + "\t" + String.format("%13s", totalSize)
                    + "\t" + String.format("%13s", chunkSize)
                    + "\t" + String.format("%15s", parseChunkCount)
                    + "\t" + String.format("%20s", bytesPerChunkPOJO)
                    + "\t" + String.format("%15s", totalChunks);

                boolean fail = false;
                String msg = "\n"+header + log + "                  <- TOO ";
                // don't cut small data into too many chunks (only 10 numbers per chunk)
                if (chunkSize < 10*maxLineLength) {
                  msg += "SMALL ";
                  FileVec.calcOptimalChunkSize((long) totalSize, numCols, maxLineLength, cores, cloudSize, oldheuristic==1);
                  toosmall[oldheuristic]++;
                  fail = true;
                }

                if (bytesPerChunkPOJO >= Value.MAX) {
                  msg += "LARGE ";
                  FileVec.calcOptimalChunkSize((long) totalSize, numCols, maxLineLength, cores, cloudSize, oldheuristic==1);
                  toolarge[oldheuristic]++;
                  fail = true;
                }

                // want at least one chunk per core
                if (parseChunkCountPerNode < cores) {
                  // only complain if we have at least 100k matrix entries per node - otherwise it's small data and fast enough anyway even with fewer chunks
                  if (numRows * numCols > 100000 * cloudSize
                      && totalSize/cloudSize/numCols/(4*cores) > 1000 // Only complain about too few chunks if there's enough data to cut it into Chunk POJO of 1kB each, otherwise it's small data and we're fine with fewer chunks
                      ) {
                    msg += "FEW ";
                    FileVec.calcOptimalChunkSize((long) totalSize, numCols, maxLineLength, cores, cloudSize, oldheuristic==1);
                    toofew[oldheuristic]++;
                    fail = true;
                  }
                }

                if (parseChunkCountPerNode*numCols > (1<<22)) {//no more than 4M chunk POJOs per node
                  msg += "MANY ";
                  FileVec.calcOptimalChunkSize((long) totalSize, numCols, maxLineLength, cores, cloudSize, oldheuristic==1);
                  toomany[oldheuristic]++;
                  fail = true;
                }

                if (fail) {
                  Log.info(msg + (oldheuristic==0?"(New Heuristic)":"(Old Heuristic)"));
                  failed[oldheuristic]++;
                }
                counter[oldheuristic]++;
              }
            }
          }
        }
      }
    }
    fw.close();
      for (int i : new int[]{0,1}) {
        Log.info((i==1 ? "Old" : "New") + " heuristic:");
        Log.info("Total: " + counter[i]);
        Log.info("Failure rate: " + PrettyPrint.formatPct((double) failed[i] / counter[i]));
        Log.info("Too small: " + PrettyPrint.formatPct((double) toosmall[i] / counter[i]));
        Log.info("Too large: " + PrettyPrint.formatPct((double) toolarge[i] / counter[i]));
        Log.info("Too few: " + PrettyPrint.formatPct((double) toofew[i] / counter[i]));
        Log.info("Too many: " + PrettyPrint.formatPct((double) toomany[i] / counter[i]));

        if (i==0) {
          Assert.assertTrue("Too small means that files cannot be parsed", toosmall[i] == 0);
          Assert.assertTrue("Too large means that chunks cannot fit in the DKV", toolarge[i] == 0);
          Assert.assertTrue("Too few means that cores aren't utilized", toofew[i] == 0);
          Assert.assertTrue("Too many means that each node has to store more than 4M chunks in its KV store", toomany[i] == 0);
        }
      }
  }
}

