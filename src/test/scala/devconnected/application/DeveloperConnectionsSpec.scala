package devconnected.application

import cats.syntax.option._
import devconnected.application.connection.ConnectionCheck
import devconnected.application.connection.GithubOrganisation
import devconnected.application.connection.UserHandle
import devconnected.application.connection.UserId
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
import devconnected.application.twitter.TwitterApi
import devconnected.application.error.InvalidTwitterUserHandle

class DeveloperConnectionsSpec extends AnyFreeSpec with Matchers with TableDrivenPropertyChecks {

  "checkConnection should" - {
    "get user github organisations and users followed on twitter and compare them using ConnectionCheck" in new TestContext {
      val cases = Table(
        ("checker results"),
        Connected(NonEmptyList.one(GithubOrganisation("org1"))),
        NotConnected
      )

      forAll(cases) { checkResult =>
        val connections = DeveloperConnections(dummyGithub, dummyTwitter, (_, _) => checkResult)

        val check = connections.checkConnection(handle1, handle2).unsafeRunSync()

        check shouldBe checkResult
      }
    }

    "pass expected parameters to connection check" in new TestContext {
      val callParams = new AtomicReference[Option[(DeveloperData, DeveloperData)]](None)
      val dev1       = DeveloperData(List(org1), randomUserId(), followsOnTwitter = List.fill(4)(randomUserId()))
      val dev2       = DeveloperData(List(org1, org2), randomUserId(), followsOnTwitter = List.fill(6)(randomUserId()))
      val github     = githubApi(Map(handle1 -> dev1.githubOrganisations, handle2 -> dev2.githubOrganisations))
      val twitter = twitterApi(
        handleToId = Map(handle1 -> dev1.twitterId, handle2 -> dev2.twitterId),
        following = Map(dev1.twitterId -> dev1.followsOnTwitter, dev2.twitterId -> dev2.followsOnTwitter)
      )
      val connectionCheck: ConnectionCheck = (d1, d2) => {
        callParams.set((d1, d2).some)
        NotConnected
      }
      val connections = DeveloperConnections(github, twitter, connectionCheck)

      connections.checkConnection(handle1, handle2).unsafeRunSync()

      callParams.get shouldBe Some((dev1, dev2))
    }

    "call github api for the developers in parallel" in new TestContext {
      val cases = Table(
        ("user 1 delay", "user 2 delay", "expected order"),
        (10.millis, 50.millis, List(handle1, handle2)),
        (50.millis, 10.millis, List(handle2, handle1))
      )

      forAll(cases) { (user1Delay, user2Delay, expectedOrder) =>
        val userCalls = new AtomicReference[List[UserHandle]](List.empty)
        val github: GithubApi[IO] = {
          case `handle1` => IO.sleep(user1Delay).map(_ => userCalls.updateAndGet(_ :+ handle1)).as(List.empty)
          case `handle2` => IO.sleep(user2Delay).map(_ => userCalls.updateAndGet(_ :+ handle2)).as(List.empty)
        }
        val connections = DeveloperConnections(github, dummyTwitter, (_, _) => NotConnected)

        connections.checkConnection(handle1, handle2).unsafeRunSync()

        userCalls.get shouldBe expectedOrder
      }
    }

    "call twitter api for the developers in parallel" in new TestContext {
      val cases = Table(
        ("user 1 delay", "user 2 delay", "expected order"),
        (10.millis, 50.millis, List(userId1, userId2)),
        (50.millis, 10.millis, List(userId2, userId1))
      )

      forAll(cases) { (user1Delay, user2Delay, expectedOrder) =>
        val getFollowedUsersCalls = new AtomicReference[List[UserId]](List.empty)
        val twitter: TwitterApi[IO] = new TwitterApi[IO] {
          override def getUserId(userHandle: UserHandle) = userHandle match {
            case `handle1` => IO.sleep(user1Delay).as(userId1.some)
            case `handle2` => IO.sleep(user2Delay).as(userId2.some)
          }

          override def getFolowedUsers(userId: UserId) =
            IO.delay(getFollowedUsersCalls.updateAndGet(_ :+ userId)).as(List.empty)
        }
        val connections = DeveloperConnections(dummyGithub, twitter, (_, _) => NotConnected)

        connections.checkConnection(handle1, handle2).unsafeRunSync()

        getFollowedUsersCalls.get shouldBe expectedOrder
      }
    }

    "fail if developer is not found by handle in github" in new TestContext {
      val cases = Table(
        ("user organisations", "missing user handles"),
        (Map.empty[UserHandle, List[GithubOrganisation]], NonEmptyList.of(handle1, handle2)),
        (Map(handle1 -> List(org1)), NonEmptyList.one(handle2)),
        (Map(handle2 -> List(org1)), NonEmptyList.one(handle1))
      )

      forAll(cases) { (userOrganisations, missingHandles) =>
        val github      = githubApi(userOrganisations = userOrganisations)
        val connections = DeveloperConnections(github, dummyTwitter, (_, _) => NotConnected)

        val check = connections.checkConnection(handle1, handle2).unsafeRunSync()

        check shouldBe missingHandles.map(InvalidGithubUserHandle(_))
      }
    }

    "fail if developer is not found by handle in twitter" in new TestContext {
      val cases = Table(
        ("twitter users", "missing user handles"),
        (Map.empty[UserHandle, UserId], NonEmptyList.of(handle1, handle2)),
        (Map(handle1 -> userId1), NonEmptyList.one(handle2)),
        (Map(handle2 -> userId2), NonEmptyList.one(handle1))
      )

      forAll(cases) { (handleToId, missingHandles) =>
        val twitter     = twitterApi(handleToId = handleToId, following = Map.empty)
        val connections = DeveloperConnections(dummyGithub, twitter, (_, _) => NotConnected)

        val check = connections.checkConnection(handle1, handle2).unsafeRunSync()

        check shouldBe missingHandles.map(InvalidTwitterUserHandle(_))
      }
    }
  }

  private trait TestContext {
    val handle1 = UserHandle(randomUUID.toString)
    val handle2 = UserHandle(randomUUID.toString)
    val userId1 = randomUserId()
    val userId2 = randomUserId()
    val org1    = GithubOrganisation(randomUUID.toString)
    val org2    = GithubOrganisation(randomUUID.toString)

    val dummyGithub: GithubApi[IO] = userHandle => IO.delay(List(GithubOrganisation("dummy-org")))

    val dummyTwitter: TwitterApi[IO] = new TwitterApi[IO] {
      def getUserId(userHandle: UserHandle) = IO.delay(userId1.some)
      def getFolowedUsers(userId: UserId)   = IO.delay(List(userId1, userId2))
    }

    def randomUserId() = UserId(randomUUID.toString)

    def githubApi(userOrganisations: Map[UserHandle, List[GithubOrganisation]]): GithubApi[IO] =
      userHandle => IO.delay(userOrganisations.get(userHandle).getOrElse(UserNotFound(userHandle)))

    def twitterApi(handleToId: Map[UserHandle, UserId], following: Map[UserId, List[UserId]]) = new TwitterApi[IO] {
      override def getUserId(userHandle: UserHandle) = IO.delay(handleToId.get(userHandle))
      override def getFolowedUsers(userId: UserId)   = IO.delay(following.getOrElse(userId, List.empty))
    }
  }
}
