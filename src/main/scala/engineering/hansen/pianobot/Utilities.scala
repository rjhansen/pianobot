package engineering.hansen.pianobot

import java.nio.file.{Files, Paths}
import java.sql.{DriverManager, PreparedStatement}
import org.apache.logging.log4j.LogManager
import scala.collection.JavaConverters._
import com.mpatric.mp3agic.{ID3v1, ID3v2, Mp3File}

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
      logger.info("created new table 'people'")
      connection.createStatement().executeUpdate(
        """
          |CREATE TABLE capabilities
          |  (
          |  id INTEGER PRIMARY KEY,
          |  name TEXT UNIQUE NOT NULL
          |  )
        """.stripMargin
      )
      logger.info("created new table 'capabilities'")
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
      logger.info("created new table 'capabilityMap'")
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
      logger.info("created new table 'messages'")
      connection.createStatement().executeUpdate(
        """
          |CREATE TABLE songwriters
          |  (
          |  id INTEGER PRIMARY KEY,
          |  name TEXT NOT NULL UNIQUE
          |  )
        """.stripMargin
      )
      logger.info("created new table 'songwriters'")
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
      logger.info("created new table 'songs'")
      connection.createStatement().executeUpdate(
        "INSERT INTO people (nick) VALUES ('" + Environment.options("admin") + "')")
      logger.info("inserted admin into people")

      connection.createStatement().executeUpdate(
        """INSERT INTO capabilities (name) VALUES ('admin')""".stripMargin
      )
      logger.info("inserted admin capability")
      connection.createStatement().executeUpdate(
        """INSERT INTO songwriters (name) VALUES ('Claude Debussy')""".stripMargin
      )
      logger.info("added Claude Debussy")
      connection.createStatement().executeUpdate(
        """
          |INSERT INTO songs (byID, name, length)
          |SELECT songwriters.id, 'Clair de Lune', 342
          |FROM songwriters
          |WHERE songwriters.name = 'Claude Debussy'
        """.stripMargin
      )
      logger.info("added Clair de Lune")
      connection.createStatement().executeUpdate(
        """
          |INSERT INTO capabilityMap (peopleID, capabilityID)
          |SELECT people.id, capabilities.id
          |FROM people, capabilities
          |WHERE people.nick = '""".stripMargin + Environment.options("admin") +
          "' AND capabilities.name = 'admin'"
      )
      logger.info("gave the admin the admin bit")
      connection.close()
    } catch {
      case e: Throwable => logger.fatal("Could not initialize db: " + e.toString)
        System.exit(1)
    }
  }

  private def makeMP3Maps(root_dir: String) = {
    def enumerateMP3s(dir: String): Array[String] = {
      val mp3s = (for (i <- Files.newDirectoryStream(Paths.get(dir)).asScala
                       if Files.isRegularFile(i) && Files.isReadable(i) && i.toAbsolutePath.toString.endsWith("mp3"))
        yield i.toAbsolutePath.toString).toArray
      val dirs = (for (i <- Files.newDirectoryStream(Paths.get(dir)).asScala
                       if Files.isDirectory(i) && Files.isReadable(i))
        yield i.toAbsolutePath.toString).toArray
      mp3s ++ dirs.flatMap(enumerateMP3s)
    }
    val mp3files: Array[(String, String, Int)] = enumerateMP3s(root_dir).flatMap((fn: String) => {
      val mp3obj = new Mp3File(fn)
      val length = mp3obj.getLengthInSeconds.toInt
      if (mp3obj.hasId3v2Tag) {
        val tagobj = mp3obj.getId3v2Tag
        val artist = tagobj.getArtist
        val songname = tagobj.getTitle
        (artist == null) || (songname == null) match {
          case true => None
          case false => Some((artist, songname, length))
        }
      } else if (mp3obj.hasId3v1Tag) {
        val tagobj = mp3obj.getId3v1Tag
        val artist = tagobj.getArtist
        val songname = tagobj.getTitle
        (artist == null) || (songname == null) match {
          case true => None
          case false => Some((artist, songname, length))
        }
      } else None
    })

    val music = scala.collection.mutable.Map[String, scala.collection.mutable.Map[String, Int]]()

    for ((artist: String, songname: String, length: Int) <- mp3files) {
      if (!music.contains(artist))
        music(artist) = scala.collection.mutable.Map[String, Int]()
      if (!music(artist).contains(songname))
        music(artist)(songname) = length
    }
    music
  }

  def populateMP3s(root: String) {
    val music = makeMP3Maps(root)

    val jdbcstr = "jdbc:sqlite:" + Environment.appdir + "pianobot.db"
    val conn = DriverManager.getConnection(jdbcstr)

    val artistQuery = conn.prepareStatement("INSERT OR IGNORE INTO songwriters (name) VALUES " +
      music.keys.toArray.map((_: String) => "(?)").mkString(", "))
    for ((artist, index) <- music.keys.zip(Stream from 1))
      artistQuery.setString(index, artist)
    artistQuery.execute()

    """
      |INSERT INTO songs (byID, name, length)
      |SELECT songwriters.id, 'Clair de Lune', 342
      |FROM songwriters
      |WHERE songwriters.name = 'Claude Debussy'
    """.stripMargin

    for (artist <- music.keys) {
      val foo = conn.prepareStatement("SELECT id FROM songwriters WHERE name=?")
      foo.setString(1, artist)
      val artistId = foo.executeQuery().getInt("id")
      val songQuery = conn.prepareStatement("INSERT OR IGNORE INTO songs (byID, name, length) VALUES " +
        music.keys.toArray.map((_: String) => s"($artistId, ?, ?)").mkString(", "))
    }
    conn.close()
  }
}
