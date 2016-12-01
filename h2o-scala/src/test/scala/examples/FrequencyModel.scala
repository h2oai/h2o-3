package examples

import examples.Murmur._

import scala.collection.mutable
import scala.language.postfixOps

class FrequencyModel(numFeatures: Int, minDocFreq: Int) extends Serializable {
  import FrequencyModel._
  
  class IDF {

    /** number of documents */
    private var m = 0L
    /** document frequency vector */
    private var df: collection.mutable.Map[String, Long] =collection.mutable.Map[String, Long]()

    /** Adds a new frequency vector. */
    def +(doc: Data): IDF = {
      for {i <- doc.keys} df(i) = df.getOrElse(i, 0L) + 1

      m += 1L
      this
    }

    private def isEmpty: Boolean = m == 0L

    /** Returns the current IDF */
    private lazy val idf: Data = {
      if (isEmpty) {
        throw new IllegalStateException("Haven't seen any document yet.")
      }
      val mLog = math.log(m+1.0)
      val importantOnes = df filter (_._2 >= minDocFreq)
      val inv = importantOnes mapValues (x => mLog - math.log(x + 1.0))

      inv toMap
    }

    def normalize: (Data) => Data = idfNormalize(idf)
  }

  def hash(s: String) = murmurMod(numFeatures)(s)

  def weigh(msg: String): Data = weighWords(tokenize(msg).toList)

  def weighWords(text: Traversable[String]): Data = {
    val hashes = text map hash map name
    val counts = mutable.Map.empty[String, Double]

    for {
      h <- hashes
    } {
      counts(h) = counts.getOrElse(h, 0.0) + 1.0
    }
    
    counts toMap
  }

  /**
    * Transforms a term frequency (TF) vector to a TF-IDF vector with a IDF vector
    *
    * @param idf an IDF vector
    * @param values a term frequency vector
    * @return a TF-IDF vector
    */
  def idfNormalize(idf: Data)(values: Data): Data = {
    val all = values map { case (k, v) => k -> v * idf.getOrElse(k, 0.0) }
    val r = all filter (0.0 < _._2)
    r
  }

  val IgnoreWords = Set("the", "not", "for")
  val IgnoreChars = "[,:;/<>\".()?\\-\\\'!01 ]"

  def tokenize(s: String) = {
    var smsText = s.toLowerCase.replaceAll(IgnoreChars, " ").replaceAll("  +", " ").trim
    val words =smsText split " " filter (w => !IgnoreWords(w) && w.length>2)

    words.toSeq
  }
}

object FrequencyModel {
  def name(i: Int) = "fv" + Integer.toString(i, 36)

  type Data = Map[String, Double]
}