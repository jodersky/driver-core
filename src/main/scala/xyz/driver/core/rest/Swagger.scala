package xyz.driver.core.rest

import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileAndResourceDirectives.ResourceFile
import akka.stream.ActorAttributes
import akka.stream.scaladsl.{Framing, StreamConverters}
import akka.util.ByteString
import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model._
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import io.swagger.models.Scheme
import io.swagger.util.Json

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe.Type
import scala.util.control.NonFatal

class Swagger(
    override val host: String,
    override val schemes: List[Scheme],
    version: String,
    val apiTypes: Seq[Type],
    val config: Config,
    val logger: Logger)
    extends SwaggerHttpService {

  lazy val mirror = universe.runtimeMirror(getClass.getClassLoader)

  override val apiClasses = apiTypes.map { tpe =>
    mirror.runtimeClass(tpe.typeSymbol.asClass)
  }.toSet

  // Note that the reason for overriding this is a subtle chain of causality:
  //
  // 1. Some of our endpoints require a single trailing slash and will not
  // function if it is omitted
  // 2. Swagger omits trailing slashes in its generated api doc
  // 3. To work around that, a space is added after the trailing slash in the
  // swagger Path annotations
  // 4. This space is removed manually in the code below
  //
  // TODO: Ideally we'd like to drop this custom override and fix the issue in
  // 1, by dropping the slash requirement and accepting api endpoints with and
  // without trailing slashes. This will require inspecting and potentially
  // fixing all service endpoints.
  override def generateSwaggerJson: String = {
    import io.swagger.models.{Swagger => JSwagger}

    import scala.collection.JavaConverters._
    try {
      val swagger: JSwagger = reader.read(apiClasses.asJava)

      // Removing trailing spaces
      swagger.setPaths(
        swagger.getPaths.asScala
          .map {
            case (key, path) =>
              key.trim -> path
          }
          .toMap
          .asJava)

      Json.pretty().writeValueAsString(swagger)
    } catch {
      case NonFatal(t) =>
        logger.error("Issue with creating swagger.json", t)
        throw t
    }
  }

  override val basePath: String    = config.getString("swagger.basePath")
  override val apiDocsPath: String = config.getString("swagger.docsPath")

  override val info = Info(
    config.getString("swagger.apiInfo.description"),
    version,
    config.getString("swagger.apiInfo.title"),
    config.getString("swagger.apiInfo.termsOfServiceUrl"),
    contact = Some(
      Contact(
        config.getString("swagger.apiInfo.contact.name"),
        config.getString("swagger.apiInfo.contact.url"),
        config.getString("swagger.apiInfo.contact.email")
      )),
    license = Some(
      License(
        config.getString("swagger.apiInfo.license"),
        config.getString("swagger.apiInfo.licenseUrl")
      )),
    vendorExtensions = Map.empty[String, AnyRef]
  )

  /** A very simple templating extractor. Gets a resource from the classpath and subsitutes any `{{key}}` with a value. */
  private def getTemplatedResource(
      resourceName: String,
      contentType: ContentType,
      substitution: (String, String)): Route = get {
    Option(this.getClass.getClassLoader.getResource(resourceName)) flatMap ResourceFile.apply match {
      case Some(ResourceFile(url, length, _)) =>
        extractSettings { settings =>
          val stream = StreamConverters
            .fromInputStream(() => url.openStream())
            .withAttributes(ActorAttributes.dispatcher(settings.fileIODispatcher))
            .via(Framing.delimiter(ByteString("\n"), 4096, true).map(_.utf8String))
            .map { line =>
              line.replaceAll(s"\\{\\{${substitution._1}\\}\\}", substitution._2)
            }
            .map(line => ByteString(line))
          complete(
            HttpEntity.Default(contentType, length, stream)
          )
        }
      case None => reject
    }
  }

  def swaggerUI: Route =
    pathEndOrSingleSlash {
      getTemplatedResource(
        "swagger-ui/index.html",
        ContentTypes.`text/html(UTF-8)`,
        "title" -> config.getString("swagger.apiInfo.title"))
    } ~ getFromResourceDirectory("swagger-ui")

}
