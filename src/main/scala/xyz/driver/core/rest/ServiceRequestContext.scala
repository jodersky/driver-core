package xyz.driver.core.rest

import xyz.driver.core.auth.{AuthToken, PermissionsToken, User}
import xyz.driver.core.generators

import scalaz.Scalaz.{mapEqual, stringInstance}
import scalaz.syntax.equal._

class ServiceRequestContext(val trackingId: String = generators.nextUuid().toString,
                            val contextHeaders: Map[String, String] = Map.empty[String, String]) {
  def authToken: Option[AuthToken] =
    contextHeaders.get(AuthProvider.AuthenticationTokenHeader).map(AuthToken.apply)

  def permissionsToken: Option[PermissionsToken] =
    contextHeaders.get(AuthProvider.PermissionsTokenHeader).map(PermissionsToken.apply)

  def withAuthToken(authToken: AuthToken): ServiceRequestContext =
    new ServiceRequestContext(
      trackingId,
      contextHeaders.updated(AuthProvider.AuthenticationTokenHeader, authToken.value)
    )

  def withAuthenticatedUser[U <: User](authToken: AuthToken, user: U): AuthorizedServiceRequestContext[U] =
    new AuthorizedServiceRequestContext(
      trackingId,
      contextHeaders.updated(AuthProvider.AuthenticationTokenHeader, authToken.value),
      user
    )

  override def hashCode(): Int =
    Seq[Any](trackingId, contextHeaders).foldLeft(31)((result, obj) => 31 * result + obj.hashCode())

  override def equals(obj: Any): Boolean = obj match {
    case ctx: ServiceRequestContext => trackingId === ctx.trackingId && contextHeaders === ctx.contextHeaders
    case _                          => false
  }

  override def toString: String = s"ServiceRequestContext($trackingId, $contextHeaders)"
}
