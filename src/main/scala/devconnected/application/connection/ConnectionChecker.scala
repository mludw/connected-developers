package devconnected.application.connection

import cats.data.NonEmptyList

trait ConnectionChecker {
  def checkConnection(developer1: DeveloperData, developer2: DeveloperData): ConnectionCheck
}

object ConnectionChecker extends ConnectionChecker {
  override def checkConnection(developer1: DeveloperData, developer2: DeveloperData): ConnectionCheck =
    developer1.githubGroups.collect {
      case group if developer2.githubGroups.contains(group) => group
    } match {
      case t :: h => Connected(NonEmptyList(t, h))
      case _      => NotConnected
    }
}
