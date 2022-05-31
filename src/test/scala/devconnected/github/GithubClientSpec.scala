package devconnected.github

import cats.effect.IO
import cats.implicits._
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import devconnected.application.connection.UserHandle
import org.http4s.client.Client
import cats.effect.kernel.Resource
import devconnected.application.github.GithubApi.UserNotFound
import devconnected.application.connection.GithubOrganisation
import org.http4s.circe._
import io.circe.syntax._
import io.circe.Encoder
import io.circe.Json
import org.http4s._
import org.http4s.dsl.io._
import java.util.concurrent.atomic.AtomicReference

class GithubClientSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {
  import TestContext._

  "getOrganisations should call expected github endpoint url-encoding user handle" in {
    val call               = new AtomicReference[Option[Request[IO]]](None)
    val token: GithubToken = GithubToken("a-token")
    val httpClient =
      Client[IO](req => {
        call.set(req.some)
        Resource(
          Ok(List.empty[GithubOrganisation].asJson).map(_ -> IO.delay(()))
        )
      })

    new GithubClient(httpClient, token).getOrganisations(UserHandle("zy/a b")).map { _ =>
      val actualCall = call.get.get
      actualCall.method shouldBe Method.GET
      actualCall.uri.renderString shouldBe s"https://github.com/users/zy%2Fa%20b/orgs?page=1"
      actualCall.headers.headers.map(h => h.name.toString -> h.value) should contain allOf (
        "Authorization" -> s"Bearer $token",
        "Accept"        -> "application/vnd.github.v3+json"
      )
    }
  }

  "getOrganisations should respond with UserNotFound if the user does not exist in github" in {
    val github = getGithubClient(Response(Status.NotFound))

    github.getOrganisations(userHandle).map { resp =>
      resp shouldBe UserNotFound(userHandle)
    }
  }

  "getOrganisations should respond with empty groups if the user has no groups" in {
    val github = getGithubClient(Map(1 -> List.empty))

    github.getOrganisations(userHandle).map { resp =>
      resp shouldBe List.empty[GithubOrganisation]
    }
  }

  "getOrganisations should include the user organisations for all pages (keep building response until there is some group returned)" in {
    val page1 = List.tabulate(5)(i => GithubOrganisation(s"org-1$i"))
    val page2 = List.tabulate(5)(i => GithubOrganisation(s"org-2$i"))
    val page3 = List.tabulate(3)(i => GithubOrganisation(s"org-3$i"))

    val github = getGithubClient(Map(1 -> page1, 2 -> page2, 3 -> page3, 4 -> List.empty))

    github.getOrganisations(userHandle).map { resp =>
      resp shouldBe (page1 ++ page2 ++ page3)
    }
  }

  "getOrganisations should fail for invalid/unexpected response body for 200 OK" in {
    val github = getGithubClient(Ok(Json.arr(Json.obj("abc" -> "def".asJson))))

    github.getOrganisations(userHandle).attempt.map { resp =>
      resp.leftMap(_.getMessage) shouldBe Left("Unexpected github API response entity")
    }
  }

  "getOrganisations should fail for unauthorized response" in { // authentication issues are real internal errors - the app is misconfigured
    val github = getGithubClient(Response(status = Unauthorized))

    github.getOrganisations(userHandle).attempt.map { resp =>
      resp.leftMap(_.getMessage) shouldBe Left("github responded with unexpected status: 401")
    }
  }

  "getOrganisations should fail for Forbidden response status" in { // authentication issues are real internal errors - the app is misconfigured
    val github = getGithubClient(Response(status = Forbidden))

    github.getOrganisations(userHandle).attempt.map { resp =>
      resp.leftMap(_.getMessage) shouldBe Left("github responded with unexpected status: 403")
    }
  }

  "getOrganisations should fail for unexpected response status" in { // TODO I could translate github 500s to 502 here (or timeout to 504) - but will skip it consciously
    val github = getGithubClient(Response(status = BadRequest))

    github.getOrganisations(userHandle).attempt.map { resp =>
      resp.leftMap(_.getMessage) shouldBe Left("github responded with unexpected status: 400")
    }
  }

  object TestContext {
    implicit val githubOrganisationEncoder: Encoder[GithubOrganisation] =
      Encoder.instance { org => Json.obj("login" -> s"$org".asJson) }

    val theToken: GithubToken = GithubToken("some-token")

    def getGithubClient(response: Response[IO]) = {
      val httpClient = Client[IO](_ => Resource(IO.delay(response -> IO.delay(()))))
      new GithubClient(httpClient, theToken)
    }

    def getGithubClient(response: IO[Response[IO]]) = {
      val httpClient = Client[IO](_ => Resource(response.map(_ -> IO.delay(()))))
      new GithubClient(httpClient, theToken)
    }

    def getGithubClient(pages: Map[Int, List[GithubOrganisation]]) = {
      val httpClient =
        Client[IO](req =>
          Resource(
            Ok(pages(req.params("page").toInt).asJson).map(_ -> IO.delay(()))
          )
        )
      new GithubClient(httpClient, theToken)
    }

    val userHandle = UserHandle("abc")
  }
}
