package com.codenames.game

sealed trait Team {
  def getNext: Team = this match {
    case Team.BlueAgents => Team.RedAgents
    case Team.RedAgents  => Team.BlueAgents
  }
}
object Team {
  final case object BlueAgents extends Team
  final case object RedAgents  extends Team

  def from(team: String): Option[Team] = team match {
    case "BlueAgents" => Some(Team.BlueAgents)
    case "RedAgents"  => Some(Team.RedAgents)
    case _            => None
  }
}
