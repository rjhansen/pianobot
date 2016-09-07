package engineering.hansen.pianobot

import java.sql.{Connection, DriverManager, ResultSet, SQLException, Statement}
import java.nio.file.Paths

object App {
  def main(args : Array[String]): Unit = {
    Environment.initialize()
    SQLUtilities.populateMP3s()
  }
}
