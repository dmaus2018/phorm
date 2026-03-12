/*
 * Copyright (C) 2022-2026 Philip Helger
 *
 * All rights reserved.
 */
package com.helger.phorm;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.debug.GlobalDebug;
import com.helger.base.string.StringParser;
import com.helger.config.ConfigFactory;
import com.helger.config.fallback.ConfigWithFallback;
import com.helger.config.fallback.IConfigWithFallback;

/**
 * This class provides access to the settings as contained in the
 * <code>application.properties</code> file.
 *
 * @author Philip Helger
 */
public final class AppConfig
{
  private static final IConfigWithFallback DEFAULT_INSTANCE = new ConfigWithFallback (ConfigFactory.createDefaultValueProvider ());

  @Deprecated
  private AppConfig ()
  {}

  @NonNull
  public static IConfigWithFallback getConfig ()
  {
    return DEFAULT_INSTANCE;
  }

  @Nullable
  public static String getGlobalDebug ()
  {
    return getConfig ().getAsString ("global.debug");
  }

  @Nullable
  public static String getGlobalProduction ()
  {
    return getConfig ().getAsString ("global.production");
  }

  @Nullable
  public static String getDataPath ()
  {
    return getConfig ().getAsString ("webapp.datapath");
  }

  public static boolean isCheckFileAccess ()
  {
    return getConfig ().getAsBoolean ("webapp.checkfileaccess", false);
  }

  public static boolean isTestVersion ()
  {
    return getConfig ().getAsBoolean ("webapp.testversion", GlobalDebug.isDebugMode ());
  }

  private static boolean _getAsBooleanWithFallback (final String sKey1, final String sKey2, final boolean bDefault)
  {
    final String sVal = getConfig ().getAsStringOrFallback (sKey1, sKey2);
    if (sVal != null)
      return StringParser.parseBool (sVal, bDefault);
    return bDefault;
  }

  public static boolean isStatusAPIEnabled ()
  {
    return _getAsBooleanWithFallback ("phorm.statusapi.enabled", "valsvc.statusapi.enabled", true);
  }

  @Nullable
  public static String getAPIRequiredToken ()
  {
    return getConfig ().getAsStringOrFallback ("phorm.api.requiredtoken", "valsvc.api.requiredtoken");
  }

  public static boolean isUseHttp400OnValidationFailure ()
  {
    return _getAsBooleanWithFallback ("phorm.api.response.onfailure.http400",
                                      "valsvc.api.response.onfailure.http400",
                                      true);
  }

  public static boolean isLogResponsePayload ()
  {
    return _getAsBooleanWithFallback ("phorm.api.response.log.payload", "valsvc.api.response.log.payload", false);
  }
}
