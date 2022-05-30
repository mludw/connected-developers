package devconnected.application

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
import cats.effect.IO
import devconnected.application.github.GithubApi.UserNotFound
import cats.effect.unsafe.implicits._
import scala.concurrent.duration._

class DeveloperConnectionsSpec extends AnyFreeSpec with Matchers with TableDrivenPropertyChecks {

  "should get user github organisations and compare them using ConnectionCheck" in new TestContext {
    val github = githubApi(userOrganisations = Map(handle1 -> List(org1), handle2 -> List(org2)))
    val cases = Table(
      ("checker results"),
      Connected(NonEmptyList.one(GithubOrganisation("org1"))),
      NotConnected
    )

    forAll(cases) { checkResult =>
      val connections = new DeveloperConnections(github, (_, _) => checkResult)

      val check = connections.checkConnection(handle1, handle2).unsafeRunSync()

      check shouldBe checkResult
    }
  }

  "call github api for the developers in parallel" in new TestContext {
    val cases = Table(
      ("user 1 delay", "user 2 delay", "second user"),
      (10.millis, 50.millis, handle2),
      (50.millis, 10.millis, handle1)
    )
    val lastUser = new AtomicReference[Option[UserHandle]](None)

    forAll(cases) { (user1Delay, user2Delay, expectedSecondUser) =>
      val github: GithubApi[IO] = {
        case `handle1` => IO.sleep(user1Delay).map(_ => lastUser.set(handle1.some)).as(List.empty)
        case `handle2` => IO.sleep(user2Delay).map(_ => lastUser.set(handle2.some)).as(List.empty)
      }

      val connections = new DeveloperConnections(github, (_, _) => NotConnected)

      connections.checkConnection(handle1, handle2).unsafeRunSync()

      lastUser.get shouldBe expectedSecondUser.some
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

    connections.checkConnection(handle1, handle2).unsafeRunSync()

    callParams.get shouldBe Some((dev1, dev2))
  }

  "checkConnection should fail if developer is not found by handle in github" in new TestContext {
    val cases = Table(
      ("user organisations", "missing user handles"),
      (Map.empty[UserHandle, List[GithubOrganisation]], NonEmptyList.of(handle1, handle2)),
      (Map(handle1 -> List(org1)), NonEmptyList.one(handle2)),
      (Map(handle2 -> List(org1)), NonEmptyList.one(handle1))
    )

    forAll(cases) { (userOrganisations, missingHandles) =>
      val github      = githubApi(userOrganisations = userOrganisations)
      val connections = new DeveloperConnections(github, (_, _) => NotConnected)

      val check = connections.checkConnection(handle1, handle2).unsafeRunSync()

      check shouldBe missingHandles.map(InvalidGithubUserHandle(_))
    }
  }

  private trait TestContext {
    val handle1 = UserHandle(randomUUID.toString)
    val handle2 = UserHandle(randomUUID.toString)
    val org1    = GithubOrganisation(randomUUID.toString)
    val org2    = GithubOrganisation(randomUUID.toString)

    def githubApi(userOrganisations: Map[UserHandle, List[GithubOrganisation]]): GithubApi[IO] =
      userHandle => IO.delay(userOrganisations.get(userHandle).getOrElse(UserNotFound(userHandle)))
  }
}
