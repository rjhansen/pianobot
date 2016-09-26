package engineering.hansen.pianobot

/*
 * Copyright (c) 2016, Rob Hansen &lt;rob@hansen.engineering&gt;.
 *
 * Permission to use, copy, modify, and/or distribute this software
 * for any purpose with or without fee is hereby granted, provided
 * that the above copyright notice and this permission notice
 * appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL
 * WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL
 * THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR
 * CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
 * LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

import java.nio.file.{Files, Paths}
import java.sql.{Connection, DriverManager}

import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters._
import com.mpatric.mp3agic.{ID3v1, ID3v2, Mp3File}
import resource._

import scala.collection.immutable.HashSet

object SQLUtilities {
  private val logger = LogManager.getLogger(getClass)
  private val sem = new java.util.concurrent.Semaphore(1)

  def getConnection = {
    DriverManager.getConnection(s"jdbc:sqlite:${Environment.appdir}pianobot.db")
  }

  private def MakeMP3Maps(root_dir: String) = {
    logger.debug(s"making maps of MP3 files found in $root_dir")

    if (!Files.isDirectory(Paths.get(root_dir))) {
      logger.fatal("directory doesn't exist/isn't readable!")
      System.err.println("There's been a fatal error.  Please check the log.")
      System.exit(1)
    }

    val mp3files = {
      var searchdirs = new scala.collection.mutable.ListBuffer[String]()
      val visiteddirs = scala.collection.mutable.Set[String]()
      val thesemp3s = scala.collection.mutable.ListBuffer[String]()
      searchdirs += root_dir

      while (searchdirs.nonEmpty) {
        logger.debug(s"searching ${searchdirs.head}")
        try {
          val files = for (i <-
                           Files.newDirectoryStream(
                             Paths.get(searchdirs.head)).asScala
                           if Files.isReadable(i)) yield i.toAbsolutePath.toString
          val dirs = for (i <-
                          files if Files.isDirectory(Paths.get(i))) yield i
          thesemp3s ++= (for (i <-
                              files if Files.isRegularFile(Paths.get(i)) &&
            i.endsWith("mp3")) yield i)

          visiteddirs += searchdirs.head
          searchdirs = searchdirs.tail
          searchdirs ++= (for (i <- dirs if !visiteddirs.contains(i)) yield i)
        } catch {
          case _ : Throwable =>
            logger.fatal(s"could not access ${searchdirs.head}")
            System.err.println("A fatal error occurred.  Please check the log.")
        }
      }
      logger.info(thesemp3s.toArray)
      thesemp3s.toList
    }

    val MP3s = for (i <- mp3files.flatMap((fn: String) => {
      try {
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
        }
        else if (mp3obj.hasId3v1Tag) {
          val tagobj = mp3obj.getId3v1Tag
          val artist = tagobj.getArtist
          val songname = tagobj.getTitle
          (artist == null) || (songname == null) match {
            case true => None
            case false => Some((artist, songname, length))
          }
        }
        else None
      }
      catch {
        case _: Throwable =>
          logger.info(s"$fn threw an exception on reading its ID3 tags.  Recovering...")
          None
      }
    })) yield i

    val music = scala.collection.mutable.Map[String, scala.collection.mutable.Map[String, Int]]()

    for ((artist: String, songname: String, length: Int) <- MP3s) {
      if (!music.contains(artist))
        music(artist) = scala.collection.mutable.Map[String, Int]()
      if (!music(artist).contains(songname))
        music(artist)(songname) = length
    }
    music
  }

  def Initialize() = {
    val conn = SQLUtilities.getConnection
    try {
      Files.deleteIfExists(Paths.get(Environment.appdir + "pianobot.db"))
      for (statement <- managed(conn.createStatement())) {
        statement.executeUpdate(
          """
            |CREATE TABLE people
            |  (
            |  id INTEGER PRIMARY KEY,
            |  nick TEXT UNIQUE NOT NULL,
            |  lastSeen INTEGER NOT NULL DEFAULT 0
            |  )
          """.stripMargin
        )
      }
      logger.info("created new table 'people'")

      for (statement <- managed(conn.createStatement())) {
        statement.executeUpdate(
          """
            |CREATE TABLE capabilities
            |  (
            |  id INTEGER PRIMARY KEY,
            |  name TEXT UNIQUE NOT NULL
            |  )
          """.stripMargin
        )
      }
      logger.info("created new table 'capabilities'")

      for (statement <- managed(conn.createStatement())) {
        statement.executeUpdate(
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
      }
      logger.info("created new table 'capabilityMap'")

      for (statement <- managed(conn.createStatement())) {
        statement.executeUpdate(
          """
            |CREATE TABLE messages
            |  (
            |  id INTEGER PRIMARY KEY,
            |  fromID INTEGER NOT NULL,
            |  toID INTEGER NOT NULL,
            |  message TEXT NOT NULL,
            |  timestamp INTEGER NOT NULL DEFAULT 0,
            |  UNIQUE(fromID, toID, message) ON CONFLICT REPLACE,
            |  FOREIGN KEY (fromID) REFERENCES people(id),
            |  FOREIGN KEY (toID) REFERENCES people(id)
            |  )
          """.stripMargin
        )
      }
      logger.info("created new table 'messages'")

      for (statement <- managed(conn.createStatement())) {
        statement.executeUpdate(
          """
            |CREATE TABLE songwriters
            |  (
            |  id INTEGER PRIMARY KEY,
            |  name TEXT NOT NULL UNIQUE
            |  )
          """.stripMargin
        )
      }
      logger.info("created new table 'songwriters'")

      for (statement <- managed(conn.createStatement())) {
        statement.executeUpdate(
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
      }
      logger.info("created new table 'songs'")

      for (statement <- managed(conn.createStatement())) {
        statement.executeUpdate(
          "INSERT INTO people (nick) VALUES ('" +
            Environment.options("admin") + "')")
      }
      logger.info("inserted admin into people")

      for (statement <- managed(conn.createStatement())) {
        statement.executeUpdate(
          "INSERT INTO capabilities (name) VALUES ('admin')"
        )
      }
      logger.info("inserted admin capability")

      for (statement <- managed(conn.createStatement())) {
        statement.executeUpdate(
          "INSERT INTO songwriters (name) VALUES ('Claude Debussy')"
        )
      }
      logger.info("added Claude Debussy")

      for (statement <- managed(conn.createStatement())) {
        statement.executeUpdate(
          """
            |INSERT INTO songs (byID, name, length)
            |SELECT songwriters.id, 'Clair de Lune', 342
            |FROM songwriters
            |WHERE songwriters.name = 'Claude Debussy'
          """.stripMargin
        )
      }
      logger.info("added Clair de Lune")

      for (statement <- managed(conn.createStatement())) {
        statement.executeUpdate(
          """
            |INSERT INTO capabilityMap (peopleID, capabilityID)
            |SELECT people.id, capabilities.id
            |FROM people, capabilities
            |WHERE people.nick = '""".stripMargin +
            Environment.options("admin") +
            "' AND capabilities.name = 'admin'"
        )
      }
      logger.info("gave the admin the admin bit")
      logger.info("reading in mp3s from " +
        Environment.options("repertoire"))

      val music = MakeMP3Maps(Environment.options("repertoire"))
      logger.debug("populating songwriters table")
      logger.debug("INSERT OR IGNORE INTO songwriters(name) VALUES " +
        music.keys.toArray.map((_: String) => "(?)").mkString(", "))
      for (artistQuery <- managed(conn.prepareStatement(
        "INSERT OR IGNORE INTO songwriters(name) VALUES " +
          music.keys.toArray.map((_: String) => "(?)").mkString(", ")))) {
        for ((artist, index) <- music.keys.zip(Stream from 1))
          artistQuery.setString(index, artist)
        artistQuery.execute()
      }

      logger.debug("populating songs table")
      for (artist <- music.keys) {
        logger.debug(s"artist: $artist")
        for (foo <- managed(conn.prepareStatement(
          "SELECT id FROM songwriters WHERE name=?"))) {
          foo.setString(1, artist)
          val artistId = foo.executeQuery().getInt("id")
          val sb = new StringBuilder()
          sb.append("INSERT OR IGNORE INTO songs(byID, name, length) VALUES ")
          sb.append(
            List.fill(
              music(artist).keys.size)
            (s"($artistId, ?, ?)").mkString(", "))
          logger.debug(sb.toString)

          for (statement <- managed(conn.prepareStatement(sb.toString))) {
            var index = 1
            for ((song, length) <- music(artist)) {
              statement.setString(index, song)
              statement.setInt(index + 1, length)
              index += 2
            }
            statement.execute()
          }
        }
      }

      val countstr = "SELECT COUNT(*) FROM songs"
      for (statement <- managed(conn.createStatement())) {
        val songCount = statement.executeQuery(countstr).getInt(1)
        for (statement2 <- managed(conn.createStatement())) {
          val artiststr = "SELECT COUNT(*) FROM songwriters"
          val songwriterCount = statement2.executeQuery(artiststr).getInt(1)
          logger.info(s"repertoire is $songCount songs by " +
            s"$songwriterCount artists")
        }
      }
    } catch {
      case e: Throwable => logger.fatal("Could not initialize db: " +
          e.toString)
        System.exit(1)
    } finally {
      conn.close()
    }
  }

  def getLastSeen(connection: Connection, nick: String) : Option[String] = {
    val qstring = "SELECT lastSeen FROM people WHERE nick = ?"
    var rv: Option[String] = None
    for (q <- managed(connection.prepareStatement(qstring))) {
      q.setString(1, nick)
      val rs = q.executeQuery()
      rs.next() match {
        case false => rv = None
        case true => rv = Some(Utilities.timestampToRFC1123(rs.getInt(1)))
      }
    }
    rv
  }

  def setLastSeen(connection: Connection, nick: String) = {
    val qstring = "INSERT OR REPLACE INTO people (id, nick, lastSeen) " +
      "VALUES ((SELECT id FROM people WHERE nick=?), ?, ?)"
    for (q <- managed(connection.prepareStatement(qstring))) {
      q.setString(1, nick)
      q.setString(2, nick)
      q.setLong(3, System.currentTimeMillis() / 1000)
      q.executeUpdate()
    }
  }

  def isSongKnown(connection: Connection, artist: String, title: String): Boolean = {
    var rv = false
    for (q <- managed(connection.prepareStatement(
      """SELECT songs.id FROM songwriters, songs
        |WHERE songwriters.name = ? AND songs.name = ?
        |AND songs.byID = songwriters.id""".stripMargin))) {
      q.setString(1, artist)
      q.setString(2, title)
      rv = q.executeQuery().next()
    }
    rv
  }

  def getSongLength(connection: Connection, artist: String, title: String) : Option[Int] = {
    var rv : Option[Int] = None
    for (q <- managed(connection.prepareStatement(
      "SELECT songs.length FROM songwriters, songs WHERE songwriters.name = ? " +
        "AND songs.name = ? AND songs.byID = songwriters.id"))) {
      q.setString(1, artist)
      q.setString(2, title)
      val rs = q.executeQuery()
      rs.next() match {
        case false => ;
        case true => rv = Some(rs.getInt(1))
      }
    }
    rv
  }

  def getSongsBy(connection: Connection, band: String) : Set[String] = {
    var rv = Set[String]()
    for (q <- managed(connection.prepareStatement(
      "SELECT songs.name FROM songs, songwriters WHERE songwriters.name = ? " +
      "AND songs.byID = songwriters.id"))) {
      q.setString(1, band)
      val rs = q.executeQuery()
      rs.next() match {
        case false => ;
        case true => rv = new Iterator[String] {
          def hasNext = rs.next()
          def next = rs.getString(1)
        }.toSet
      }
    }
    rv
  }

  def getArtistsWhoHavePerformed(connection: Connection, title: String): Set[String] = {
    var rv = Set[String]()
    for (q <- managed(connection.prepareStatement(
      """SELECT songwriters.name FROM songwriters, songs
        |WHERE songs.name = ? AND 
        |songs.byID = songwriters.id""".stripMargin))) {
      q.setString(1, title)
      val rs = q.executeQuery()
      rv = new Iterator[String] {
          def hasNext = rs.next()
          def next = rs.getString(1)
        }.toSet
    }
    rv
  }

  private def getIdFromNick(connection: Connection, nick: String): Option[Int] = {
    var rv: Option[Int] = None
    for (q <- managed(connection.prepareStatement(
      "SELECT id FROM people WHERE people.nick = ?"))) {
      q.setString(1, nick)
      val rs = q.executeQuery()
      rs.next() match {
        case true => rv = Some(rs.getInt(1))
        case _ => ;
      }
    }
    rv
  }

  def leaveMessageFor(connection: Connection, from: String, to: String, msg: String) : Boolean =
    getIdFromNick(connection, from) match {
      case None => false
      case Some(fromID: Int) => getIdFromNick(connection, to) match {
        case None => false
        case Some(toID: Int) =>
          val ts = System.currentTimeMillis / 1000
          for (q <- managed(connection.prepareStatement(
            s"""INSERT INTO messages(fromID, toID, message, timestamp)
                |VALUES($fromID, $toID, ?, $ts)""".stripMargin))) {
            q.setString(1, msg)
            q.execute()
          }
          true
      }
    }

  def getMessagesFor(connection: Connection, nick: String) : Option[Array[(String, String, Int)]] = {
    var rv: Option[Array[(String, String, Int)]] = None
    getIdFromNick(connection, nick) match {
      case None => rv
      case Some(nickID: Int) =>
        for (q <- managed(connection.createStatement())) {
          val stmt = "SELECT people.nick, messages.message, messages.timestamp " +
            s"FROM people, messages WHERE messages.toID = $nickID " +
            "AND people.id = messages.fromID ORDER BY messages.timestamp"
          val rs = q.executeQuery(stmt)
          val arr = new Iterator[(String, String, Int)] {
            def hasNext = rs.next
            def next = (rs.getString(1), rs.getString(2), rs.getInt(3))
          }.toArray
          arr.length > 0 match {
            case true => rv = Some(arr)
            case false => rv = None
          }
        }
        rv
    }
  }

  def flushOldMessages(connection: Connection) = {
    val now = System.currentTimeMillis / 1000
    val old = now - 864000
    for (s <- managed(connection.createStatement())) {
      s.execute(s"DELETE FROM messages WHERE messages.timestamp < $old")
    }
  }

  def flushMessagesFor(connection: Connection, nick: String) : Unit = {
    getIdFromNick(connection, nick) match {
      case None => ;
      case Some(nickID: Int) =>
        for (q <- managed(connection.createStatement()))
          q.executeUpdate(
            s"DELETE FROM messages WHERE toID = $nickID")
    }
  }

  def getCapabilitiesFor(connection: Connection, nick: String) : Set[String] = {
    getIdFromNick(connection, nick) match {
      case None => Set[String]()
      case Some(nickID: Int) =>
        var rv = Set[String]()
        for (q <- managed(connection.createStatement())) {
          val rs = q.executeQuery(
          s"""SELECT capabilities.name
             |FROM capabilityMap, capabilities
             |WHERE capabilityMap.peopleID = $nickID AND
             |capabilityMap.capabilityID = capabilities.id""".stripMargin)
          rv = new Iterator[String] {
            def hasNext = rs.next
            def next = rs.getString(1)
          }.toSet
        }
        rv
    }
  }
}
