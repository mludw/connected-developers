package devconnected.github

import cats.effect.IO
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.traverse._
import devconnected.application.connection.GithubOrganisation
import devconnected.application.connection.UserHandle
import devconnected.application.github.GithubApi
import org.http4s.client.Client
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.ember.client._
import org.http4s.client._
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.Request
import org.http4s.Method.GET
import org.http4s.syntax.all.uri
import org.http4s.Header
import org.http4s.circe._
import io.circe.generic.auto._
import org.http4s.Status
import org.http4s.Status.Successful
import org.http4s.Status.ClientError
import cats.effect.Concurrent
import org.typelevel.ci.CIStringSyntax
import org.http4s.QueryParam
import org.http4s.Credentials
import org.http4s.AuthScheme

class GithubClient[F[_]](httpClient: Client[F], token: GithubToken)(implicit F: Concurrent[F]) extends GithubApi[F]:

  override def getOrganisations(userHandle: UserHandle): F[GithubApi.GetOrganisationsResponse] = {
    val pages = for {
      pageIndex <- fs2.Stream.iterate(1)(_ + 1) // will call multiple pages until getting non empty groups
      orgsPage  <- httpClient.stream(getOrganisationsRequest(userHandle, pageIndex))
      maybeOrgs <- fs2.Stream.eval(orgsPage match {
        case Successful(resp) =>
          resp
            .attemptAs[List[Organisation]](jsonOf)
            .foldF(_ => F.raiseError(new Exception("Unexpected github API response entity")), _.some.pure)
        case ClientError(resp) if resp.status == Status.NotFound =>
          None.pure[F]
        case resp =>
          F.raiseError(new Exception(s"github responded with unexpected status: ${resp.status.code}"))
      })
    } yield maybeOrgs.map(_.map(o => GithubOrganisation(o.login)))

    pages
      .takeWhile(_.forall(_.nonEmpty))            // will stop fetching pages on the first empty page
      .takeWhile(_.isDefined, takeFailure = true) // should stop on 1st response with user not found and return None
      .compile
      .toList
      .map(_.flatSequence)
      .map {
        case Some(orgs) => orgs
        case _          => GithubApi.UserNotFound(userHandle)
      }
  }

  private def getOrganisationsRequest(user: UserHandle, page: Int = 1) =
    Request[F](
      method = GET,
      uri = (uri"https://github.com" / "users" / s"$user" / "orgs").withQueryParam("page", page.toString)
    )
      .withHeaders(
        Authorization(Credentials.Token(AuthScheme.Bearer, s"$token")),
        Header.Raw(ci"Accept", "application/vnd.github.v3+json")
      )

private final case class Organisation(login: String)
