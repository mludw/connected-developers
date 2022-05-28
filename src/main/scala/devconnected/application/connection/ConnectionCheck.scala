package devconnected.application.connection

import cats.data.NonEmptyList

trait ConnectionCheck:
  def apply(developer1: DeveloperData, developer2: DeveloperData): ConnectionCheckResult

object ConnectionCheck extends ConnectionCheck:
  override def apply(developer1: DeveloperData, developer2: DeveloperData): ConnectionCheckResult =
    developer1.githubOrganisations.collect {
      case org if developer2.githubOrganisations.contains(org) => org
    } match {
      case t :: h => Connected(NonEmptyList(t, h))
      case _      => NotConnected
    }
