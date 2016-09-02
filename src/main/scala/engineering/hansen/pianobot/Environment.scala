package engineering.hansen.pianobot

import java.nio.file.{Paths, Files}
import java.io.{InputStream, PrintWriter}
import java.sql.{Connection, DriverManager, ResultSet, SQLException, Statement}
import org.apache.logging.log4j.{Logger, LogManager}
import scala.io._

object Environment {
  private def printerr(x: String) = System.err.println(x)

  val homedir =
    sys.props.get("user.home") match {
      case None =>
        printerr("user.home is not defined.  Aborting.")
        System.exit(-1)
        ""
      case Some(x) =>
        Files.exists(Paths.get(x)) match {
          case false =>
            printerr("user.home does not exist. Aborting.")
            System.exit(-1)
            ""
          case true => Files.isDirectory(Paths.get(x)) match {
            case false =>
              printerr("user.home is not a directory.  Aborting.")
              System.exit(-1)
              ""
            case true => x + java.io.File.separator
          }
        }
    }

  val appdir = {
    val appdir_ = homedir + ".pianobot" + java.io.File.separator
    val appdirPath = Paths.get(appdir_)
    Files.exists(appdirPath) match {
      case false =>
        try {
          Files.createDirectory(appdirPath)
        }
        catch {
          case _: Throwable =>
            printerr("Could not create " + appdir_ + ".  Aborting.")
            System.exit(-1)
            ""
        }
      case true => Files.isDirectory(appdirPath) match {
        case true => appdir_
        case false =>
          printerr("Your ~/.pianobot is a file, not a directory.  Aborting.")
          System.exit(-1)
          ""
      }
    }
  }

  val confFile = {
    val confFile_ = appdir + "pianobot.conf"
    val confFilePath = Paths.get(confFile_)
    Files.exists(confFilePath) match {
      case false =>
        var istream : InputStream = null
        try {
          istream = getClass.getResourceAsStream("/pianobotconf.sample")
          Files.copy(istream, confFilePath)
        }
        catch {
          case _ : Throwable =>
            printerr("Error copying default pianobot configuration file.")
            printerr("Aborting.")
            System.exit(-1)
        }
        finally {
          istream.close()
        }
      case true => ;
    }
    confFile_
  }

  val log4jFile = {
    val log4jfile = appdir + "log4j2.xml"
    val log4jfilePath = Paths.get(log4jfile)

    Files.exists(log4jfilePath) match {
      case false =>
        var istream : InputStream = null
        var pw : PrintWriter = null
        try {
          istream = getClass.getResourceAsStream("/log4jconf.sample")
          pw = new PrintWriter(log4jfile)
          for (i <- Source.fromInputStream(istream).getLines())
            pw.println(i.replace("LOG4JCONF_FILE", appdir + "pianobot.log"))
        }
        catch {
          case _ : Throwable =>
            printerr("Error copying default log4j configuration file.")
            printerr("Aborting.")
            System.exit(-1)
        }
        finally {
          if (istream != null) istream.close()
          if (pw != null) pw.close()
        }

      case true => ;
    }

    sys.props.get("log4j.configurationFile") match {
      case None => sys.props("log4j.configurationFile") = log4jfile
      case Some(x) => ;
    }
    log4jfile
  }

  val logger = LogManager.getLogger(getClass)

  val musicDB = {
    Class.forName("org.sqlite.JDBC")

    val dbpath = Paths.get(appdir + "pianobot.db")
    Files.exists(dbpath) match {
      case false =>
        try {
          val jdbcstr = "jdbc:sqlite:" + appdir + "pianobot.db"
          val c = DriverManager.getConnection(jdbcstr)
          c.close
        }
        catch {
          case _ : Throwable =>
            logger.fatal("Could not initialize an empty database.")
            printerr("Could not initialize database.  Aborting.")
            System.exit(-1)
        }
        finally {
          // only way it gets here is if getConnection fails, in
          // which case c is undefined, hence doesn't need to be
          // closed
        }
      case true =>
        Files.isDirectory(dbpath) match {
          case true =>
            logger.fatal("pianobot.db is a directory, not a file")
            printerr("pianobot.db is a directory, not a file. Aborting.")
            System.exit(-1)
          case false => ;
        }
    }
    dbpath
  }

  logger.info("Successfully finished startup sequence.")

  def HW() = { System.err.println(homedir) }
}
