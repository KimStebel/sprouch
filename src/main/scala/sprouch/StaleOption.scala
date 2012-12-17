package sprouch

sealed trait StaleOption

object StaleOption {
  case object notStale extends StaleOption
  case object ok extends StaleOption
  case object update_after extends StaleOption
}