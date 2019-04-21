package water.parser;

import water.MemoryManager;
import water.util.StringUtils;

import static water.util.ArrayUtils.*;

public class PackedDomains {

  public static int sizeOf(byte[] domain) {
    return encodeAsInt(domain, 0);
  }

  public static String[] unpackToStrings(byte[] domain) {
    final int n = sizeOf(domain);
    String[] out = new String[n];
    int pos = 4;
    for (int i = 0; i < n; i++) {
      int len = encodeAsInt(domain, pos);
      pos += 4;
      out[i] = StringUtils.toString(domain, pos, len);
      pos += len;
    }
    return out;
  }

  public static byte[] pack(BufferedString[] source) {
    int len = 0;
    for (BufferedString bs : source)
      len += bs.length();
    byte[] data = new byte[len + (source.length + 1) * 4];
    decodeAsInt(source.length, data, 0);
    int pos = 4;
    for (BufferedString bs : source) {
      byte[] buff = bs.getBuffer();
      decodeAsInt(bs.length(), data, pos);
      pos += 4;
      for (int i = bs.getOffset(); i < bs.getOffset() + bs.length(); i++)
        data[pos++] = buff[i];
    }
    return data;
  }

  static int calcMergedSize(byte[] as, byte[] bs) {
    int shared = 0;
    int pA = 4;
    int pB = 4;
    BufferedString bsA = new BufferedString(as, 0, 0);
    BufferedString bsB = new BufferedString(bs, 0, 0);
    while ((pA < as.length) && (pB < bs.length)) {
      int sizeA = encodeAsInt(as, pA);
      bsA.setOff(pA + 4);
      bsA.setLen(sizeA);
      int sizeB = encodeAsInt(bs, pB);
      bsB.setOff(pB + 4);
      bsB.setLen(sizeB);
      int x = bsA.compareTo(bsB);
      if (x < 0) {
        pA += sizeA + 4;
      } else if (x > 0) {
        pB += sizeB + 4;
      } else {
        shared += sizeA + 4;
        pA += sizeA + 4;
        pB += sizeA + 4;
      }
    }
    return as.length + bs.length - 4 - shared;
  }

  public static byte[] merge(byte[] as, byte[] bs) {
    int size = calcMergedSize(as, bs);
    if (size == as.length)
      return as;
    if (size == bs.length)
      return bs;
    byte[] data = MemoryManager.malloc1(size);

    int shared = 0; // number of shared words
    int pos = 4; // position in output
    int pA = 4; // position in A
    int pB = 4; // position in B

    while (pA < as.length && pB < bs.length) {
      int wordPos = pos;
      pos += 4;

      int wA = pA;
      int sizeA = encodeAsInt(as, pA); pA += 4;
      int endA = pA + sizeA;

      int wB = pB;
      int sizeB = encodeAsInt(bs, pB); pB += 4;
      int endB = pB + sizeB;

      int l = sizeA > sizeB ? sizeB : sizeA;
      int comp = sizeA - sizeB;
      for (int i = 0; i < l; i++) {
        int x = (0xFF & as[pA]) - (0xFF & bs[pB]);
        if (x != 0) {
          comp = x;
          break;
        }
        data[pos++] = as[pA++];
        pB++;
      }
      if ((pA == endA) && (pB == endB)) { // words were the same
        decodeAsInt(sizeA, data, wordPos);
        shared++;
      } else if (comp < 0) { // output word A
        while (pA < endA)
          data[pos++] = as[pA++];
        decodeAsInt(sizeA, data, wordPos);
        pB = wB;
      } else { // output word B
        while (pB < endB)
          data[pos++] = bs[pB++];
        decodeAsInt(sizeB, data, wordPos);
        pA = wA;
      }
    }
    while (pA < as.length)
      data[pos++] = as[pA++];
    while (pB < bs.length)
      data[pos++] = bs[pB++];
    int len = encodeAsInt(as, 0) + encodeAsInt(bs, 0) - shared;
    decodeAsInt(len, data, 0);
    return data;
  }

}
