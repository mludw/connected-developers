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

  "getUserId should" - {
    "call expected twitter endpoint url-encoding user handle" in {
      val call = new AtomicReference[Option[Request[IO]]](None)
      val httpClient =
        Client[IO](req => {
          call.set(req.some)
          Resource(
            Ok(Paginated(List.empty, None).asJson).map(_ -> IO.delay(()))
          )
        })

      new TwitterClient(httpClient).getUserId(UserHandle("ab/c")).map { _ =>
        val actualCall = call.get.get
        actualCall.method shouldBe Method.GET
        actualCall.uri.renderString shouldBe "https://api.twitter.com/2/users/by/username/ab%2Fc"
      }
    }

    "return user id when found" in {
      val twitter = twitterClientReturningId(userId2)

      twitter.getUserId(userHandle).map { resp =>
        resp shouldBe Some(userId2)
      }
    }

    "return None unexpected response body" in { // TODO error handling is simplified and should beimproved
      val twitter = twitterClient(Ok(Json.arr(Json.obj("abc" -> "def".asJson))))

      twitter.getUserId(userHandle).map { resp =>
        resp shouldBe None
      }
    }

    "fail for unauthorized response" in {
      val twitter = twitterClient(Response(status = Unauthorized))

      twitter.getUserId(userHandle).attempt.map { resp =>
        resp.leftMap(_.getMessage) shouldBe Left("twitter responded with unexpected status: 401")
      }
    }

    "fail for Forbidden response status" in {
      val twitter = twitterClient(Response(status = Forbidden))

      twitter.getUserId(userHandle).attempt.map { resp =>
        resp.leftMap(_.getMessage) shouldBe Left("twitter responded with unexpected status: 403")
      }
    }

    "fail for unexpected response status" in {
      val twitter = twitterClient(Response(status = BadRequest))

      twitter.getUserId(userHandle).attempt.map { resp =>
        resp.leftMap(_.getMessage) shouldBe Left("twitter responded with unexpected status: 400")
      }
    }
  }

  "isFollowing should" - {
    "call expected twitter endpoint url-encoding user handle" in {
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
        actualCall.uri.renderString shouldBe "https://api.twitter.com/2/users/zy%2Fa%20b/following?max_results=100&user.fields=id"
      }
    }

    "respond with false if the user follows no one" in {
      val twitter = followingTwitterClient(Paginated(List.empty, nextPage = None), Map.empty)

      twitter.isFollowing(userId1, userId2).map { resp =>
        resp shouldBe false
      }
    }

    "iterate through pages until there are no more pages left" in {
      val twitter =
        followingTwitterClient(
          page("next-1"),
          Map(
            "next-1" -> page("next-2"),
            "next-2" -> page("next-3"),
            "next-3" -> page("next-4"),
            "next-3" -> page(None)
          )
        )

      twitter.isFollowing(userId1, userId2).map { resp =>
        resp shouldBe false
      }
    }

    "stop iterating through pages when it finds matching id" in {
      val page3   = page("next-3")
      val twitter = followingTwitterClient(page("next-1"), Map("next-1" -> page("next-2"), "next-2" -> page3))

      twitter.isFollowing(userId1, page3.followed.get(2).get).map { resp =>
        resp shouldBe true
      }
    }

    "return true if it finds matching id on the last page" in {
      val page4 = page(None)
      val twitter =
        followingTwitterClient(
          page("next-1"),
          Map("next-1" -> page("next-2"), "next-2" -> page("next-3"), "next-3" -> page4)
        )

      twitter.isFollowing(userId1, page4.followed.get(3).get).map { resp =>
        resp shouldBe true
      }
    }

    "return false for unexpected response body" in { // this should be improved in real app, for this test this is enough
      val twitter = twitterClient(Ok(Json.arr(Json.obj("abc" -> "def".asJson))))

      twitter.isFollowing(userId1, userId2).map { resp =>
        resp shouldBe false
      }
    }

    "fail for unauthorized response" in {
      val twitter = twitterClient(Response(status = Unauthorized))

      twitter.isFollowing(userId1, userId2).attempt.map { resp =>
        resp.leftMap(_.getMessage) shouldBe Left("twitter responded with unexpected status: 401")
      }
    }

    "fail for Forbidden response status" in {
      val twitter = twitterClient(Response(status = Forbidden))

      twitter.isFollowing(userId1, userId2).attempt.map { resp =>
        resp.leftMap(_.getMessage) shouldBe Left("twitter responded with unexpected status: 403")
      }
    }

    "fail for unexpected response status" in {
      val twitter = twitterClient(Response(status = BadRequest))

      twitter.isFollowing(userId1, userId2).attempt.map { resp =>
        resp.leftMap(_.getMessage) shouldBe Left("twitter responded with unexpected status: 400")
      }
    }
  }

  object TestContext {
    type Page = String
    case class Paginated(followed: List[UserId], nextPage: Option[Page])

    implicit val userIdEncoder: Encoder[UserId] =
      Encoder.instance { id =>
        Json.obj("data" -> Json.obj("id" -> s"$id".asJson).asJson)
      }

    implicit val paginatedUserIdsEncoder: Encoder[Paginated] =
      Encoder.instance { case Paginated(ids, maybePage) =>
        Json.obj(
          "data" -> ids.map(id => Json.obj("id" -> s"$id".asJson)).asJson,
          "meta" -> maybePage.fold(Json.obj())(page => Json.obj("next_token" -> page.asJson))
        )
      }

    def twitterClient(response: Response[IO]) = {
      val httpClient = Client[IO](_ => Resource(IO.delay(response -> IO.delay(()))))
      new TwitterClient(httpClient)
    }

    def twitterClient(response: IO[Response[IO]]) = {
      val httpClient = Client[IO](_ => Resource(response.map(_ -> IO.delay(()))))
      new TwitterClient(httpClient)
    }

    def twitterClientReturningId(response: UserId) = TwitterClient(
      Client[IO](req => Resource(Ok(response.asJson).map(_ -> IO.delay(()))))
    )

    def followingTwitterClient(firstPage: Paginated, otherPages: Map[Page, Paginated]) = TwitterClient(
      Client[IO](req =>
        val paginatedUsersResp = req.params.get("pagination_token").fold(firstPage)(otherPages(_))
        Resource(
          Ok(paginatedUsersResp.asJson).map(_ -> IO.delay(()))
        )
      )
    )

    val userHandle = UserHandle("xyz")
    val userId1    = UserId("abc")
    val userId2    = UserId("def")

    def page(next: String): Paginated         = page(next.some)
    def page(next: Option[String]): Paginated = Paginated(List.fill(5)(randomUserId), nextPage = next)

    def randomUserId = UserId(UUID.randomUUID.toString)
  }
}
