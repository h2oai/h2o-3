package water.parser;

import water.Futures;

interface StreamParseWriter<T extends StreamParseWriter> extends ParseWriter {
  StreamParseWriter nextChunk();
  StreamParseWriter reduce(T dout);
  StreamParseWriter close();
  StreamParseWriter close(Futures fs);
}
