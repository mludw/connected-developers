package devconnected.application

import cats.Id
import cats.syntax.option._
import devconnected.application.connection.ConnectionCheck
import devconnected.application.connection.GithubOrganisation
import devconnected.application.connection.UserHandle
import devconnected.application.error.InvalidGithubUserHandle
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import devconnected.application.github.GithubApi
import java.util.UUID.randomUUID
import devconnected.application.connection.ConnectionCheck
import devconnected.application.connection.NotConnected
import devconnected.application.connection.Connected
import cats.data.NonEmptyList
import devconnected.application.connection.DeveloperData
import java.util.concurrent.atomic.AtomicReference

class DeveloperConnectionsSpec extends AnyFreeSpec with Matchers with TableDrivenPropertyChecks {

  "should get user github organisations and compart them using ConnectionCheck" in new TestContext {
    val github = githubApi(userOrganisations = Map(handle1 -> List(org1), handle2 -> List(org2)))
    val cases = Table(
      ("checker results"),
      Connected(NonEmptyList.one(GithubOrganisation("org1"))),
      NotConnected
    )

    forAll(cases) { checkResult =>
      val connections = new DeveloperConnections(github, (_, _) => checkResult)

      val check = connections.checkConnection(handle1, handle2)

      check shouldBe Right(checkResult)
    }
  }

  "should pass expected parameters to connection check" in new TestContext {
    val callParams = new AtomicReference[Option[(DeveloperData, DeveloperData)]](None)
    val dev1       = DeveloperData(List(org1))
    val dev2       = DeveloperData(List(org1, org2))
    val github =
      githubApi(userOrganisations = Map(handle1 -> dev1.githubOrganisations, handle2 -> dev2.githubOrganisations))
    val connectionCheck: ConnectionCheck = (d1, d2) => {
      callParams.set((d1, d2).some)
      NotConnected
    }
    val connections = new DeveloperConnections(github, connectionCheck)

    val check = connections.checkConnection(handle1, handle2)

    callParams.get shouldBe Some((dev1, dev2))
  }

  "checkConnection should fail if developer is not found by handle in github" in new TestContext {
    val cases = Table(
      ("user organisations", "missing user handles"),
      (Map.empty[UserHandle, List[GithubOrganisation]], List(handle1, handle2)),
      (Map(handle1 -> List(org1)), List(handle2)),
      (Map(handle2 -> List(org1)), List(handle1))
    )

    forAll(cases) { (userOrganisations, missingHandles) =>
      val github      = githubApi(userOrganisations = userOrganisations)
      val connections = new DeveloperConnections(github, (_, _) => NotConnected)

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
