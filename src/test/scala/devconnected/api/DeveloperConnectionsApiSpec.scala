package devconnected.api

import cats.effect.IO
import cats.implicits._
import cats.effect.testing.scalatest.AsyncIOSpec
import org.http4s.syntax.literals.uri
import org.http4s._
import org.http4s.circe._
import io.circe._
import io.circe.literal.json
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import devconnected.application.connection.Connected
import devconnected.application.connection.NotConnected
import cats.data.NonEmptyList
import devconnected.application.error.InvalidGithubUserHandle
import devconnected.application.error.InvalidTwitterUserHandle
import devconnected.application.connection.GithubOrganisation
import devconnected.application.connection.UserHandle
import scala.concurrent.ExecutionContext

class DeveloperConnectionsApiSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {
  private val validRequest              = Request[IO](method = Method.GET, uri = uri"/developers/connected/a/b")
  implicit val global: ExecutionContext = scala.concurrent.ExecutionContext.global

  "connected developers call should" - {

    "return matching user gitub organisations when those are returned" in {
      val organisations = NonEmptyList.of(GithubOrganisation("org1"), GithubOrganisation("org2"))
      val api           = DeveloperConnectionsApi((_, _) => IO.delay(Connected(organisations)))

      api.routes.callExpectingJsonEntityResponse().map { (resp, body) =>
        resp.status shouldBe Status.Ok
        body shouldBe json"""{"connected":true,"organisations":["org1","org2"]}"""
      }
    }

    "return not matching users answer when they do not match" in {
      val api = DeveloperConnectionsApi((_, _) => IO.delay(NotConnected))

      api.routes.callExpectingJsonEntityResponse().map { (resp, body) =>
        resp.status shouldBe Status.Ok
        body shouldBe json"""{"connected":false}"""
      }
    }

    "return errors message when the developers connection check fails" in {
      val errors = NonEmptyList.of(
        InvalidGithubUserHandle(UserHandle("usr1")),
        InvalidTwitterUserHandle(UserHandle("usr2")),
        InvalidGithubUserHandle(UserHandle("usr2"))
      )
      val api = DeveloperConnectionsApi((_, _) => IO.delay(errors))

      api.routes.callExpectingJsonEntityResponse().map { (resp, body) =>
        resp.status shouldBe Status.Ok
        body shouldBe json"""{
        "errors": [
            "usr1 is no a valid user in github",
            "usr2 is no a valid user in twitter",
            "usr2 is no a valid user in github"
            ]
        }"""
      }
    }

    "fail when the developers connection check errors" in {
      val api = DeveloperConnectionsApi((_, _) => IO.raiseError(new Exception("boom!")))

      api.routes.callExpectingJsonEntityResponse().map { (resp, body) =>
        resp.status shouldBe Status.InternalServerError
        body shouldBe json"""{"errors": ["boom!"]}"""
      }
    }
  }

  extension (routes: HttpRoutes[IO]) {
    def callExpectingJsonEntityResponse(): IO[(Response[IO], Json)] = for {
      resp <- routes.orNotFound.run(validRequest)
      body <- resp.as[Json]
    } yield (resp, body)
  }
}
