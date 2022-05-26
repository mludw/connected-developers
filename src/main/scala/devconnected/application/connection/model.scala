package devconnected.application.connection

import cats.data.NonEmptyList

opaque type GithubGroup = String

object GithubGroup:
  def apply(s: String): GithubGroup = s

final case class DeveloperData(githubGroups: List[GithubGroup])

sealed trait ConnectionCheck
final case class Connected(githubGroups: NonEmptyList[GithubGroup]) extends ConnectionCheck
case object NotConnected                                            extends ConnectionCheck
