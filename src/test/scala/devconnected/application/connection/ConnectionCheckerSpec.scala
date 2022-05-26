package devconnected.application.connection

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import cats.data.NonEmptyList
import org.scalatest.Inside

class ConnectionCheckerSpec extends AnyFreeSpec with Matchers with Inside with TableDrivenPropertyChecks {

  "checkConnection should respond with true for the developers with intersecting github organisations" in new TestContext {
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

      val check = ConnectionChecker.checkConnection(developer1, developer2)

      inside(check) { case Connected(connected) =>
        connected.toList should contain allElementsOf (commonOrgs.toList)
      }
    }
  }

  "checkConnection should respond with false when the developer does not belong to any github organisation" in new TestContext {
    val noOrganisationAssigned = Table(
      ("developer 1 github organisations", "developer 2 github organisations"),
      List.empty[GithubOrganisation] -> List(org1),
      List(org1)                     -> List.empty[GithubOrganisation],
      List.empty[GithubOrganisation] -> List.empty[GithubOrganisation]
    )

    forAll(noOrganisationAssigned) { case (org1, org2) =>
      val developer1 = developer.copy(githubOrganisations = org1)
      val developer2 = developer.copy(githubOrganisations = org2)

      ConnectionChecker.checkConnection(developer1, developer2) shouldBe NotConnected
    }
  }

  "checkConnection should respond with false for the users with different github organisations" in new TestContext {
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

      ConnectionChecker.checkConnection(developer1, developer2) shouldBe NotConnected
    }
  }

  private trait TestContext {
    val List(org1, org2, org3, org4) = List.tabulate(4)(i => GithubOrganisation(s"org-$i"))

    val developer = DeveloperData(githubOrganisations = List(org1, org2))
  }
}
