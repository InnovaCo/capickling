import eu.inn.binders.naming.{CamelCaseToSnakeCaseConverter, PlainConverter}
import org.scalatest.mock.MockitoSugar.mock
import org.scalatest.{FlatSpec, Matchers}
import org.mockito.Mockito._
import eu.inn.binders._

class TestBindIntSpec extends FlatSpec with Matchers {

  "all case class fields with int " should " be bound to statement by names " in {
    val m = mock[TestSerializer[PlainConverter]]
    m.bind(TestInt(123456, Some(555), 7890))
    verify(m).setInt("intValue1", 123456)
    verify(m).setIntNullable("nullableValue", Some(555))
    verify(m).setInt("intValue2", 7890)
    verifyNoMoreInteractions(m)
  }

  "all case class fields with int " should " be bound to statement by names with specified convention" in {
    val m = mock[TestSerializer[CamelCaseToSnakeCaseConverter]]
    m.bind(TestInt(123456, Some(555), 7890))
    verify(m).setInt("int_value1", 123456)
    verify(m).setIntNullable("nullable_value", Some(555))
    verify(m).setInt("int_value2", 7890)
    verifyNoMoreInteractions(m)
  }

  "all int parameters " should " be bound to statement by indexes " in {
    val m = mock[TestSerializer[PlainConverter]]
    val i1 = 123456
    val i2 = Some(555)
    val i3 = 7890
    m.bindParameter(0, i1)
    m.bindParameter(1, i2)
    m.bindParameter(2, i3)
    verify(m).setInt(0, 123456)
    verify(m).setIntNullable(1, Some(555))
    verify(m).setInt(2, 7890)
    verifyNoMoreInteractions(m)
  }

  "all int parameters " should " be bound to statement as args by indexes " in {
    val m = mock[TestSerializer[PlainConverter]]
    val i1 = 123456
    val i2 = Some(555)
    val i3 = 7890
    m.bindArgs(i1, i2, i3)
    verify(m).setInt(0, 123456)
    verify(m).setIntNullable(1, Some(555))
    verify(m).setInt(2, 7890)
    verifyNoMoreInteractions(m)
  }

  "some case class fields with int " should " be bound to statement by names " in {
    val m = mock[TestSerializer[PlainConverter]]
    when(m.hasField("intValue1")).thenReturn(true)
    when(m.hasField("nullableValue")).thenReturn(true)
    when(m.hasField("intValue2")).thenReturn(false)
    m.bindPartial(TestInt(123456, Some(555), 7890))
    verify(m).setInt("intValue1", 123456)
    verify(m).setIntNullable("nullableValue", Some(555))
    verify(m, times(0)).setInt("intValue2", 7890)
  }
}
