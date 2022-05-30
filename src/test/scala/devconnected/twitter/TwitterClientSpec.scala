package devconnected.twitter

import cats.effect.IO
import cats.implicits._
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicInteger
import devconnected.application.connection.UserHandle
import devconnected.application.connection.UserId
import org.http4s.client.Client
import cats.effect.kernel.Resource
import org.http4s.Response
import devconnected.application.github.GithubApi.UserNotFound
import scala.util.Failure
import org.http4s.circe._
import io.circe.syntax._
import io.circe.Encoder
import io.circe.Json

import io.circe._
import org.http4s._
import org.http4s.dsl.io._
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

class TwitterClientSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {
  import TestContext._

  "isFollowing should call expected twitter endpoint url-encoding user handle" in {
    val call = new AtomicReference[Option[Request[IO]]](None)
    val httpClient =
      Client[IO](req => {
        call.set(req.some)
        Resource(
          Ok(Paginated(List.empty, None).asJson).map(_ -> IO.delay(()))
        )
      })

    new TwitterClient(httpClient).isFollowing(UserId("zy/a b"), UserId("qwe")).map { _ =>
      val actualCall = call.get.get
      actualCall.method shouldBe Method.GET
      actualCall.uri.renderString shouldBe "https://api.twitter.com/2/users/zy%2Fa%20b/following?user.fields=id"
    }
  }

  "isFollowing should respond with false if the user follows no one" in {
    val github = getTwitterClient(Paginated(List.empty, nextPage = None), Map.empty)

    github.isFollowing(userId1, userId2).map { resp =>
      resp shouldBe false
    }
  }

  "isFollowing should iterate through pages until there are no more pages left" in {
    val github =
      getTwitterClient(
        page("next-1"),
        Map("next-1" -> page("next-2"), "next-2" -> page("next-3"), "next-3" -> page("next-4"), "next-3" -> page(None))
      )

    github.isFollowing(userId1, userId2).map { resp =>
      resp shouldBe false
    }
  }

  "isFollowing should stop iterating through pages when it finds matching id" in {
    val page3  = page("next-3")
    val github = getTwitterClient(page("next-1"), Map("next-1" -> page("next-2"), "next-2" -> page3))

    github.isFollowing(userId1, page3.followed.get(2).get).map { resp =>
      resp shouldBe true
    }
  }

  "isFollowing should return true if it finds matching id on the last page" in {
    val page4 = page(None)
    val github =
      getTwitterClient(
        page("next-1"),
        Map("next-1" -> page("next-2"), "next-2" -> page("next-3"), "next-3" -> page4)
      )

    github.isFollowing(userId1, page4.followed.get(3).get).map { resp =>
      resp shouldBe true
    }
  }

  "isFollowing should return false for unexpected response body" in { // this should be improved in real app, for this test this is enough
    val github = getTwitterClient(Ok(Json.arr(Json.obj("abc" -> "def".asJson))))

    github.isFollowing(userId1, userId2).map { resp =>
      resp shouldBe false
    }
  }

  "isFollowing should fail for unauthorized response" in {
    val github = getTwitterClient(Response(status = Unauthorized))

    github.isFollowing(userId1, userId2).attempt.map { resp =>
      resp.leftMap(_.getMessage) shouldBe Left("twitter responded with unexpected status: 401")
    }
  }

  "isFollowing should fail for Forbidden response status" in {
    val github = getTwitterClient(Response(status = Forbidden))

    github.isFollowing(userId1, userId2).attempt.map { resp =>
      resp.leftMap(_.getMessage) shouldBe Left("twitter responded with unexpected status: 403")
    }
  }

  "isFollowing should fail for unexpected response status" in {
    val github = getTwitterClient(Response(status = BadRequest))

    github.isFollowing(userId1, userId2).attempt.map { resp =>
      resp.leftMap(_.getMessage) shouldBe Left("twitter responded with unexpected status: 400")
    }
  }

  object TestContext {
    type Page = String
    case class Paginated(followed: List[UserId], nextPage: Option[Page])
    implicit val paginatedUserIdsEncoder: Encoder[Paginated] =
      Encoder.instance { case Paginated(ids, maybePage) =>
        Json.obj(
          "data" -> ids.map(id => Json.obj("id" -> s"$id".asJson)).asJson,
          "meta" -> maybePage.fold(Json.obj())(page => Json.obj("next_token" -> page.asJson))
        )
      }

    def getTwitterClient(response: Response[IO]) = {
      val httpClient = Client[IO](_ => Resource(IO.delay(response -> IO.delay(()))))
      new TwitterClient(httpClient)
    }

    def getTwitterClient(response: IO[Response[IO]]) = {
      val httpClient = Client[IO](_ => Resource(response.map(_ -> IO.delay(()))))
      new TwitterClient(httpClient)
    }

    def getTwitterClient(firstPage: Paginated, otherPages: Map[Page, Paginated]) = {
      val httpClient =
        Client[IO](req =>
          val paginatedUsersResp = req.params.get("pagination_token").fold(firstPage)(otherPages(_))
          Resource(
            Ok(paginatedUsersResp.asJson).map(_ -> IO.delay(()))
          )
        )
      new TwitterClient(httpClient)
    }

    val userId1 = UserId("abc")
    val userId2 = UserId("def")

    def page(next: String): Paginated         = page(next.some)
    def page(next: Option[String]): Paginated = Paginated(List.fill(5)(randomUserId), nextPage = next)

    def randomUserId = UserId(UUID.randomUUID.toString)
  }
}
