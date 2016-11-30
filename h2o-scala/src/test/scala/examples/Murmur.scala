package examples

import java.lang.reflect.Field
import java.nio.charset.StandardCharsets

import sun.misc.Unsafe

/**
  * Murmur hash copied from spark
  */
object Murmur {

  def mod(i: Int, n: Int) = ((i % n) + n) % n

  def murmurMod(n: Int)(s: String) = mod(murmur3(s), n)

  def murmur3(s: String): Int = {
    val seed = 42
    val bytes = s.getBytes(StandardCharsets.UTF_8)
    hashUnsafeBytes(bytes, BYTE_ARRAY_OFFSET, bytes.length, seed)
  }

  private val _UNSAFE: Unsafe = {
    var unsafe: Unsafe = null
    try {
      val unsafeField: Field = classOf[Unsafe].getDeclaredField("theUnsafe")
      unsafeField.setAccessible(true)
      unsafeField.get(null).asInstanceOf[Unsafe]
    }
    catch {
      case cause: Throwable => {
        null
      }
    }
  }
  val BYTE_ARRAY_OFFSET = _UNSAFE.arrayBaseOffset(classOf[Array[Byte]])

  def getInt(x: AnyRef, offset: Long): Int = {
    _UNSAFE.getInt(x, offset)
  }

  def getByte(x: AnyRef, offset: Long): Byte = {
    _UNSAFE.getByte(x, offset)
  }

  def hashUnsafeBytes(base: AnyRef, offset: Long, lengthInBytes: Int, seed: Int): Int = {
    assert(lengthInBytes >= 0, "lengthInBytes cannot be negative")
    val lengthAligned: Int = lengthInBytes - lengthInBytes % 4
    var h1: Int = hashBytesByInt(base, offset, lengthAligned, seed)

    for {
      i <- lengthAligned until lengthInBytes
    } {
      val halfWord: Int = getByte(base, offset + i)
      val k1: Int = mixK1(halfWord)
      h1 = mixH1(h1, k1)
    }

    fmix(h1, lengthInBytes)
  }

  private def mixH1(h1: Int, k1: Int): Int = {
    val h2 = h1 ^ k1
    val h3 = Integer.rotateLeft(h2, 13)
    h3 * 5 + 0xe6546b64
  }

  private val C1: Int = 0xcc9e2d51
  private val C2: Int = 0x1b873593

  private def mixK1(k1: Int): Int = {
    val k2 = k1 * C1
    val k3 = Integer.rotateLeft(k2, 15)
    k3 * C2
  }

  private def fmix (h1: Int, length: Int): Int = {
    val h2 = h1 ^ length
    val h3 = h2 ^ (h2 >>> 16)
    val h4 = h3 * 0x85ebca6b
    val h5 = h4 ^ (h4 >>> 13)
    val h6 = h5 * 0xc2b2ae35
    h6 ^ (h6 >>> 16)
  }

  private def hashBytesByInt(base: AnyRef, offset: Long, lengthInBytes: Int, seed: Int): Int = {
    assert(lengthInBytes % 4 == 0)
    var h1: Int = seed

    for { i <- 0 until lengthInBytes by 4} {
      val halfWord: Int = getInt(base, offset + i)
      val k1: Int = mixK1(halfWord)
      h1 = mixH1(h1, k1)
    }

    h1
  }


}
