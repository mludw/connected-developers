package devconnected.application

import cats.Applicative
import cats.implicits._
import cats.effect.Concurrent
import cats.Parallel
import cats.data.Validated
import cats.data.NonEmptyList
import cats.data.Validated.Valid
import cats.data.Validated.Invalid
import devconnected.application.connection.ConnectionCheck
import devconnected.application.connection.ConnectionCheckResult
import devconnected.application.connection.GithubOrganisation
import devconnected.application.connection.UserHandle
import devconnected.application.connection.UserId
import devconnected.application.connection.DeveloperData
import devconnected.application.github.GithubApi
import devconnected.application.error.ConnectionsFailure
import devconnected.application.error.InvalidGithubUserHandle
import devconnected.application.error.InvalidTwitterUserHandle
import devconnected.application.twitter.TwitterApi
import devconnected.application.github.GithubApi.UserNotFound

class DeveloperConnections[F[_]: Concurrent: Parallel](
    github: GithubApi[F],
    twitter: TwitterApi[F],
    connectionCheck: ConnectionCheck
):

  def checkConnection(
      handle1: UserHandle,
      handle2: UserHandle
  ): F[NonEmptyList[ConnectionsFailure] | ConnectionCheckResult] =
    (github.getOrganisations(handle1), github.getOrganisations(handle2), getFollowed(handle1), getFollowed(handle2))
      .parMapN { case (maybeOrgs1, maybeOrgs2, followsOnTwitter1, followsOnTwitter2) =>
        (toValidated(maybeOrgs1), toValidated(maybeOrgs2), followsOnTwitter1, followsOnTwitter2)
          .mapN { case (githubUserOrgs1, githubUserOrgs2, twitterUser1, twitterUser2) =>
            val developer1 =
              DeveloperData(
                githubOrganisations = githubUserOrgs1,
                twitterId = twitterUser1.userId,
                followsOnTwitter = twitterUser1.follows
              )
            val developer2 = DeveloperData(
              githubOrganisations = githubUserOrgs2,
              twitterId = twitterUser2.userId,
              followsOnTwitter = twitterUser2.follows
            )
            connectionCheck(developer1, developer2)
          }
          .fold(identity, identity) // converts validated to flat list of errors OR successful connection check result
      }

  private def getFollowed(handle: UserHandle) = for {
    maybeId <- twitter.getUserId(handle)
    followed <- maybeId.fold(InvalidTwitterUserHandle(handle).invalidNel[TwitterData].pure[F])(id =>
      twitter.getFolowedUsers(id).map(TwitterData(id, _).validNel)
    )
  } yield followed

  private def toValidated
      : GithubApi.GetOrganisationsResponse => Validated[NonEmptyList[ConnectionsFailure], List[GithubOrganisation]] = {
    case orgs: List[GithubOrganisation] => orgs.validNel
    case UserNotFound(user)             => InvalidGithubUserHandle(user).invalidNel
  }

private case class TwitterData(userId: UserId, follows: List[UserId])
