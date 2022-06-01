package devconnected.twitter

opaque type TwitterToken = String

object TwitterToken:
  def apply(s: String): TwitterToken = s
