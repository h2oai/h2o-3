package hex.operations;

import hex.DataInfo;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.InteractionWrappedVec;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Arrays;

public class OneHot {
    public static Frame oneHot(Frame fr, String[] interactions, boolean useAll, boolean standardize, final boolean interactionsOnly, final boolean skipMissing) {
      final DataInfo dinfo = new DataInfo(fr,null,1,useAll,standardize? DataInfo.TransformType.STANDARDIZE: DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,skipMissing,false,false,false,false,false,interactions);
      Frame res;
      if( interactionsOnly ) {
        if( null==dinfo._interactionVecs ) throw new IllegalArgumentException("no interactions");
        int noutputs=0;
        final int[] colIds = new int[dinfo._interactionVecs.length];
        final int[] offsetIds = new int[dinfo._interactionVecs.length];
        int idx=0;
        String[] coefNames = dinfo.coefNames();
        for(int i : dinfo._interactionVecs)
          noutputs+= ( offsetIds[idx++] = ((InteractionWrappedVec)dinfo._adaptedFrame.vec(i)).expandedLength());
        String[] names = new String[noutputs];
        int offset=idx=0;
        int namesIdx=0;
        for(int i=0;i<dinfo._adaptedFrame.numCols();++i) {
          Vec v = dinfo._adaptedFrame.vec(i);
          if( v instanceof InteractionWrappedVec ) { // ding! start copying coefNames into names while offset < colIds[idx+1]
            colIds[idx] = offset;
            for(int nid=0;nid<offsetIds[idx];++nid)
              names[namesIdx++] = coefNames[offset++];
            idx++;
            if( idx > dinfo._interactionVecs.length ) break; // no more interaciton vecs left
          } else {
            if( v.isCategorical() ) offset+= v.domain().length - (useAll?0:1);
            else                    offset++;
          }
        }
        res = new MRTask() {
          @Override public void map(Chunk[] cs, NewChunk ncs[]) {
            DataInfo.Row r = dinfo.newDenseRow();
            for(int i=0;i<cs[0]._len;++i) {
              r=dinfo.extractDenseRow(cs,i,r);
              if( skipMissing && r.isBad() ) continue;
              int newChkIdx=0;
              for(int idx=0;idx<colIds.length;++idx) {
                int startOffset = colIds[idx];
                for(int start=startOffset;start<(startOffset+offsetIds[idx]);++start )
                  ncs[newChkIdx++].addNum(r.get(start));
              }
            }
          }
        }.doAll(noutputs,Vec.T_NUM,dinfo._adaptedFrame).outputFrame(Key.make(),names,null);
      } else {
        byte[] types = new byte[dinfo.fullN()];
        Arrays.fill(types, Vec.T_NUM);
        res = new MRTask() {
          @Override
          public void map(Chunk[] cs, NewChunk ncs[]) {
            DataInfo.Row r = dinfo.newDenseRow();
            for (int i = 0; i < cs[0]._len; ++i) {
              r = dinfo.extractDenseRow(cs, i, r);
              if( skipMissing && r.isBad() ) continue;
              for (int n = 0; n < ncs.length; ++n)
                ncs[n].addNum(r.get(n));
            }
          }
        }.doAll(types, dinfo._adaptedFrame.vecs()).outputFrame(Key.make("OneHot"+Key.make().toString()), dinfo.coefNames(), null);
      }
      dinfo.dropInteractions();
      dinfo.remove();
      return res;
    }
}
