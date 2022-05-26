package devconnected.application

import cats.Id
import devconnected.application.connection.ConnectionCheck
import devconnected.application.connection.GithubOrganisation
import devconnected.application.connection.UserHandle
import devconnected.application.error.InvalidGithubUserHandle
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import devconnected.application.github.GithubApi
import java.util.UUID.randomUUID

class DeveloperConnectionsSpec extends AnyFreeSpec with Matchers with TableDrivenPropertyChecks {

  "checkConnection should fail if developer is not found by handle in github" in new TestContext {
    val cases = Table(
      ("user organisations", "missing user handles"),
      (Map.empty[UserHandle, List[GithubOrganisation]], List(handle1, handle2)),
      (Map(handle1 -> List(org1)), List(handle2)),
      (Map(handle2 -> List(org1)), List(handle1))
    )

    forAll(cases) { (userOrganisations, missingHandles) =>
      val github      = githubApi(userOrganisations = userOrganisations)
      val connections = new DeveloperConnections(github)

      val check = connections.checkConnection(handle1, handle2)

      check shouldBe Left(missingHandles.map(InvalidGithubUserHandle(_)))
    }
  }

  private trait TestContext {
    val handle1 = UserHandle(randomUUID.toString)
    val handle2 = UserHandle(randomUUID.toString)
    val org1    = GithubOrganisation(randomUUID.toString)
    val org2    = GithubOrganisation(randomUUID.toString)

    def githubApi(userOrganisations: Map[UserHandle, List[GithubOrganisation]]): GithubApi[Id] =
      userHandle => userOrganisations.get(userHandle)
  }
}
