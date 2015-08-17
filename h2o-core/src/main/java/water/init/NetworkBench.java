package water.init;

import jsr166y.CountedCompleter;
import jsr166y.RecursiveAction;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.util.Log;
import water.util.MathUtils;
import water.util.TwoDimTable;

import java.util.Random;

/**
 * Created by tomasnykodym on 7/28/15.
 */
public class NetworkBench extends Iced {
//  public static int [] MSG_SZS = new int[]{1, 64, 256, 1024, 4096, 16384, 65536, 262144, 1048576, 4194304};
//  public static int [] MSG_SZS = new int[]{1};
//  public static int [] MSG_CNT = new int[]{100};

  public static int [] MSG_SZS = new int[]{1,64,512,1024,4*1024,8*1024,16*1024,32*1024,64*1024,16*1024*1024};
  public static int [] MSG_CNT = new int[]{500000,8*32*1024,32*1024,16*1024,4*1024,2*1024,1024,512,256,16};
//  public static int [] MSG_CNT = new int[]{500000, 25000, 12500, 6000, 781, 391, 195, 98, 49, 25};

  public static class NetworkBenchResults {
    final int _msgSz;
    final int _msgCnt;
    final long [] _mrtTimes;
    final long [][] _all2AllTimes;

    public NetworkBenchResults(int msgSz, int msgCnt, long [][] all2all, long [] mrts) {
      _msgSz = msgSz;
      _msgCnt = msgCnt;
      _mrtTimes = mrts;
      _all2AllTimes = all2all;
    }

    public TwoDimTable to2dTable(){
      // public TwoDimTable(String tableHeader, String tableDescription, String[] rowHeaders, String[] colHeaders, String[] colTypes,
      // String[] colFormats, String colHeaderForRowHeaders) {
      String title = "Network Bench, sz = " + _msgSz + "B, cnt = " + _msgCnt + ", total sz = " + 0.01*((int)(100*_msgSz*_msgCnt/(1024.0*1024))) + "MB";
      String [] rowHeaders = new String[H2O.CLOUD.size() + 1];
      rowHeaders[H2O.CLOUD.size()] = "MrTasks";
      String [] colHeaders = new String[H2O.CLOUD.size()];
      String [] colTypes = new String[H2O.CLOUD.size()];
      String [] colFormats = new String[H2O.CLOUD.size()];
      for(int i = 0; i < H2O.CLOUD.size(); ++i) {
        rowHeaders[i] = colHeaders[i] = H2O.CLOUD._memary[i].toString();
        colTypes[i] = "double";
        colFormats[i] = "%2f";
      }
      TwoDimTable td = new TwoDimTable(title, "Network benchmark results, round-trip bandwidth in MB/s", rowHeaders, colHeaders, colTypes, colFormats, "");
      double sizeKB = _msgSz * _msgCnt / 1024.0;
      for(int i = 0 ; i < _all2AllTimes.length; ++i) {
        for (int j = 0; j < _all2AllTimes.length; ++j)
          td.set(i, j, MathUtils.roundToNDigits( sizeKB / (0.001 * _all2AllTimes[i][j]),4));
        td.set(H2O.CLOUD.size(),i, MathUtils.roundToNDigits(sizeKB / (_mrtTimes[i] * 0.001),4));
      }
      return td;
    }
  }

  public NetworkBenchResults [] _results;
  public NetworkBench doTest(){
    long t1 = System.currentTimeMillis();
    H2O.submitTask(new H2OCountedCompleter() {
     @Override
     protected void compute2() {
       _results = new NetworkBenchResults[MSG_SZS.length];
       for(int i = 0; i < MSG_SZS.length; ++i) {
         long t2 = System.currentTimeMillis();
         long [] mrts = new long[H2O.CLOUD.size()];
         Log.info("Network Bench, running All2All, message size = " + MSG_SZS[i] + ", message count = " + MSG_CNT[i]);
         long[][] all2all = new TestAll2All(i).doAllNodes()._time;
         Log.info("All2All test done in " + ((System.currentTimeMillis()-t2)*0.001) + "s");
         for(int j = 0; j < H2O.CLOUD.size(); ++j) {
           Log.info("Network Bench, running MRTask test at node " + j + ", message size = " + MSG_SZS[i] + ", message count = " + MSG_CNT[i]);
           mrts[j] = RPC.call(H2O.CLOUD._memary[j], new TestMRTasks(i)).get()._time;
         }
         _results[i] = new NetworkBenchResults(MSG_SZS[i],MSG_CNT[i],all2all,mrts);
       }
       tryComplete();
     }
                   }).join();
    for(NetworkBenchResults r:_results) {
      System.out.println("===================================== MSG SZ = " + r._msgSz + ", CNT = " + r._msgCnt + " =========================================");
      System.out.println(r.to2dTable());
      System.out.println();
    }
    Log.info("Newtork test done in " + ((System.currentTimeMillis()-t1)*0.001) + "s");
    return this;
  }

  private static class TestAll2All extends MRTask<TestAll2All> {
    final int  _i;  // in=
    long [][] _time; // out

    public TestAll2All(int i) {
      _i = i;
    }

    private static class SendRandomBytesTsk extends DTask{
      final byte [] dd;

      public SendRandomBytesTsk(byte[] data) {dd = data;}
      @Override
      protected void compute2() {tryComplete();}
    }
    @Override
    public void setupLocal(){
      _time = new long[H2O.CLOUD.size()][];
      final int myId = H2O.SELF.index();
      _time[myId] = new long[H2O.CLOUD.size()];
      addToPendingCount(H2O.CLOUD.size());
      for (int i = 0; i < H2O.CLOUD.size(); ++i) {
        final int fi = i;
        new CountedCompleter(this) {
          long t1;
          @Override
          public void compute() {
            t1 = System.currentTimeMillis();
            addToPendingCount(MSG_CNT[_i] - 1);
            for (int j = 0; j < MSG_CNT[_i]; ++j)
              new RPC(H2O.CLOUD._memary[fi], new SendRandomBytesTsk(data[_i])).addCompleter(this).call();
          }
          @Override
          public void onCompletion(CountedCompleter cc) {
            long t2 = System.currentTimeMillis();
            _time[myId][fi] = (t2 - t1);
          }
        }.fork();
      }
    }

    @Override public void reduce(TestAll2All tst) {
      for(int i = 0; i < _time.length; ++i)
        if(_time[i] == null)
          _time[i] = tst._time[i];
        else
          assert tst._time[i] == null;
    }
  }

  private static byte [][] data;
  static {
    data = new byte[MSG_SZS.length][];
    for(int i = 0 ; i < MSG_SZS.length; ++i) {
      data[i] = new byte[MSG_SZS[i]];
      new Random().nextBytes(data[i]);
    }
  }

  private static class TestMRTasks extends DTask<TestMRTasks> {
    final int _i;  // in
    final int _msgCnt; // in

    public TestMRTasks(int i) {
      _i = i;
      _msgCnt = MSG_CNT[i];
    }
    long _time;  // out
    @Override
    protected void compute2() {
      _time = System.currentTimeMillis();
      addToPendingCount(_msgCnt-1);
      for(int i = 0; i < _msgCnt; ++i)
        new MRTask(this){
          byte [] dd = data[_i];
          @Override public void setupLocal(){
            dd = null;
          }
        }.asyncExecOnAllNodes();
    }
    @Override public byte priority(){return 1;}
    @Override public void onCompletion(CountedCompleter cc) {
      _time = System.currentTimeMillis() - _time;
    }
  }
}
