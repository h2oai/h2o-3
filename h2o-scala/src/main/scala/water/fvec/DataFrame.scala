package water.fvec

import water._
import water.fvec._
import java.io.File

class DataFrame private ( key : Key, names : Array[String], vecs : Array[Vec] ) extends Frame(key,names,vecs) {

  def this(fr : Frame) = this(fr._key,fr._names,fr.vecs())
  
  def this(file : File) = this(water.util.FrameUtils.parseFrame(Key.make(water.parser.ParseSetup.hex(file.getName)),file))


}

//val fr = new DataFrame("airlines.csv")
//fr.map((Year,IsDelayed) => (...if( Year.isNA ) Year = 1977...))
