package devconnected.application.connection

import cats.data.NonEmptyList

opaque type UserHandle = String

object UserHandle:
  def apply(s: String): UserHandle = s

opaque type UserId = String

object UserId:
  def apply(s: String): UserId = s

opaque type GithubOrganisation = String

object GithubOrganisation:
  def apply(s: String): GithubOrganisation = s

final case class DeveloperData(githubOrganisations: List[GithubOrganisation])

sealed trait ConnectionCheckResult
final case class Connected(githubOrganisations: NonEmptyList[GithubOrganisation]) extends ConnectionCheckResult
case object NotConnected                                                          extends ConnectionCheckResult
