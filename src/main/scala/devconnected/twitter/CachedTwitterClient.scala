package devconnected.twitter

import cats.implicits._
import devconnected.application.twitter.TwitterApi
import scalacache.caffeine._
import scalacache.serialization.binary._
import cats.effect.kernel.Sync
import devconnected.application.connection.UserHandle
import devconnected.application.connection.UserId
import scalacache._
import scalacache.caffeine._
import com.github.benmanes.caffeine.cache.Caffeine
import scala.concurrent.duration._

object CachedTwitterClient { // TODO test it (just did not want to use more time)

  def apply[F[_]](client: TwitterApi[F], maxSize: Long, ttl: Option[Duration])(using F: Sync[F]): F[TwitterApi[F]] =
    for {
      idsToHandles      <- F.delay(Caffeine.newBuilder().maximumSize(maxSize).build[UserHandle, Entry[Option[UserId]]])
      handlesToFollowed <- F.delay(Caffeine.newBuilder().maximumSize(maxSize).build[UserId, Entry[List[UserId]]])
    } yield {
      given Cache[F, UserHandle, Option[UserId]] = CaffeineCache(idsToHandles)
      given Cache[F, UserId, List[UserId]]       = CaffeineCache(handlesToFollowed)

      new TwitterApi[F] {
        override def getUserId(userHandle: UserHandle): F[Option[UserId]] =
          cachingF(userHandle)(ttl)(client.getUserId(userHandle))

        override def getFolowedUsers(userId: UserId): F[List[UserId]] =
          cachingF(userId)(ttl)(client.getFolowedUsers(userId))
      }
    }
}
