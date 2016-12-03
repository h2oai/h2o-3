package examples

import examples.Murmur._

import scala.language.postfixOps

class Frequencies(numFeatures: Int, minDocFreq: Int) extends Serializable {
  import Frequencies._
  
  class IDF {
    /** number of documents */
    private var m = 0L
    /** document frequency vector */
    private var df = Array.fill[Long](numFeatures)(0L)
    
//    def +(doc: String) = this + (weigh(doc))

    /** Adds a new frequency vector. */
    def +(doc: Data): IDF = {
      for { i <- df.indices} if (doc(i) > 0) df(i) += 1

      m += 1L
      this
    }

    private def isEmpty: Boolean = m == 0L

    /** The current IDF */
    lazy val idf: Data = {
      if (isEmpty) {
        throw new IllegalStateException("Haven't seen any document yet.")
      }
      val mLog = math.log(m+1.0)
      val inv = df map (x => if (x > minDocFreq) mLog - math.log(x + 1.0) else 0.)
      inv
    }

    /**
      * Transforms a term frequency (TF) vector to a TF-IDF vector with a IDF vector
      *
      * @param values a term frequency vector
      * @return a TF-IDF vector
      */
    def normalize(values: Data): Data = {
      values zip idf map {case (v, c) => v*c}
    }
  }

  def hash(s: String) = murmurMod(numFeatures)(s)

  def weigh(text: String): Data = {
    val out = Array.fill[Double](numFeatures)(0.0)

    for {
      word <- tokenize(text)
    } out(hash(word)) += 1

    out
  }

  val IgnoreWords = Set("the", "not", "for")
  val IgnoreChars = "[,:;/<>\".()?\\-\\\'!01 ]"

  def tokenize(s: String) = {
    var smsText = s.toLowerCase.replaceAll(IgnoreChars, " ").replaceAll("  +", " ").trim
    val words =smsText split " " filter (w => !IgnoreWords(w) && w.length>2)

    words.toSeq
  }
}

object Frequencies {
  def name(i: Int) = "fv" + Integer.toString(i, 36)

  type Data = Array[Double]
}