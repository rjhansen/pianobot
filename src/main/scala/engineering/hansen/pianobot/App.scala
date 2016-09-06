package engineering.hansen.pianobot

import java.sql.{Connection, DriverManager, ResultSet, SQLException, Statement}
import java.nio.file.Paths

object App {
  def main(args : Array[String]): Unit = {
    Environment.initialize()
    Utilities.populateMP3s("/Users/rjh/Music/iTunes/iTunes Media/Music/Various Artists")
  }
}
