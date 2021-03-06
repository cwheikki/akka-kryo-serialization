package io.altoo.akka.serialization.kryo

import akka.annotation.InternalApi
import akka.event.LoggingAdapter
import akka.serialization.Serializer
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.{Input, Output, UnsafeInput, UnsafeOutput}

@InternalApi
private[kryo] class KryoSerializerBackend(val kryo: Kryo, val bufferSize: Int, val maxBufferSize: Int, val includeManifest: Boolean, val useUnsafe: Boolean)(log: LoggingAdapter) extends Serializer {
  // A unique identifier for this Serializer
  def identifier = 12454323

  // "toBinary" serializes the given object to an Array of Bytes
  override def toBinary(obj: AnyRef): Array[Byte] = {
    val buffer = getOutput
    try {
      if (includeManifest)
        kryo.writeObject(buffer, obj)
      else
        kryo.writeClassAndObject(buffer, obj)
      buffer.toBytes
    } catch {
      case e: StackOverflowError if !kryo.getReferences => // when configured with "nograph" serialization can fail with stack overflow
        log.error(e, "Could not serialize class with potentially circular references: {}", obj)
        throw new RuntimeException("Could not serialize class with potential circular references: " + obj)
    } finally {
      releaseBuffer(buffer)
    }
  }

  // "fromBinary" deserializes the given array,
  // using the type hint (if any, see "includeManifest" above)
  // into the optionally provided classLoader.
  override def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef = {
    if (includeManifest)
      clazz match {
        case Some(c) => kryo.readObject(getInput(bytes), c).asInstanceOf[AnyRef]
        case _ => throw new RuntimeException("Object of unknown class cannot be deserialized")
      }
    else
      kryo.readClassAndObject(getInput(bytes))
  }

  private[this] val getOutput =
    if (useUnsafe)
      new UnsafeOutput(bufferSize, maxBufferSize)
    else
      new Output(bufferSize, maxBufferSize)

  private def getInput(bytes: Array[Byte]): Input =
    if (useUnsafe)
      new UnsafeInput(bytes)
    else
      new Input(bytes)

  private def releaseBuffer(buffer: Output): Unit = {
    buffer.clear()
  }
}
