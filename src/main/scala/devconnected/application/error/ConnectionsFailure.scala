package devconnected.application.error

import devconnected.application.connection.UserHandle

sealed trait ConnectionsFailure
final case class InvalidGithubUserHandle(handle: UserHandle)  extends ConnectionsFailure
final case class InvalidTwitterUserHandle(handle: UserHandle) extends ConnectionsFailure
