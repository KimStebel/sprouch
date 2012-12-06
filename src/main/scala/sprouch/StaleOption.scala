package sprouch

sealed trait StaleOption
case object notStale extends StaleOption
case object ok extends StaleOption
case object update_after extends StaleOption
