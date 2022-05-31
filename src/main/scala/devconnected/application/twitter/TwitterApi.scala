package devconnected.application.twitter

import devconnected.application.connection.GithubOrganisation
import devconnected.application.connection.UserHandle
import devconnected.application.connection.UserId

trait TwitterApi[F[_]]:
  def getUserId(userHandle: UserHandle): F[Option[UserId]]
  def getFolowedUsers(userId: UserId): F[List[UserId]]
