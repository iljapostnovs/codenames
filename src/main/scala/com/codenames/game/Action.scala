package com.codenames.game

import cats.data.Validated
import cats.effect.IO
import cats.effect.concurrent.Ref
import com.codenames.game.Card.CardId
import com.codenames.game.Game.GameId
import com.codenames.game.Player.PlayerId
import com.codenames.game.State.{CaptainThinking, TeamThinking}

import java.util.UUID

sealed trait PlayerUpdatable {
  protected def updatePlayer(
    gameId: GameId,
    playerId: PlayerId,
    validateGameAndPlayer: (Game, Player) => Either[ErrorMessage, Game],
    updatePlayer: Player => Player,
  )(implicit
    gamesRef: Ref[IO, Map[GameId, Game]]
  ): IO[Either[ErrorMessage, Game]] =
    for {
      games <- gamesRef.get
      gameWithUpdatedPlayerOrError <- {
        val errorOrAdjustedGame: Either[ErrorMessage, Game] = for {
          game          <- games.get(gameId).toRight(ErrorMessage.Game.GameNotFound)
          player        <- game.players.find(_.playerId == playerId).toRight(ErrorMessage.Player.PlayerNotFound)
          validatedGame <- validateGameAndPlayer(game, player)
          newPlayer               = updatePlayer(player)
          gameWithAdjustedPlayers = validatedGame.replacePlayer(newPlayer)
        } yield gameWithAdjustedPlayers

        errorOrAdjustedGame match {
          case error @ Left(_) => IO.pure(error)
          case Right(adjustedGame) =>
            gamesRef.update(_ + (adjustedGame.gameId -> adjustedGame)).as(Right(adjustedGame))
        }
      }
    } yield gameWithUpdatedPlayerOrError
}

sealed trait Action {
  def perform()(implicit
    gamesRef: Ref[IO, Map[GameId, Game]]
  ): IO[Either[ErrorMessage, Game]]
}
object Action {
  final case class CreateGame(gameId: GameId = UUID.randomUUID(), board: Option[List[Card]] = None) extends Action {
    def perform()(implicit
      gamesRef: Ref[IO, Map[GameId, Game]]
    ): IO[Either[ErrorMessage, Game]] = {
      for {
        validatedNewGame <- Game.create(gameId, board)
        gameOrError <- validatedNewGame match {
          case Validated.Valid(newGame) =>
            gamesRef.update(games => games + (newGame.gameId -> newGame)).as(Right(newGame))
          case Validated.Invalid(error) => IO.pure(Left(ErrorMessage.Other(error.toNonEmptyList.toList.mkString(", "))))
        }
      } yield gameOrError
    }
  }

  final case class JoinGame(gameId: UUID, playerId: PlayerId) extends Action {
    def perform()(implicit gamesRef: Ref[IO, Map[GameId, Game]]): IO[Either[ErrorMessage, Game]] = {
      for {
        games <- gamesRef.get
        gameWithNewPlayerOrError = createGameWithPlayer(playerId, games)
        _ <- addGameToGamesRef(gameWithNewPlayerOrError)
      } yield gameWithNewPlayerOrError
    }

    private def addGameToGamesRef(
      gameWithNewPlayerOrError: Either[ErrorMessage, Game]
    )(implicit
      gamesRef: Ref[IO, Map[GameId, Game]]
    ): IO[Unit] = {
      gameWithNewPlayerOrError.fold(
        _ => IO.unit,
        game => {
          gamesRef.update(games => games + (game.gameId -> game))
        },
      )
    }

    private def createGameWithPlayer(playerId: PlayerId, games: Map[GameId, Game]): Either[ErrorMessage, Game] = {
      val playerAlreadyExistsInAnotherGame = games.values.flatMap(_.players).exists(_.playerId == playerId)
      for {
        game <- games.get(gameId).toRight(ErrorMessage.Game.GameNotFound)
        _    <- Either.cond(!playerAlreadyExistsInAnotherGame, (), ErrorMessage.Game.MultipleGamesJoining)
        _    <- Either.cond(game.state == State.TeamBuilding(), (), ErrorMessage.State.JoinOnlyDuringTeamBuilding)
        joinedPlayer   = Player(playerId, PlayerName("Player"), None, None)
        gameWithPlayer = game.addPlayer(joinedPlayer)
      } yield gameWithPlayer
    }
  }

  final case class JoinTeam(
    gameId: GameId,
    team: Team,
    playerId: PlayerId,
  ) extends Action
      with PlayerUpdatable {
    def perform()(implicit
      gamesRef: Ref[IO, Map[GameId, Game]]
    ): IO[Either[ErrorMessage, Game]] = updatePlayer(
      gameId,
      playerId,
      (game, _) => Either.cond(game.state == State.TeamBuilding(), game, ErrorMessage.State.JoinOnlyDuringTeamBuilding),
      player => player.setTeam(Some(team)).setRole(Some(PlayerRole.TeamMember)),
    )
  }

  final case class ChangePlayerName(gameId: GameId, playerId: PlayerId, newName: PlayerName) extends Action with PlayerUpdatable {
    def perform()(implicit
      gamesRef: Ref[IO, Map[GameId, Game]]
    ): IO[Either[ErrorMessage, Game]] = for {
      gameWithRenamedPlayerOrError <- {
        for {
          gameWithAdjustedPlayers <- updatePlayer(
            gameId,
            playerId,
            (game, _) => Either.cond(game.state == State.TeamBuilding(), game, ErrorMessage.State.ChangeNameOnlyDuringTeamBuilding),
            player => player.setName(newName),
          )
        } yield gameWithAdjustedPlayers
      }
    } yield gameWithRenamedPlayerOrError
  }

  final case class BecomeCaptain(
    gameId: GameId,
    playerId: PlayerId,
  ) extends Action
      with PlayerUpdatable {
    def perform()(implicit
      gamesRef: Ref[IO, Map[GameId, Game]]
    ): IO[Either[ErrorMessage, Game]] = updatePlayer(
      gameId,
      playerId,
      (game, player) =>
        for {
          _ <- Either.cond(game.state == State.TeamBuilding(), (), ErrorMessage.State.BecomeCaptainOnlyDuringTeamBuilding)
          _ <- player.team.toRight(ErrorMessage.Player.Captain.ChooseTeamBeforeBecomingCaptain)
          captainAlreadyExists = game.players.filter(_.team == player.team).exists(_.role.fold(false)(_ == PlayerRole.Captain))
          _ <- Either.cond(!captainAlreadyExists, (), ErrorMessage.Player.Captain.OnlyOneCaptain)
        } yield game,
      player => player.setRole(Some(PlayerRole.Captain)),
    )
  }

  final case class StartGame(gameId: GameId) extends Action {
    def perform()(implicit ref: Ref[IO, Map[GameId, Game]]): IO[Either[ErrorMessage, Game]] =
      CaptainThinking(Team.BlueAgents).changeStateForGame(gameId)
  }

  final case class ProvideWord(gameId: GameId, playerId: PlayerId, word: CaptainWord, count: WordCount) extends Action {
    def perform()(implicit
      gamesRef: Ref[IO, Map[GameId, Game]]
    ): IO[Either[ErrorMessage, Game]] = {
      for {
        games <- gamesRef.get
        gameWithUpdatedPlayerOrError <- {
          val errorOrNewState: Either[ErrorMessage, TeamThinking] = for {
            game   <- games.get(gameId).toRight(ErrorMessage.Game.GameNotFound)
            player <- game.players.find(_.playerId == playerId).toRight(ErrorMessage.Player.PlayerNotFound)
            team   <- player.team.toRight(ErrorMessage.Player.PlayerMustJoinTeam)
            _      <- validate(game, player)
            state = State.TeamThinking(team)
          } yield state

          errorOrNewState match {
            case Left(error) => IO.pure(Left(error))
            case Right(teamThinkingState) =>
              for {
                gameOrError <- teamThinkingState.changeStateForGame(gameId)
                gameWithWordAndCount = gameOrError.map(_.setWord(Some(word), Some(count)))
                updatedGame <- gameWithWordAndCount.fold(
                  error => IO.pure(Left(error)),
                  game => gamesRef.update(_ + (game.gameId -> game)).as(Right(game)),
                )
              } yield updatedGame
          }
        }
      } yield gameWithUpdatedPlayerOrError
    }

    private def validate(game: Game, player: Player): Either[ErrorMessage, Game] = {
      val isPlayerACaptain = player.role.fold(false)(_ == PlayerRole.Captain)
      for {
        _ <- Either.cond(isPlayerACaptain, (), ErrorMessage.Player.TeamMember.OnlyCaptainCanProvideWords)
        isCaptainsTurn = game.state match {
          case CaptainThinking(team) => player.team.fold(false)(_ == team)
          case _                     => false
        }
        _ <- Either.cond(isCaptainsTurn, (), ErrorMessage.State.NotCaptainsTurn)
      } yield game
    }
  }

  final case class ChooseCard(gameId: GameId, playerId: PlayerId, cardId: CardId) extends Action {
    def perform()(implicit
      gamesRef: Ref[IO, Map[GameId, Game]]
    ): IO[Either[ErrorMessage, Game]] =
      for {
        games <- gamesRef.get
        updatedGame <- {
          val updatedGameOrErrors: Either[ErrorMessage, Game] = for {
            game   <- games.get(gameId).toRight(ErrorMessage.Game.GameNotFound)
            player <- game.players.find(_.playerId == playerId).toRight(ErrorMessage.Player.PlayerNotFound)
            team   <- player.team.toRight(ErrorMessage.Player.PlayerMustJoinTeam)
            card   <- game.board.find(_.cardId == cardId).toRight(ErrorMessage.Card.CardNotFound)
            _      <- validate(game, card, player)
            updatedGame = updateGame(game, card, player, team)
          } yield updatedGame

          updatedGameOrErrors match {
            case Left(error) => IO.pure(Left(error))
            case Right(game) =>
              gamesRef.update(_ + (game.gameId -> game)) *> IO.pure(Right(game))
          }
        }
      } yield updatedGame

    private def validate(game: Game, card: Card, player: Player): Either[ErrorMessage, Unit] = {
      val isPlayerATeamMember = player.role.fold(false)(_ == PlayerRole.TeamMember)
      for {
        _ <- Either.cond(isPlayerATeamMember, (), ErrorMessage.Player.Captain.CaptainCantChooseCard)
        _ <- Either.cond(!card.isRevealed, (), ErrorMessage.Card.CardAlreadyRevealed)
        itIsPlayersTurn = game.state match {
          case TeamThinking(team) => player.team.fold(false)(_ == team)
          case _                  => false
        }
        _ <- Either.cond(itIsPlayersTurn, (), ErrorMessage.Player.TeamMember.NotTeamMemberTurn)
      } yield ()
    }

    private def updateGame(game: Game, card: Card, player: Player, team: Team) = {
      val gameWithUpdatedScore = card.cardType match {
        case CardType.RedAgent  => game.increaseScore(Team.RedAgents)
        case CardType.BlueAgent => game.increaseScore(Team.BlueAgents)
        case _                  => game
      }
      val gameWithRevealedCard = gameWithUpdatedScore.revealCard(card)
      val gameWithNewState = card.cardType match {
        case CardType.BlueAgent =>
          if (player.team.fold(false)(_ == Team.BlueAgents)) gameWithRevealedCard
          else gameWithRevealedCard.setState(CaptainThinking(team.getNext)).setWord(None, None)

        case CardType.RedAgent =>
          if (player.team.fold(false)(_ == Team.RedAgents)) gameWithRevealedCard
          else gameWithRevealedCard.setState(CaptainThinking(team.getNext)).setWord(None, None)

        case CardType.InnocentBystander =>
          gameWithRevealedCard.setState(CaptainThinking(team.getNext)).setWord(None, None)
        case CardType.Assassin =>
          gameWithRevealedCard.setState(State.FinishedGame()).setWord(None, None).setTimerEnd(None)
      }

      val blueTeamCards         = gameWithNewState.board.filter(_.cardType == CardType.BlueAgent)
      val revealedBlueTeamCards = blueTeamCards.filter(_.isRevealed)

      val redTeamCards         = gameWithNewState.board.filter(_.cardType == CardType.RedAgent)
      val revealedRedTeamCards = redTeamCards.filter(_.isRevealed)

      val isGameFinished =
        blueTeamCards.length == revealedBlueTeamCards.length || redTeamCards.length == revealedRedTeamCards.length
      val maybeFinishedGame =
        if (isGameFinished)
          gameWithNewState.setState(State.FinishedGame()).setWord(None, None).setTimerEnd(None)
        else
          gameWithNewState

      maybeFinishedGame
    }
  }

  final case class FinishMove(gameId: GameId, playerId: PlayerId) extends Action {
    def perform()(implicit
      gamesRef: Ref[IO, Map[GameId, Game]]
    ): IO[Either[ErrorMessage, Game]] = {
      for {
        games <- gamesRef.get
        gameWithUpdatedPlayerOrError <- {
          val errorOrNewState: Either[ErrorMessage, CaptainThinking] = for {
            game   <- games.get(gameId).toRight(ErrorMessage.Game.GameNotFound)
            player <- game.players.find(_.playerId == playerId).toRight(ErrorMessage.Player.PlayerNotFound)
            team   <- player.team.toRight(ErrorMessage.Player.PlayerMustJoinTeam)
            _      <- validate(game, player)
            state = State.CaptainThinking(team.getNext)
          } yield state

          errorOrNewState match {
            case Left(error) => IO.pure(Left(error))
            case Right(captainThinkingState) =>
              captainThinkingState.changeStateForGame(gameId)
          }
        }
      } yield gameWithUpdatedPlayerOrError
    }

    private def validate(game: Game, player: Player): Either[ErrorMessage, Game] = {
      val isPlayerATeamMember = player.role.fold(false)(_ == PlayerRole.TeamMember)
      for {
        _ <- Either.cond(isPlayerATeamMember, (), ErrorMessage.Player.Captain.CaptainCantFinishMove)
        isPlayersTeamTurn = game.state match {
          case TeamThinking(team) => player.team.fold(false)(_ == team)
          case _                  => false
        }
        _ <- Either.cond(isPlayersTeamTurn, (), ErrorMessage.Player.TeamMember.NotTeamMemberTurn)
      } yield game
    }
  }
}
