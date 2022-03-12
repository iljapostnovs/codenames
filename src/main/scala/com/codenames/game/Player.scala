package com.codenames.game

import com.codenames.game.Player.PlayerId

import java.util.UUID

final case class PlayerName(name: String) extends AnyVal

sealed trait PlayerRole
object PlayerRole {
  final case object TeamMember extends PlayerRole
  final case object Captain    extends PlayerRole
}

case class Player private (
  playerId: PlayerId,
  name: PlayerName,
  team: Option[Team],
  role: Option[PlayerRole],
) {
  def setTeam(team: Option[Team]): Player       = copy(team = team)
  def setRole(role: Option[PlayerRole]): Player = copy(role = role)
  def setName(name: PlayerName): Player         = copy(name = name)
}

object Player {
  type PlayerId = UUID
  def create(
    name: PlayerName
  ): Player = Player(UUID.randomUUID(), name, None, None)
}
