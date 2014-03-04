package models

import play.api.libs.functional.syntax._ 
import play.api.libs.json._

case class Point(x: Int, y: Int)
case class Player(dataType: String, name: String, location: Point)
case class StartInfo(dataType: String, gridSize: Point, players: List[Player])