package com.codenames.game

import cats.effect.IO
import cats.effect.concurrent.Ref
import com.codenames.game.Game.GameId

sealed trait State {
  def changeStateForGame(gameId: GameId)(implicit
    gamesRef: Ref[IO, Map[GameId, Game]]
  ): IO[Either[ErrorMessage, Game]] = for {
    games <- gamesRef.get
    gameWithNewStateOrError <- games.get(gameId) match {
      case Some(game) =>
        val gameWithNewState = game.setState(this)
        gamesRef.update(_ + (gameWithNewState.gameId -> gameWithNewState)).as(Right(gameWithNewState))

      case None => IO.pure(Left(ErrorMessage.Game.GameNotFound))
    }
  } yield gameWithNewStateOrError
}

object State {
  final case class TeamBuilding() extends State
  final case class CaptainThinking(team: Team) extends State {
    override def changeStateForGame(
      gameId: GameId
    )(implicit
      gamesRef: Ref[IO, Map[GameId, Game]]
    ): IO[Either[ErrorMessage, Game]] =
      for {
        games <- gamesRef.get
        gameWithNewStateOrError <- games.get(gameId) match {
          case Some(game) =>
            validateGame(game) match {
              case error @ Left(_) => IO.pure(error)
              case Right(_) =>
                val gameWithNoWord   = game.setWord(None, None)
                val gameWithNewState = gameWithNoWord.setState(this)

                gamesRef.update(_ + (gameWithNoWord.gameId -> gameWithNewState)).as(Right(gameWithNoWord))
            }

          case None => IO.pure(Left(ErrorMessage.Game.GameNotFound))
        }
      } yield gameWithNewStateOrError

    private def validateGame(game: Game): Either[ErrorMessage, Game] = {
      val gameStarts = game.state == TeamBuilding()

      if (gameStarts) {
        validatePlayerQuantity(game)
      } else {
        game.state match {
          case TeamBuilding()     => Right(game)
          case TeamThinking(_)    => Right(game)
          case CaptainThinking(_) => Left(ErrorMessage.Game.GameAlreadyStarted)
          case FinishedGame()     => Left(ErrorMessage.Game.GameAlreadyFinished)
        }
      }
    }
  }

  def validatePlayerQuantity(game: Game): Either[ErrorMessage, Game] = {
    val thereAreAtLeastTwoCaptains: Boolean = game.players.count(_.role match {
      case Some(role) => role == PlayerRole.Captain
      case None       => false
    }) == 2
    val thereAreAtLeastFourPlayers = game.players.length >= 4
    val notAllPlayersJoinedTeam    = game.players.exists(_.team.isEmpty)
    val teamMemberCount: Map[Team, Int] =
      game.players
        .filter(_.role.fold(false)(_ == PlayerRole.TeamMember))
        .foldLeft(Map[Team, Int](Team.BlueAgents -> 0, Team.RedAgents -> 0))((accumulator, player) => {
          player.team.fold(accumulator)(team => {
            accumulator
              .get(team)
              .fold(accumulator + (team -> 1))(count => {
                accumulator + (team -> (count + 1))
              })
          })
        })
    val allTeamsHaveAtLeastOneTeamMember = teamMemberCount.toList.forall(_._2 > 0)

    if (!thereAreAtLeastFourPlayers) {
      Left(ErrorMessage.State.AtLeastFourPlayers)
    } else if (!thereAreAtLeastTwoCaptains) {
      Left(ErrorMessage.State.AtLeastTwoCaptains)
    } else if (notAllPlayersJoinedTeam) {
      Left(ErrorMessage.State.AllPlayersShouldHaveATeam)
    } else if (!allTeamsHaveAtLeastOneTeamMember) {
      Left(ErrorMessage.State.EveryTeamShouldHaveTeamMembers)
    } else {
      Right(game)
    }
  }

  final case class TeamThinking(team: Team) extends State {
    override def changeStateForGame(
      gameId: GameId
    )(implicit
      gamesRef: Ref[IO, Map[GameId, Game]]
    ): IO[Either[ErrorMessage, Game]] = for {
      games <- gamesRef.get
      gameWithNewStateOrError <- games.get(gameId) match {
        case Some(game) =>
          validateGame(game) match {
            case error @ Left(_) => IO.pure(error)
            case Right(_) =>
              val gameWithNewState = game.setState(this)

              gamesRef.update(_ + (gameWithNewState.gameId -> gameWithNewState)).as(Right(gameWithNewState))
          }

        case None => IO.pure(Left(ErrorMessage.Game.GameNotFound))
      }
    } yield gameWithNewStateOrError

    private def validateGame(game: Game): Either[ErrorMessage, Game] = {
      val previousStateWasCaptainState = game.state match {
        case CaptainThinking(_) => true
        case _                  => false
      }

      Either.cond(previousStateWasCaptainState, game, ErrorMessage.State.TeamCanStartMoveOnlyAfterCaptain)
    }
  }
  final case class FinishedGame() extends State
}
