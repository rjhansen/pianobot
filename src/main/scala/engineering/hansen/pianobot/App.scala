package engineering.hansen.pianobot

import java.sql.{Connection, DriverManager, ResultSet, SQLException, Statement}

object App {
  def main(args : Array[String]): Unit = {
    Environment.initialize()
  }
}
