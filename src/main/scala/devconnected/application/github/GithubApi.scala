package devconnected.application.github

import devconnected.application.connection.GithubOrganisation
import devconnected.application.connection.UserHandle

trait GithubApi[F[_]]:
  def getOrganisations(userHandle: UserHandle): F[GithubApi.GetOrganisationsResponse]

object GithubApi:
  final case class UserNotFound(userHandle: UserHandle)
  type GetOrganisationsResponse = List[GithubOrganisation] | UserNotFound
