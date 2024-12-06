/*
 * Copyright (C) 2022-2024 Philip Helger
 *
 * All rights reserved.
 */
package com.helger.valsvc.api;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.helger.commons.annotation.Nonempty;
import com.helger.commons.http.CHttp;
import com.helger.commons.string.StringHelper;
import com.helger.ddd.DocumentDetails;
import com.helger.json.IJsonObject;
import com.helger.json.serialize.JsonWriter;
import com.helger.json.serialize.JsonWriterSettings;
import com.helger.photon.api.IAPIDescriptor;
import com.helger.photon.app.PhotonUnifiedResponse;
import com.helger.valsvc.AppConfig;
import com.helger.valsvc.AppVersion;
import com.helger.valsvc.ddd.ValSvcDDD;
import com.helger.web.scope.IRequestWebScopeWithoutResponse;
import com.helger.xml.serialize.read.DOMReader;

/**
 * Determine the document type and return it
 *
 * @author Philip Helger
 */
public final class ApiPostDetermineDocDetails extends AbstractAPIInvoker
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ApiPostDetermineDocDetails.class);
  private static final AtomicInteger COUNTER = new AtomicInteger (0);

  @Override
  public void invokeAPI (@Nonnull final IAPIDescriptor aAPIDescriptor,
                         @Nonnull @Nonempty final String sPath,
                         @Nonnull final Map <String, String> aPathVariables,
                         @Nonnull final IRequestWebScopeWithoutResponse aRequestScope,
                         @Nonnull final PhotonUnifiedResponse aUnifiedResponse) throws IOException
  {
    final String sLogPrefix = "[DETERMINE-" + AppVersion.getVersionNumber () + "-" + COUNTER.incrementAndGet () + "] ";

    // Check request validity
    final String sToken = aRequestScope.headers ().getFirstHeaderValue (HEADER_X_TOKEN);
    if (StringHelper.hasNoText (sToken))
    {
      LOGGER.error (sLogPrefix + "The specific token header is missing");
      aUnifiedResponse.setStatus (CHttp.HTTP_FORBIDDEN);
      return;
    }
    if (!sToken.equals (AppConfig.getAPIRequiredToken ()))
    {
      LOGGER.error (sLogPrefix + "The specified token value does not match the configured required token");
      aUnifiedResponse.setStatus (CHttp.HTTP_FORBIDDEN);
      return;
    }

    // Read the payload as XML
    LOGGER.info (sLogPrefix + "Trying to read payload as XML");
    final Document aDoc = DOMReader.readXMLDOM (aRequestScope.getRequest ().getInputStream ());
    if (aDoc == null || aDoc.getDocumentElement () == null)
    {
      LOGGER.error (sLogPrefix + "Failed to read the message body as XML");
      aUnifiedResponse.createBadRequest ();
      return;
    }

    LOGGER.info (sLogPrefix + "Trying to determine payload type");
    final DocumentDetails aDDO = ValSvcDDD.findDocumentDetails (aDoc.getDocumentElement ());
    if (aDDO == null)
    {
      LOGGER.error (sLogPrefix + "Failed to determine the document types");
      aUnifiedResponse.createNotFound ();
      return;
    }

    final IJsonObject aJson = aDDO.getAsJson ();

    if (AppConfig.isLogResponsePayload ())
    {
      LOGGER.info (sLogPrefix +
                   "Response JSON is:\n" +
                   new JsonWriter (JsonWriterSettings.DEFAULT_SETTINGS_FORMATTED).writeAsString (aJson));
    }

    aUnifiedResponse.json (aJson);
  }
}
