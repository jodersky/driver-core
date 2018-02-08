package xyz.driver.core.database

import slick.lifted.AbstractTable
import xyz.driver.core.rest.errors.DatabaseException

import scala.concurrent.{ExecutionContext, Future}
import scalaz.{ListT, Monad, OptionT}
import scalaz.std.scalaFuture._

trait Dal {
  type T[D]
  implicit def monadT: Monad[T]

  def execute[D](operations: T[D]): Future[D]
  def noAction[V](v: V): T[V]
  def customAction[R](action: => Future[R]): T[R]

  def customAction[R](action: => OptionT[Future, R]): OptionT[T, R] =
    OptionT[T, R](customAction(action.run))

  implicit def fromStringOrThrow[D](mapper: (String) => Option[D])(msg: String)(fs: String): D = {
    mapper(fs).getOrElse(throw DatabaseException(msg))
  }
}

class FutureDal(executionContext: ExecutionContext) extends Dal {
  implicit val exec = executionContext
  override type T[D] = Future[D]
  implicit val monadT = implicitly[Monad[Future]]

  def execute[D](operations: T[D]): Future[D]     = operations
  def noAction[V](v: V): T[V]                     = Future.successful(v)
  def customAction[R](action: => Future[R]): T[R] = action
}

class SlickDal(database: Database, executionContext: ExecutionContext) extends Dal {
  import database.profile.api._
  implicit val exec = executionContext

  override type T[D] = slick.dbio.DBIO[D]

  implicit protected class QueryOps[U](query: Query[_, U, Seq]) {
    def resultT: ListT[T, U] = ListT[T, U](query.result.map(_.toList))
  }

  implicit protected class CompiledQueryOps[U](compiledQuery: slick.lifted.RunnableCompiled[_, Seq[U]]) {
    def resultT: ListT[T, U] = ListT.listT[T](compiledQuery.result.map(_.toList))
  }

  private val dbioMonad = new Monad[T] {
    override def point[A](a: => A): T[A]                  = DBIO.successful(a)
    override def bind[A, B](fa: T[A])(f: A => T[B]): T[B] = fa.flatMap(f)
  }

  override implicit def monadT: Monad[T] = dbioMonad

  override def execute[D](readOperations: T[D]): Future[D] = {
    database.database.run(readOperations.transactionally)
  }

  override def noAction[V](v: V): T[V]                     = DBIO.successful(v)
  override def customAction[R](action: => Future[R]): T[R] = DBIO.from(action)

  def affectsRows(updatesCount: Int): Option[Unit] = {
    if (updatesCount > 0) Some(()) else None
  }

  def insertReturning[AT <: AbstractTable[_], V](table: TableQuery[AT])(
      row: AT#TableElementType): slick.dbio.DBIO[AT#TableElementType] = {
    table.returning(table) += row
  }
}
