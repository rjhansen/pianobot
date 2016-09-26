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

import java.nio.file.{Paths, Files}
import java.io.{InputStream, PrintWriter, File}
import java.sql.{Connection, DriverManager, ResultSet, SQLException, Statement}
import org.apache.logging.log4j.{Logger, LogManager}
import scala.io._

object Environment {
  private def printerr(x: String) = System.err.println(x)

  var logger : Logger = _

  val homedir = sys.props.get("user.home") match {
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

  val appdir = Files.exists(Paths.get(homedir + ".pianobot" + File.separator)) match {
      case false =>
        try {
          Files.createDirectory(Paths.get(homedir + ".pianobot" + File.separator))
          homedir + ".pianobot" + File.separator
        }
        catch {
          case _: Throwable =>
            printerr("Could not create " + homedir + ".pianobot" + File.separator + ".  Aborting.")
            System.exit(-1)
            ""
        }
      case true => Files.isDirectory(Paths.get(homedir + ".pianobot" + File.separator)) match {
        case true => homedir + ".pianobot" + File.separator
        case false =>
          printerr("Your ~/.pianobot is a file, not a directory.  Aborting.")
          System.exit(-1)
          ""
      }
    }

  val log4jFile = {
    var log4jfile = appdir + "log4j2.xml"
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
      case Some(x) => log4jfile = x
    }
    logger = LogManager.getLogger(getClass)
    logger.info("started Pianobot.scala")
    log4jfile
  }

  val confFile = Files.exists(Paths.get(appdir + "pianobot.conf")) match {
    case false => new GetConfig().runme()
      appdir + "pianobot.conf"
    case true => appdir + "pianobot.conf";
  }

  val options = {
    val regex = "^(.*)=(.*)$".r
    val validOptions = scala.collection.immutable.Set("admin", "bot", "password", "irc server", "irc channel", "repertoire")
    (for (regex(key, value) <- scala.io.Source.fromFile(confFile).getLines()
          if validOptions.contains(key.trim.toLowerCase)
    ) yield (key.trim.toLowerCase(), value.trim)).toMap
  }

  val musicDB = Files.exists(Paths.get(appdir + "pianobot.db")) match {
      case false => SQLUtilities.Initialize
        appdir + "pianobot.db"
      case true =>
        Files.isDirectory(Paths.get(appdir + "pianobot.db")) match {
          case true =>
            printerr("pianobot.db is a directory, not a file. Aborting.")
            System.exit(-1)
            appdir + "pianobot.db"
          case false => appdir + "pianobot.db"
        }
    }

  def initialize() = {
    (homedir, appdir, log4jFile, confFile, options, musicDB)
  }
}
