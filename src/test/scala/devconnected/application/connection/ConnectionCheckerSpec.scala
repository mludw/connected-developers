package devconnected.application.connection

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.matchers.should.Matchers

class ConnectionCheckerSpec extends AnyFreeSpec with Matchers with TableDrivenPropertyChecks {
  "checkConnection should respond with false when the developer does not belong to any group" in new TestContext {
    val noGroupAssigned = Table(
      ("developer 1 github groups", "developer 2 github groups"),
      (List.empty[GithubGroup], List(group1)),
      (List(group1), List.empty[GithubGroup]),
      (List.empty[GithubGroup], List.empty[GithubGroup])
    )

    forAll(noGroupAssigned) { case (groups1, groups2) =>
      val developer1 = developer.copy(githubGroups = groups1)
      val developer2 = developer.copy(githubGroups = groups2)

      ConnectionChecker.checkConnection(developer1, developer2) shouldBe NotConnected
    }
  }

  trait TestContext {
    val group1: GithubGroup = GithubGroup("group1")
    val group2: GithubGroup = GithubGroup("group2")

    val developer = DeveloperData(githubGroups = List(group1, group2))
  }
}
