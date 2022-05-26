package devconnected.application.connection

import cats.data.NonEmptyList

trait ConnectionChecker {
  def checkConnection(developer1: DeveloperData, developer2: DeveloperData): ConnectionCheck
}

object ConnectionChecker extends ConnectionChecker {
  override def checkConnection(developer1: DeveloperData, developer2: DeveloperData): ConnectionCheck =
    developer1.githubOrganisations.collect {
      case org if developer2.githubOrganisations.contains(org) => org
    } match {
      case t :: h => Connected(NonEmptyList(t, h))
      case _      => NotConnected
    }
}
