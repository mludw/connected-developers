package devconnected.twitter

import cats.effect.IO
import cats.implicits._
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import devconnected.application.connection.UserHandle
import devconnected.application.connection.UserId
import org.http4s.client.Client
import cats.effect.kernel.Resource
import org.http4s.circe._
import io.circe.syntax._
import io.circe.Encoder
import io.circe.Json
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

  "getFolowedUsers should" - {
    "call expected twitter endpoint url-encoding user handle" in {
      val call = new AtomicReference[Option[Request[IO]]](None)
      val httpClient =
        Client[IO](req => {
          call.set(req.some)
          Resource(
            Ok(Paginated(List.empty, None).asJson).map(_ -> IO.delay(()))
          )
        })

      new TwitterClient(httpClient).getFolowedUsers(UserId("zy/a b")).map { _ =>
        val actualCall = call.get.get
        actualCall.method shouldBe Method.GET
        actualCall.uri.renderString shouldBe "https://api.twitter.com/2/users/zy%2Fa%20b/following?max_results=100&user.fields=id"
      }
    }

    "respond with false if the user follows no one" in {
      val twitter = followingTwitterClient(Paginated(List.empty, nextPage = None), Map.empty)

      twitter.getFolowedUsers(userId1).map { resp =>
        resp shouldBe List.empty[UserId]
      }
    }

    "iterate through pages until there are no more pages left" in {
      val pages @ List(page1, page2, page3, page4, page5) =
        List(page("next-1"), page("next-2"), page("next-3"), page("next-4"), page(None))

      val twitter =
        followingTwitterClient(
          page1,
          Map(
            page1.nextPage.get -> page2,
            page2.nextPage.get -> page3,
            page3.nextPage.get -> page4,
            page4.nextPage.get -> page5
          )
        )

      twitter.getFolowedUsers(userId1).map { resp =>
        resp shouldBe pages.flatMap(_.followed)
      }
    }

    "return no user ids for unexpected response body" in { // this should be improved in real app, for this test this is enough
      val twitter = twitterClient(Ok(Json.arr(Json.obj("abc" -> "def".asJson))))

      twitter.getFolowedUsers(userId1).map { resp =>
        resp shouldBe List.empty[UserId]
      }
    }

    "fail for unauthorized response" in {
      val twitter = twitterClient(Response(status = Unauthorized))

      twitter.getFolowedUsers(userId1).attempt.map { resp =>
        resp.leftMap(_.getMessage) shouldBe Left("twitter responded with unexpected status: 401")
      }
    }

    "fail for Forbidden response status" in {
      val twitter = twitterClient(Response(status = Forbidden))

      twitter.getFolowedUsers(userId1).attempt.map { resp =>
        resp.leftMap(_.getMessage) shouldBe Left("twitter responded with unexpected status: 403")
      }
    }

    "fail for unexpected response status" in {
      val twitter = twitterClient(Response(status = BadRequest))

      twitter.getFolowedUsers(userId1).attempt.map { resp =>
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
