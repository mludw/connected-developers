package devconnected.twitter

import cats.effect.Concurrent
import cats.effect.IO
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.traverse._
import devconnected.application.connection.UserHandle
import devconnected.application.connection.UserId
import devconnected.application.twitter.TwitterApi
import io.circe.generic.auto._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.ember.client._
import org.http4s.client._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.Method.GET
import org.http4s.Request
import org.http4s.syntax.all.uri
import org.http4s.Status
import org.http4s.Status.Successful
import org.http4s.Status.ClientError
import org.http4s.QueryParam

class TwitterClient[F[_]](httpClient: Client[F])(implicit F: Concurrent[F]) extends TwitterApi[F]:

  override def getUserId(userHandle: UserHandle): F[Option[UserId]] = ???

  override def isFollowing(follower: UserId, followed: UserId): F[Boolean] =
    fs2.Stream
      .iterateEval[F, Instruction](Init) {
        case Init =>
          getFollowed(follower)
        case WithNextPage(found, page) =>
          if (found.contains(followed)) LastPage(found).pure[F] else getFollowed(follower, nextPage = page.some)
        case last: LastPage =>
          last.pure[F]
      }
      .collect { case LastPage(found) =>
        found.contains(followed)
      }
      .take(1)
      .compile
      .lastOrError // this is not perfect but I expect the stream to always return something

  private def getFollowed(id: UserId, nextPage: Option[String] = None): F[Instruction] =
    httpClient.run(getFollowedRequest(id, nextPage)).use {
      case Successful(resp) =>
        resp
          .attemptAs[Users](jsonOf)
          .fold(
            _ => LastPage(found = List.empty), // the error handling here should be improved!
            {
              case Users(data, Meta(Some(nextToken))) => WithNextPage(data.map(toId), nextToken)
              case Users(data, Meta(_))               => LastPage(data.map(toId))
            }
          )
      case resp =>
        // seems like api returns 200 ok for unknown id (error handling is simplified here)
        F.raiseError(new Exception(s"twitter responded with unexpected status: ${resp.status.code}"))
    }

  private def toId(data: Data): UserId = UserId(data.id)

  private def getUserByHandleRequest(user: UserHandle) =
    Request[F](
      method = GET,
      uri = (uri"https://api.twitter.com/2/users/by/username" / s"$user")
    )

  private def getFollowedRequest(id: UserId, nextPage: Option[String]) =
    Request[F](
      method = GET,
      uri = (uri"https://api.twitter.com/2/users/" / s"$id" / "following")
        .withQueryParam("user.fields", "id")
        .withOptionQueryParam("pagination_token", nextPage)
    )

private sealed trait Instruction
private case object Init                                           extends Instruction
private case class LastPage(found: List[UserId])                   extends Instruction
private case class WithNextPage(found: List[UserId], page: String) extends Instruction

private final case class User(data: Data)
private final case class Users(data: List[Data], meta: Meta)
private final case class Meta(next_token: Option[String])
private final case class Data(id: String)
