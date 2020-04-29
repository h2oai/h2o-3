package water.fvec;

import water.*;
import water.util.StringUtils;

public class FVecFactory {

    public static Key makeByteVec(Key k, String... data) {
        byte [][] chunks = new byte[data.length][];
        long [] espc = new long[data.length+1];
        for(int i = 0; i < chunks.length; ++i){
            chunks[i] = StringUtils.bytesOf(data[i]);
            espc[i+1] = espc[i] + data[i].length();
        }
        Futures fs = new Futures();
        Key key = Vec.newKey();
        ByteVec bv = new ByteVec(key,Vec.ESPC.rowLayout(key,espc));
        for(int i = 0; i < chunks.length; ++i){
            Key chunkKey = bv.chunkKey(i);
            DKV.put(chunkKey, new Value(chunkKey,chunks[i].length,chunks[i], TypeMap.C1NCHUNK,Value.ICE),fs);
        }
        DKV.put(bv._key,bv,fs);
        Frame fr = new Frame(k,new String[]{"makeByteVec"},new Vec[]{bv});
        DKV.put(k, fr, fs);
        fs.blockForPending();
        return k;
    }

    public static Key makeByteVec(String... data) {
      Futures fs = new Futures();
      long[] espc  = new long[data.length+1];
      for( int i = 0; i < data.length; ++i ) espc[i+1] = espc[i]+data[i].length();
      Key k = Vec.newKey();
      ByteVec bv = new ByteVec(k,Vec.ESPC.rowLayout(k,espc));
      DKV.put(k,bv,fs);
      for( int i = 0; i < data.length; ++i ) {
        Key ck = bv.chunkKey(i);
        DKV.put(ck, new Value(ck,new C1NChunk(StringUtils.bytesOf(data[i]))),fs);
      }
      fs.blockForPending();
      return k;
    }
}
