package engineering.hansen.pianobot

import java.time._
import java.time.format.DateTimeFormatter

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

object Utilities {
  val csprng = new java.security.SecureRandom()
  val prng = new scala.util.Random(csprng.nextLong)

  def timestampToRFC1123(timestamp: Long) = {
    ZonedDateTime.ofLocal(
      LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault()),
      ZoneId.systemDefault(),
      ZoneOffset.UTC)
      .format(DateTimeFormatter.RFC_1123_DATE_TIME)
  }
}
