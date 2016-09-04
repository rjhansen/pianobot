package engineering.hansen.pianobot

import java.nio.file.{Paths, Files}
import java.io.PrintWriter
import java.io.FileOutputStream
import java.sql.DriverManager
import org.apache.logging.log4j.{LogManager, Logger}

object Utilities {
  private val logger = LogManager.getLogger(getClass)

  def initializeDB() : Unit = {
    try {
      Files.deleteIfExists(Paths.get(Environment.appdir + "pianobot.db"))
      val jdbcstr = "jdbc:sqlite:" + Environment.appdir + "pianobot.db"
      val connection = DriverManager.getConnection(jdbcstr)
      connection.createStatement().executeUpdate(
        """
          |CREATE TABLE people
          |  (
          |  id INTEGER PRIMARY KEY,
          |  nick TEXT UNIQUE NOT NULL,
          |  lastSeen TEXT
          |  )
        """.stripMargin
      )
      connection.createStatement().executeUpdate(
        """
          |CREATE TABLE capabilities
          |  (
          |  id INTEGER PRIMARY KEY,
          |  name TEXT UNIQUE NOT NULL
          |  )
        """.stripMargin
      )
      connection.createStatement().executeUpdate(
        """
          |CREATE TABLE capabilityMap
          |  (
          |  id INTEGER PRIMARY KEY,
          |  peopleID INTEGER NOT NULL,
          |  capabilityID INTEGER NOT NULL,
          |  FOREIGN KEY (peopleID) REFERENCES people(id),
          |  FOREIGN KEY (capabilityID) REFERENCES capabilities(id)
          |  UNIQUE(peopleID, capabilityID) ON CONFLICT REPLACE
          |  )
        """.stripMargin
      )
      connection.createStatement().executeUpdate(
        """
          |CREATE TABLE messages
          |  (
          |  id INTEGER PRIMARY KEY,
          |  fromID INTEGER NOT NULL,
          |  toID INTEGER NOT NULL,
          |  message TEXT NOT NULL,
          |  FOREIGN KEY (fromID) REFERENCES people(id),
          |  FOREIGN KEY (toID) REFERENCES people(id)
          |  )
        """.stripMargin
      )
      connection.createStatement().executeUpdate(
        """
          |CREATE TABLE songwriters
          |  (
          |  id INTEGER PRIMARY KEY,
          |  name TEXT NOT NULL UNIQUE
          |  )
        """.stripMargin
      )
      connection.createStatement().executeUpdate(
        """
          |CREATE TABLE songs
          |  (
          |  id INTEGER PRIMARY KEY,
          |  byID INTEGER NOT NULL,
          |  name TEXT NOT NULL,
          |  length INTEGER NOT NULL,
          |  UNIQUE(byID, name) ON CONFLICT REPLACE,
          |  FOREIGN KEY (byID) REFERENCES songwriters(id)
          |  )
        """.stripMargin
      )
      connection.createStatement().executeUpdate(
        """
          |INSERT INTO people (nick) VALUES ('Raubritter')
        """.stripMargin
      )
      connection.createStatement().executeUpdate(
        """
           |INSERT INTO capabilities (name) VALUES ('admin')
        """.stripMargin
      )
      connection.createStatement().executeUpdate(
        """
          |INSERT INTO songwriters (name) VALUES ('Claude Debussey')
        """.stripMargin
      )
      connection.createStatement().executeUpdate(
        """
          |INSERT INTO songs (byID, name, length)
          |SELECT songwriters.id, 'Clair de Lune', 342
          |FROM songwriters
          |WHERE songwriters.name = 'Claude Debussey'
        """.stripMargin
      )
      connection.createStatement().executeUpdate(
        """
          |INSERT INTO capabilityMap (peopleID, capabilityID)
          |SELECT people.id, capabilities.id
          |FROM people, capabilities
          |WHERE people.nick = 'Raubritter' AND capabilities.name = 'admin'
        """.stripMargin
      )
    } catch {
      case e : Throwable => logger.fatal("Could not initialize db: " + e.toString)
        System.exit(-1)
    }
  }
}
