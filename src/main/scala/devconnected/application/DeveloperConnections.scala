package devconnected.application

import cats.Applicative
import cats.implicits._
import devconnected.application.connection.ConnectionCheck
import devconnected.application.connection.ConnectionCheckResult
import devconnected.application.connection.GithubOrganisation
import devconnected.application.connection.UserHandle
import devconnected.application.github.GithubApi
import devconnected.application.error.ConnectionsFailure
import devconnected.application.error.InvalidGithubUserHandle
import devconnected.application.connection.DeveloperData
import cats.effect.Concurrent
import cats.Parallel
import cats.data.Validated
import devconnected.application.github.GithubApi.UserNotFound
import cats.data.NonEmptyList
import cats.data.Validated.Valid
import cats.data.Validated.Invalid

class DeveloperConnections[F[_]: Concurrent: Parallel](github: GithubApi[F], connectionCheck: ConnectionCheck):

  def checkConnection(
      handle1: UserHandle,
      handle2: UserHandle
  ): F[NonEmptyList[ConnectionsFailure] | ConnectionCheckResult] =
    (github.getOrganisations(handle1), github.getOrganisations(handle2))
      .parMapN { case (maybeOrgs1, maybeOrgs2) =>
        (toValidated(maybeOrgs1), toValidated(maybeOrgs2))
          .mapN { case (orgs1, orgs2) =>
            val developer1 = DeveloperData(orgs1)
            val developer2 = DeveloperData(orgs2)
            connectionCheck(developer1, developer2)
          }
          .fold(identity, identity) // converts validated to flat list of errors OR successful connection check result
      }

  private def toValidated
      : GithubApi.GetOrganisationsResponse => Validated[NonEmptyList[ConnectionsFailure], List[GithubOrganisation]] = {
    case orgs: List[GithubOrganisation] => orgs.validNel
    case UserNotFound(user)             => InvalidGithubUserHandle(user).invalidNel
  }

  object DeveloperConnections {
    type Errors = UserNotFound.type
  }
