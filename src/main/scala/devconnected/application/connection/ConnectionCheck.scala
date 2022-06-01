package devconnected.application.connection

import cats.data.NonEmptyList

trait ConnectionCheck:
  def apply(developer1: DeveloperData, developer2: DeveloperData): ConnectionCheckResult

object ConnectionCheck extends ConnectionCheck:
  override def apply(developer1: DeveloperData, developer2: DeveloperData): ConnectionCheckResult =
    if (!followEachOther(developer1, developer2)) then NotConnected
    else
      developer1.githubOrganisations.collect {
        case org if developer2.githubOrganisations.contains(org) => org
      } match {
        case t :: h => Connected(NonEmptyList(t, h))
        case _      => NotConnected
      }

  private def followEachOther(dev1: DeveloperData, dev2: DeveloperData): Boolean =
    dev1.followsOnTwitter.contains(dev2.twitterId) && dev2.followsOnTwitter.contains(dev1.twitterId)
