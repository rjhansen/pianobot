package engineering.hansen.pianobot

import java.nio.file.{Files, Paths}
import java.sql.{DriverManager, PreparedStatement}
import org.apache.logging.log4j.LogManager
import scala.collection.JavaConverters._
import com.mpatric.mp3agic.{ID3v1, ID3v2, Mp3File}

object SQLUtilities {
  private val logger = LogManager.getLogger(getClass)
  private val jdbcstr = s"jdbc:sqlite:${Environment.appdir}pianobot.db"
  private var connection = DriverManager.getConnection(jdbcstr)

  def initializeDB() : Unit = {
    try {
      connection.close()
      Files.deleteIfExists(Paths.get(Environment.appdir + "pianobot.db"))
      connection = DriverManager.getConnection(jdbcstr)
      connection.createStatement().executeUpdate(
        """
          |CREATE TABLE people
          |  (
          |  id INTEGER PRIMARY KEY,
          |  nick TEXT UNIQUE NOT NULL,
          |  lastSeen INTEGER NOT NULL DEFAULT 0
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
          |  when INTEGER NOT NULL DEFAULT 0,
          |  UNIQUE(fromID, toID, message) ON CONFLICT REPLACE,
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
        "INSERT INTO people (nick) VALUES ('" +
          Environment.options("admin") + "')")
      logger.info("inserted admin into people")

      connection.createStatement().executeUpdate(
        "INSERT INTO capabilities (name) VALUES ('admin')"
      )
      logger.info("inserted admin capability")
      connection.createStatement().executeUpdate(
        "INSERT INTO songwriters (name) VALUES ('Claude Debussy')"
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
          |WHERE people.nick = '""".stripMargin +
          Environment.options("admin") +
          "' AND capabilities.name = 'admin'"
      )
      logger.info("gave the admin the admin bit")
      logger.info("reading in mp3s from " +
        Environment.options("repertoire"))
      populateMP3s(Environment.options("repertoire"))
      val countstr = "SELECT COUNT(*) FROM songs"
      val songCount = connection.createStatement().executeQuery(countstr).getInt(1)
      val artiststr = "SELECT COUNT(*) FROM songwriters"
      val songwriterCount = connection.createStatement().executeQuery(artiststr).getInt(1)
      logger.info(s"repertoire is $songCount songs by " +
        s"$songwriterCount artists")
    } catch {
      case e: Throwable => logger.fatal("Could not initialize db: " +
          e.toString)
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
    val artistQuery = connection.prepareStatement(
      "INSERT OR IGNORE INTO songwriters (name) VALUES " +
      music.keys.toArray.map((_: String) => "(?)").mkString(", "))
    for ((artist, index) <- music.keys.zip(Stream from 1))
      artistQuery.setString(index, artist)
    artistQuery.execute()

    for (artist <- music.keys) {
      val foo = connection.prepareStatement(
        "SELECT id FROM songwriters WHERE name=?")
      foo.setString(1, artist)
      val artistId = foo.executeQuery().getInt("id")
      val sb = new StringBuilder()
      sb.append("INSERT OR IGNORE INTO songs (byID, name, length) VALUES ")
      sb.append(
        List.fill(
          music(artist).keys.size)
          (s"($artistId, ?, ?)").mkString(", "))

      val statement = connection.prepareStatement(sb.toString)
      var index = 1
      for ((song, length) <- music(artist)) {
        statement.setString(index, song)
        statement.setInt(index + 1, length)
        index += 2
      }
      statement.execute()
    }
  }

  def onPersonEnter(nick: String) = {
  }
}
