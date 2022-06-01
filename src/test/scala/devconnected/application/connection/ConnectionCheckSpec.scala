package devconnected.application.connection

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import cats.data.NonEmptyList
import org.scalatest.Inside

class ConnectionCheckSpec extends AnyFreeSpec with Matchers with Inside with TableDrivenPropertyChecks {

  "should respond with true for the developers with intersecting github organisations (and following each other)" in new TestContext {
    val withMatchingOrganisations = Table(
      ("developer 1 github organisations", "developer 2 github organisations", "common organisations"),
      (List(org1), List(org1), NonEmptyList.of(org1)),
      (List(org2, org1), List(org1, org2, org3), NonEmptyList.of(org1, org2)),
      (List(org2, org3, org1), List(org3), NonEmptyList.of(org3)),
      (List(org2, org3, org1), List(org2, org3), NonEmptyList.of(org2, org3)),
      (List(org2, org3, org1), List(org1, org3, org2), NonEmptyList.of(org1, org2, org3))
    )

    forAll(withMatchingOrganisations) { case (org1, org2, commonOrgs) =>
      val developer1 = developer.copy(githubOrganisations = org1)
      val developer2 = developer.copy(githubOrganisations = org2)

      val checkResult = ConnectionCheck.apply(developer1, developer2)

      inside(checkResult) { case Connected(connected) =>
        connected.toList should contain allElementsOf (commonOrgs.toList)
      }
    }
  }

  "should respond with true for the developers following each other (and with intersecting github organisations)" in new TestContext {
    val withMatchingOrganisations = Table(
      ("developer 1 followed users", "developer 2 followed users"),
      (List(id2, id3, id4), List(id1)),
      (List(id3, id4, id2), List(id3, id4, id1)),
      (List(id2), List(id1))
    )

    forAll(withMatchingOrganisations) { case (followed1, followed2) =>
      val developer1 = developer.copy(twitterId = id1, followsOnTwitter = followed1)
      val developer2 = developer.copy(twitterId = id2, followsOnTwitter = followed2)

      val checkResult = ConnectionCheck.apply(developer1, developer2)

      inside(checkResult) { case Connected(connected) =>
        connected.toList should contain allElementsOf (developer.githubOrganisations)
      }
    }
  }

  "should respond with false when the developer does not belong to any github organisation" in new TestContext {
    val noOrganisationAssigned = Table(
      ("developer 1 github organisations", "developer 2 github organisations"),
      List.empty[GithubOrganisation] -> List(org1),
      List(org1)                     -> List.empty[GithubOrganisation],
      List.empty[GithubOrganisation] -> List.empty[GithubOrganisation]
    )

    forAll(noOrganisationAssigned) { case (org1, org2) =>
      val developer1 = developer.copy(githubOrganisations = org1)
      val developer2 = developer.copy(githubOrganisations = org2)

      ConnectionCheck.apply(developer1, developer2) shouldBe NotConnected
    }
  }

  "should respond with false for the users with different github organisations" in new TestContext {
    val withoutMatchingOrganisations = Table(
      ("developer 1 github organisations", "developer 2 github organisations"),
      List(org1)       -> List(org2),
      List(org2, org1) -> List(org3),
      List(org1)       -> List(org2, org3),
      List(org1, org4) -> List(org2, org3)
    )

    forAll(withoutMatchingOrganisations) { case (org1, org2) =>
      val developer1 = developer.copy(githubOrganisations = org1)
      val developer2 = developer.copy(githubOrganisations = org2)

      ConnectionCheck.apply(developer1, developer2) shouldBe NotConnected
    }
  }

  "should respond with false when the developer does not follow anybody" in new TestContext {
    val noOrganisationAssigned = Table(
      ("followed by developer 1", "followed by developer 2"),
      List.empty[UserId] -> List(id1),
      List(id2)          -> List.empty[UserId],
      List.empty[UserId] -> List.empty[UserId]
    )

    forAll(noOrganisationAssigned) { case (followedBy1, followedBy2) =>
      val developer1 = developer.copy(twitterId = id1, followsOnTwitter = followedBy1)
      val developer2 = developer.copy(twitterId = id2, followsOnTwitter = followedBy2)

      ConnectionCheck.apply(developer1, developer2) shouldBe NotConnected
    }
  }

  "should respond with false for the users that do not follow each other" in new TestContext {
    val withoutMatchingOrganisations = Table(
      ("followed by developer 1", "followed by developer 2"),
      List(id1)      -> List(id1),
      List(id3, id4) -> List(id1),
      List(id2, id3) -> List(id3, id4),
      List(id4)      -> List(id4)
    )

    forAll(withoutMatchingOrganisations) { case (followedBy1, followedBy2) =>
      val developer1 = developer.copy(twitterId = id1, followsOnTwitter = followedBy1)
      val developer2 = developer.copy(twitterId = id2, followsOnTwitter = followedBy2)

      ConnectionCheck.apply(developer1, developer2) shouldBe NotConnected
    }
  }

  private trait TestContext {
    val List(org1, org2, org3, org4) = List.tabulate(4)(i => GithubOrganisation(s"org-$i"))
    val List(id1, id2, id3, id4)     = List.tabulate(4)(i => UserId(s"id-$i"))

    val developer =
      DeveloperData(githubOrganisations = List(org1, org2), twitterId = id1, followsOnTwitter = List(id1))
  }
}
