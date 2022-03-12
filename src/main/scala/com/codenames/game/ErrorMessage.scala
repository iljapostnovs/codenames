package com.codenames.game

sealed trait ErrorMessage
object ErrorMessage {
  object Game {
    final case object GameNotFound extends ErrorMessage {
      override def toString: String = "Game not found"
    }
    final case object MultipleGamesJoining extends ErrorMessage {
      override def toString: String = "One player can't join multiple games at the same time"
    }
    final case object GameAlreadyStarted extends ErrorMessage {
      override def toString: String = "Game already started"
    }
    final case object GameAlreadyFinished extends ErrorMessage {
      override def toString: String = "Game already finished"
    }
  }
  object Player {
    final case object PlayerNotFound extends ErrorMessage {
      override def toString: String = "Player not found"
    }
    final case object PlayerMustJoinTeam extends ErrorMessage {
      override def toString: String = "Player must have a team"
    }
    object Captain {
      final case object ChooseTeamBeforeBecomingCaptain extends ErrorMessage {
        override def toString: String = "Player must choose a team before becoming a captain"
      }
      final case object OnlyOneCaptain extends ErrorMessage {
        override def toString: String = "There can be only one captain in the team"
      }
      final case object CaptainCantChooseCard extends ErrorMessage {
        override def toString: String = "Captain can't choose card"
      }
      final case object CaptainCantFinishMove extends ErrorMessage {
        override def toString: String = "Captains can't finish the move"
      }
    }
    object TeamMember {
      final case object NotTeamMemberTurn extends ErrorMessage {
        override def toString: String = "It's not team members turn"
      }
      final case object OnlyCaptainCanProvideWords extends ErrorMessage {
        override def toString: String = "Only captain can provide words"
      }
    }

  }
  object Card {
    final case object CardNotFound extends ErrorMessage {
      override def toString: String = "Card wasn't found"
    }
    final case object CardAlreadyRevealed extends ErrorMessage {
      override def toString: String = "Card already revealed"
    }
  }

  object State {
    final case object BecomeCaptainOnlyDuringTeamBuilding extends ErrorMessage {
      override def toString: String = "Player can become a captain only during team building"
    }
    final case object ChangeNameOnlyDuringTeamBuilding extends ErrorMessage {
      override def toString: String = "Player can change name only during team building"
    }
    final case object JoinOnlyDuringTeamBuilding extends ErrorMessage {
      override def toString: String = "Player can join team only during team building"
    }
    final case object AtLeastFourPlayers extends ErrorMessage {
      override def toString: String = "Can't start game with less than 4 players"
    }
    final case object AtLeastTwoCaptains extends ErrorMessage {
      override def toString: String = "There should be 2 captains before game starts"
    }
    final case object AllPlayersShouldHaveATeam extends ErrorMessage {
      override def toString: String = "All players should join a team"
    }
    final case object EveryTeamShouldHaveTeamMembers extends ErrorMessage {
      override def toString: String = "Every team should have at least one team member"
    }
    final case object TeamCanStartMoveOnlyAfterCaptain extends ErrorMessage {
      override def toString: String = "Team can start their move only after captain"
    }
    final case object NotCaptainsTurn extends ErrorMessage {
      override def toString: String = "Captain can provide word only in his turn"
    }

  }

  final case class Other(message: String) extends ErrorMessage {
    override def toString: String = message
  }
}
