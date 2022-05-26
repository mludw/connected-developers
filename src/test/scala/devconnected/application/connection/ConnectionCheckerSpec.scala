package devconnected.application.connection

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.matchers.should.Matchers
import cats.data.NonEmptyList
import org.scalatest.Inside

class ConnectionCheckerSpec extends AnyFreeSpec with Matchers with Inside with TableDrivenPropertyChecks {

  "checkConnection should respond with true for the developers with intersecting github groups" in new TestContext {
    val noGroupAssigned = Table(
      ("developer 1 github groups", "developer 2 github groups", "common groups"),
      (List(group1), List(group1), NonEmptyList.of(group1)),
      (List(group2, group1), List(group1, group2, group3), NonEmptyList.of(group1, group2)),
      (List(group2, group3, group1), List(group3), NonEmptyList.of(group3)),
      (List(group2, group3, group1), List(group2, group3), NonEmptyList.of(group2, group3)),
      (List(group2, group3, group1), List(group1, group3, group2), NonEmptyList.of(group1, group2, group3))
    )

    forAll(noGroupAssigned) { case (groups1, groups2, commonGroups) =>
      val developer1 = developer.copy(githubGroups = groups1)
      val developer2 = developer.copy(githubGroups = groups2)

      val check = ConnectionChecker.checkConnection(developer1, developer2)

      inside(check) { case Connected(connected) =>
        connected.toList should contain allElementsOf (commonGroups.toList)
      }
    }
  }

  "checkConnection should respond with false when the developer does not belong to any github group" in new TestContext {
    val noGroupAssigned = Table(
      ("developer 1 github groups", "developer 2 github groups"),
      List.empty[GithubGroup] -> List(group1),
      List(group1)            -> List.empty[GithubGroup],
      List.empty[GithubGroup] -> List.empty[GithubGroup]
    )

    forAll(noGroupAssigned) { case (groups1, groups2) =>
      val developer1 = developer.copy(githubGroups = groups1)
      val developer2 = developer.copy(githubGroups = groups2)

      ConnectionChecker.checkConnection(developer1, developer2) shouldBe NotConnected
    }
  }

  "checkConnection should respond with false for the users with different github groups" in new TestContext {
    val noGroupAssigned = Table(
      ("developer 1 github groups", "developer 2 github groups"),
      List(group1)         -> List(group2),
      List(group2, group1) -> List(group3),
      List(group1)         -> List(group2, group3),
      List(group1, group4) -> List(group2, group3)
    )

    forAll(noGroupAssigned) { case (groups1, groups2) =>
      val developer1 = developer.copy(githubGroups = groups1)
      val developer2 = developer.copy(githubGroups = groups2)

      ConnectionChecker.checkConnection(developer1, developer2) shouldBe NotConnected
    }
  }

  trait TestContext {
    val List(group1, group2, group3, group4) = List.tabulate(4)(i => GithubGroup(s"group-$i"))

    val developer = DeveloperData(githubGroups = List(group1, group2))
  }
}
