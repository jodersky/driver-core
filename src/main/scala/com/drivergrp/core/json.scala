package com.drivergrp.core

import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.PathMatcher.Matched
import akka.http.scaladsl.server.{PathMatcher, _}
import com.drivergrp.core.time.Time
import spray.json.{DeserializationException, JsNumber, _}

import scala.reflect.runtime.universe._

object json {

  def IdInPath[T]: PathMatcher1[Id[T]] =
    PathMatcher("""[+-]?\d*""".r) flatMap { string =>
      try Some(Id[T](string.toLong))
      catch { case _: IllegalArgumentException => None }
    }

  implicit def idFormat[T] = new RootJsonFormat[Id[T]] {
    def write(id: Id[T]) = JsNumber(id)

    def read(value: JsValue) = value match {
      case JsNumber(id) => Id[T](id.toLong)
      case _            => throw new DeserializationException("Id expects number")
    }
  }

  def NameInPath[T]: PathMatcher1[Name[T]] = new PathMatcher1[Name[T]] {
    def apply(path: Path) = Matched(Path.Empty, Tuple1(Name[T](path.toString)))
  }

  implicit def nameFormat[T] = new RootJsonFormat[Name[T]] {
    def write(name: Name[T]) = JsString(name)

    def read(value: JsValue): Name[T] = value match {
      case JsString(name) => Name[T](name)
      case _              => throw new DeserializationException("Name expects string")
    }
  }

  def TimeInPath: PathMatcher1[Time] =
    PathMatcher("""[+-]?\d*""".r) flatMap { string =>
      try Some(Time(string.toLong))
      catch { case _: IllegalArgumentException => None }
    }

  implicit val timeFormat = new RootJsonFormat[Time] {
    def write(time: Time) = JsObject("timestamp" -> JsNumber(time.millis))

    def read(value: JsValue): Time = value match {
      case JsObject(fields) =>
        fields
          .get("timestamp")
          .flatMap {
            case JsNumber(millis) => Some(Time(millis.toLong))
            case _                => None
          }
          .getOrElse(throw new DeserializationException("Time expects number"))
      case _ => throw new DeserializationException("Time expects number")
    }
  }

  class EnumJsonFormat[T](mapping: (String, T)*) extends JsonFormat[T] {
    private val map = mapping.toMap

    override def write(value: T): JsValue = {
      map.find(_._2 == value).map(_._1) match {
        case Some(name) => JsString(name)
        case _          => serializationError(s"Value $value is not found in the mapping $map")
      }
    }

    override def read(json: JsValue): T = json match {
      case JsString(name) =>
        map.getOrElse(name, throw new DeserializationException(s"Value $name is not found in the mapping $map"))
      case _ => deserializationError("Expected string as enumeration value, but got " + json)
    }
  }

  class ValueClassFormat[T: TypeTag](writeValue: T => BigDecimal, create: BigDecimal => T) extends JsonFormat[T] {
    def write(valueClass: T) = JsNumber(writeValue(valueClass))
    def read(json: JsValue): T = json match {
      case JsNumber(value) => create(value)
      case _               => deserializationError(s"Expected number as ${typeOf[T].getClass.getName}, but got " + json)
    }
  }
}
