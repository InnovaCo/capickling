import eu.inn.binders.core.{Serializer, Deserializer}
import eu.inn.binders.value.Value
import eu.inn.binders.naming.Converter
import java.util.Date

import scala.reflect.ClassTag

trait TestGeneric

trait TestDeserializer[C <: Converter] extends Deserializer[C] {
  def readInt(): Int

  def readIntNullable(): Option[Int]

  def readDate(): Date

  def readDateNullable(): Option[Date]

  def readLong(): Long

  def readString(): String

  def isNull: Boolean

  def readDynamic(): Value

  def iterator(): Iterator[TestDeserializer[C]]
}

trait TestDeserializerWithGenerics[C <: Converter] extends Deserializer[C] {
  def readInt(): Int

  def readIntNullable(): Option[Int]

  def readDate(): Date

  def readDateNullable(): Option[Date]

  def readLong(): Long

  def readString(): String

  def readMap[K, V](): Map[K, V]

  def isNull: Boolean

  def readDynamic(): Value

  def readGenericWihBounds[T <: TestGeneric](): T

  def iterator(): Iterator[TestDeserializerWithGenerics[C]]
}

trait TestDeserializerWithList[C <: Converter] extends Deserializer[C] {
  def readList[T: ClassTag](): List[T] = ???

  def iterator(): Iterator[TestDeserializer[C]]
}

trait TestSerializer[C <: Converter] extends Serializer[C] {
  def writeInt(value: Int)

  def writeIntNullable(value: Option[Int])

  def writeDate(value: Date)

  def writeDateNullable(value: Option[Date])

  def writeLong(value: Long)

  def writeString(value: String)

  def writeNull()

  def getFieldSerializer(fieldName: String): Option[TestSerializer[C]]

  def beginArray()

  def endArray()

  def beginObject()

  def endObject()
}

trait TestSerializerWithGenerics[C <: Converter] extends Serializer[C] {
  def writeInt(value: Int)

  def writeIntNullable(value: Option[Int])

  def writeDate(value: Date)

  def writeDateNullable(value: Option[Date])

  def writeSeq[T: ClassTag](value: Seq[T])

  def writeMap[K, V](value: Map[K, V])

  def writeMapNullable[K, V](value: Option[Map[K, V]])

  def writeLong(value: Long)

  def writeString(value: String)

  def writeGenericWithBounds[T <: TestGeneric](value: T)

  def writeNull()

  def getFieldSerializer(fieldName: String): Option[TestSerializer[C]]

  def beginArray()

  def endArray()
}
