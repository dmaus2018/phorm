/*
 * Copyright (C) 2022-2025 Philip Helger
 *
 * All rights reserved.
 */
package com.helger.valsvc.api;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import com.helger.commons.callback.IThrowingRunnable;
import com.helger.commons.datetime.PDTFactory;
import com.helger.commons.timing.StopWatch;
import com.helger.json.IJsonObject;
import com.helger.phive.result.json.PhiveJsonHelper;
import com.helger.phive.result.xml.PhiveXMLHelper;
import com.helger.xml.microdom.IMicroElement;

@Immutable
public final class CommonAPIInvoker
{
  public static final String JSON_SUCCESS = "success";

  private CommonAPIInvoker ()
  {}

  public static void invoke (@Nonnull final IJsonObject aJson, @Nonnull final IThrowingRunnable <Exception> r)
  {
    final ZonedDateTime aQueryDT = PDTFactory.getCurrentZonedDateTimeUTC ();
    final StopWatch aSW = StopWatch.createdStarted ();
    try
    {
      r.run ();
    }
    catch (final Exception ex)
    {
      aJson.add (JSON_SUCCESS, false);
      aJson.addJson ("exception", PhiveJsonHelper.getJsonStackTrace (ex));
    }
    aSW.stop ();

    aJson.add ("invocationDateTime", DateTimeFormatter.ISO_ZONED_DATE_TIME.format (aQueryDT));
    aJson.add ("invocationDurationMillis", aSW.getMillis ());
  }

  public static void invoke (@Nonnull final IMicroElement aXML, @Nonnull final IThrowingRunnable <Exception> r)
  {
    final ZonedDateTime aQueryDT = PDTFactory.getCurrentZonedDateTimeUTC ();
    final StopWatch aSW = StopWatch.createdStarted ();
    try
    {
      r.run ();
    }
    catch (final Exception ex)
    {
      aXML.appendElement (JSON_SUCCESS).appendText (false);
      aXML.appendChild (PhiveXMLHelper.getXMLStackTrace (ex, "exception"));
    }
    aSW.stop ();

    aXML.appendElement ("invocationDateTime").appendText (DateTimeFormatter.ISO_ZONED_DATE_TIME.format (aQueryDT));
    aXML.appendElement ("invocationDurationMillis").appendText (aSW.getMillis ());
  }
}
