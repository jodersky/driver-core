package xyz.driver.core

import java.io.ByteArrayOutputStream

import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

class CoreTest extends FlatSpec with Matchers with MockitoSugar {

  "'make' function" should "allow initialization for objects" in {

    val createdAndInitializedValue = make(new ByteArrayOutputStream(128)) { baos =>
      baos.write(Array(1.toByte, 1.toByte, 0.toByte))
    }

    createdAndInitializedValue.toByteArray should be(Array(1.toByte, 1.toByte, 0.toByte))
  }

  "'using' function" should "call close after performing action on resource" in {

    val baos = mock[ByteArrayOutputStream]

    using(baos /* usually new ByteArrayOutputStream(128) */ ) { baos =>
      baos.write(Array(1.toByte, 1.toByte, 0.toByte))
    }

    verify(baos).close()
  }

  "Id" should "have equality and ordering working correctly" in {

    (Id[String]("1234213") === Id[String]("1234213")) should be(true)
    (Id[String]("1234213") === Id[String]("213414")) should be(false)
    (Id[String]("213414") === Id[String]("1234213")) should be(false)

    val ids    = Seq(Id[String]("4"), Id[String]("3"), Id[String]("2"), Id[String]("1"))
    val sorted = Seq(Id[String]("1"), Id[String]("2"), Id[String]("3"), Id[String]("4"))

    ids.sorted should contain theSameElementsInOrderAs sorted
  }

  it should "have type-safe conversions" in {
    final case class X(id: Id[X])
    final case class Y(id: Id[Y])
    final case class Z(id: Id[Z])

    implicit val xy = Id.Mapper[X, Y]
    implicit val yz = Id.Mapper[Y, Z]

    // Test that implicit conversions work correctly
    val x  = X(Id("0"))
    val y  = Y(x.id)
    val z  = Z(y.id)
    val y2 = Y(z.id)
    val x2 = X(y2.id)
    (x2 === x) should be(true)
    (y2 === y) should be(true)

    // Test that type inferrence for explicit conversions work correctly
    val yid = y.id
    val xid = xy(yid)
    val zid = yz(yid)
    (xid: Id[X]) should be(zid: Id[Z])
  }

  "Name" should "have equality and ordering working correctly" in {

    (Name[String]("foo") === Name[String]("foo")) should be(true)
    (Name[String]("foo") === Name[String]("bar")) should be(false)
    (Name[String]("bar") === Name[String]("foo")) should be(false)

    val names  = Seq(Name[String]("d"), Name[String]("cc"), Name[String]("a"), Name[String]("bbb"))
    val sorted = Seq(Name[String]("a"), Name[String]("bbb"), Name[String]("cc"), Name[String]("d"))
    names.sorted should contain theSameElementsInOrderAs sorted
  }

  "Revision" should "have equality working correctly" in {

    val bla = Revision[String]("85569dab-a3dc-401b-9f95-d6fb4162674b")
    val foo = Revision[String]("f54b3558-bdcd-4646-a14b-8beb11f6b7c4")

    (bla === bla) should be(true)
    (bla === foo) should be(false)
    (foo === bla) should be(false)
  }

}
