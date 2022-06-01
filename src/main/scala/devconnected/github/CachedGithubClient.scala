package devconnected.github

import cats.implicits._
import devconnected.application.github.GithubApi
import scalacache.caffeine._
import scalacache.serialization.binary._
import cats.effect.kernel.Sync
import devconnected.application.connection.UserHandle
import devconnected.application.connection.UserId
import scalacache._
import scalacache.caffeine._
import com.github.benmanes.caffeine.cache.Caffeine
import scala.concurrent.duration.Duration
import cats.implicits
import devconnected.application.connection.GithubOrganisation
import devconnected.application.github.GithubApi.UserNotFound

object CachedGithubClient { // TODO test it (just did not want to use more time)

  def apply[F[_]](client: GithubApi[F], maxSize: Long, ttl: Option[Duration])(using F: Sync[F]): F[GithubApi[F]] =
    F.delay(
      Caffeine.newBuilder().maximumSize(maxSize).build[UserHandle, Entry[List[GithubOrganisation] | UserNotFound]]
    ).map(organisationsByHandle =>

      given Cache[F, UserHandle, List[GithubOrganisation] | UserNotFound] = CaffeineCache(organisationsByHandle)

      new GithubApi[F] {
        def getOrganisations(userHandle: UserHandle): F[GithubApi.GetOrganisationsResponse] =
          cachingF(userHandle)(ttl)(client.getOrganisations(userHandle))
      }
    )
}
