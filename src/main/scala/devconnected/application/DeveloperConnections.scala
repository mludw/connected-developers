package devconnected.application

import cats.Applicative
import cats.implicits._
import devconnected.application.connection.ConnectionCheck
import devconnected.application.connection.ConnectionCheckResult
import devconnected.application.connection.UserHandle
import devconnected.application.github.GithubApi
import devconnected.application.error.ConnectionsFailure
import devconnected.application.error.InvalidGithubUserHandle
import devconnected.application.connection.DeveloperData

class DeveloperConnections[F[_]: Applicative](github: GithubApi[F], connectionCheck: ConnectionCheck) {
  def checkConnection(
      handle1: UserHandle,
      handle2: UserHandle
  ): F[Either[List[ConnectionsFailure], ConnectionCheckResult]] =
    (github.getOrganisations(handle1), github.getOrganisations(handle2))
      .mapN { // TODO use parMapN or fs2 streams for parallelism?
        case (None, None) => List(handle1, handle2).map(InvalidGithubUserHandle(_)).asLeft
        case (None, _)    => List(InvalidGithubUserHandle(handle1)).asLeft
        case (_, None)    => List(InvalidGithubUserHandle(handle2)).asLeft
        case (Some(orgs1), Some(orgs2)) =>
          val developer1 = DeveloperData(orgs1)
          val developer2 = DeveloperData(orgs2)
          connectionCheck(developer1, developer2).asRight
      }
}
