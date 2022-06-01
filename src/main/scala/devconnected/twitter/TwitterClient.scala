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
import org.http4s.Credentials
import org.http4s.AuthScheme
import org.http4s.Response

class TwitterClient[F[_]](httpClient: Client[F], token: TwitterToken)(implicit F: Concurrent[F]) extends TwitterApi[F]:

  override def getUserId(userHandle: UserHandle): F[Option[UserId]] =
    httpClient.run(getUserByHandleRequest(userHandle)).use {
      case Successful(resp) =>
        resp
          .attemptAs[User](jsonOf)
          .fold(
            _ => None,
            u => UserId(u.data.id).some
          )
      case resp => raiseError(resp)
    }

  override def getFolowedUsers(userId: UserId): F[List[UserId]] =
    fs2.Stream
      .iterateEval[F, Init | Paginated](()) {
        case ()                     => getFollowed(userId, List.empty)
        case Paginated(found, page) => getFollowed(userId, found, nextPage = page)
      }
      .collect { case Paginated(found, None) => found }
      .take(1)
      .compile
      .last
      .map(_.getOrElse(List.empty)) // we could fail the call as well here with some error

  private def getFollowed(id: UserId, found: List[UserId], nextPage: Option[String] = None): F[Init | Paginated] =
    httpClient.run(getFollowedRequest(id, nextPage)).use {
      case Successful(resp) =>
        resp
          .attemptAs[Users](jsonOf)
          .fold( // assumes unexpected error is caused by missing user, this should be improved
            _ => Paginated(found = List.empty, nextPage = None),
            { case Users(data, Meta(maybeNextToken)) =>
              Paginated(found ++ data.map(toId), maybeNextToken)
            }
          )
      case resp => raiseError(resp)
      // it seems that api returns 200 ok for unknown id (error handling is simplified here)
    }

  private def raiseError[A](resp: Response[F]) = F.raiseError[A](
    new Exception(s"twitter responded with unexpected status: ${resp.status.code} ${resp.status.reason}")
  )

  private def toId(data: Data): UserId = UserId(data.id)

  private def getUserByHandleRequest(user: UserHandle) =
    Request[F](
      method = GET,
      uri = (uri"https://api.twitter.com/2/users/by/username" / s"$user")
    ).withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, s"$token")))

  private def getFollowedRequest(id: UserId, nextPage: Option[String]) =
    Request[F](
      method = GET,
      uri = (uri"https://api.twitter.com/2/users/" / s"$id" / "following")
        .withQueryParam("max_results", "100")
        .withQueryParam("user.fields", "id")
        .withOptionQueryParam("pagination_token", nextPage)
    ).withHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, s"$token")))

private type Init = Unit
private case class Paginated(found: List[UserId], nextPage: Option[String])

private final case class User(data: Data)
private final case class Users(data: List[Data], meta: Meta)
private final case class Meta(next_token: Option[String])
private final case class Data(id: String)
