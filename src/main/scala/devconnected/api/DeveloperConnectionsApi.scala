package devconnected.api

import cats._
import cats.data.NonEmptyList
import devconnected.application.DeveloperConnections
import devconnected.application.connection.UserHandle
import devconnected.application.error.ConnectionsFailure
import devconnected.application.connection.Connected
import devconnected.application.connection.NotConnected
import devconnected.application.connection.GithubOrganisation
import devconnected.application.error.InvalidGithubUserHandle
import devconnected.application.error.InvalidTwitterUserHandle
import io.circe.literal.json
import io.circe.syntax._
import _root_.io.circe.Encoder
import org.http4s.circe._
import org.http4s._
import org.http4s.dsl._
import cats.effect.IO

class DeveloperConnectionsApi(connections: DeveloperConnections[IO]):

  val routes: HttpRoutes[IO] = {
    val dsl = Http4sDsl[IO]
    import dsl._
    HttpRoutes.of[IO] { case GET -> Root / "developers" / "connected" / dev1 / dev2 =>
      connections
        .checkConnection(UserHandle(dev1), UserHandle(dev2))
        .flatMap {
          case connected: Connected                     => Ok(connected.asJson)
          case NotConnected                             => Ok(NotConnected.asJson)
          case errors: NonEmptyList[ConnectionsFailure] => Ok(errors.asJson)
        }
        .handleErrorWith(e => InternalServerError(e.asJson))
    }
  }

  private given Encoder[GithubOrganisation] = Encoder.instance(_.toString.asJson)

  private given Encoder[Connected] = Encoder.instance { case Connected(organisations) =>
    json"""{ "connected": true, "organisations": ${organisations.asJson} }"""
  }

  private given Encoder[NotConnected.type] = Encoder.instance(_ => json"""{ "connected": false }""")

  private given Encoder[NonEmptyList[ConnectionsFailure]] = Encoder.instance { errors =>
    val errorStrings = errors.map {
      case InvalidGithubUserHandle(handle)  => s"$handle is no a valid user in github"
      case InvalidTwitterUserHandle(handle) => s"$handle is no a valid user in twitter"
    }

    json"""{"errors": ${errorStrings.asJson}}"""
  }

  private given Encoder[Throwable] = Encoder.instance(e => json"""{ "errors": ${List(e.getMessage).asJson} }""")
