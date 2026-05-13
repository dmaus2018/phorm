/*
 * Copyright (C) 2022-2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phorm.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.helger.annotation.Nonempty;
import com.helger.base.io.stream.StreamHelper;
import com.helger.base.string.StringHelper;
import com.helger.base.wrapper.Wrapper;
import com.helger.ddd.DocumentDetails;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.http.CHttp;
import com.helger.http.header.specific.AcceptMimeTypeList;
import com.helger.json.IJsonObject;
import com.helger.json.JsonObject;
import com.helger.json.serialize.JsonWriter;
import com.helger.json.serialize.JsonWriterSettings;
import com.helger.kaltblut.core.extract.HybridExtractor;
import com.helger.kaltblut.core.model.EZugferdCountry;
import com.helger.kaltblut.core.source.HybridLimits;
import com.helger.kaltblut.core.source.HybridSource;
import com.helger.kaltblut.core.source.IHybridSource;
import com.helger.kaltblut.core.validate.HybridValidationLayer;
import com.helger.kaltblut.core.validate.HybridValidationResult;
import com.helger.kaltblut.core.validate.HybridValidator;
import com.helger.mime.CMimeType;
import com.helger.phive.api.executorset.IValidationExecutorSet;
import com.helger.phive.api.result.ValidationResult;
import com.helger.phive.api.result.ValidationResultList;
import com.helger.phive.api.source.ValidationSourceBinary;
import com.helger.phive.result.html.PhiveHtmlHelper;
import com.helger.phive.result.json.JsonValidationResultListHelper;
import com.helger.phive.result.xml.XMLValidationResultListHelper;
import com.helger.phive.xml.source.IValidationSourceXML;
import com.helger.phive.xml.source.ValidationSourceXML;
import com.helger.phorm.AppConfig;
import com.helger.phorm.AppVersion;
import com.helger.phorm.CApp;
import com.helger.phorm.ddd.PhormDDD;
import com.helger.phorm.validation.AppValidator;
import com.helger.phorm.validation.HybridFindingConverter;
import com.helger.photon.api.IAPIDescriptor;
import com.helger.photon.app.PhotonUnifiedResponse;
import com.helger.schematron.svrl.SVRLResourceError;
import com.helger.servlet.request.RequestHelper;
import com.helger.web.scope.IRequestWebScopeWithoutResponse;
import com.helger.xml.microdom.IMicroDocument;
import com.helger.xml.microdom.IMicroElement;
import com.helger.xml.microdom.MicroDocument;
import com.helger.xml.microdom.serialize.MicroWriter;
import com.helger.xml.serialize.read.DOMReader;
import com.helger.xml.serialize.write.EXMLSerializeIndent;
import com.helger.xml.serialize.write.XMLWriterSettings;

/**
 * Validate a hybrid invoice (ZUGFeRD / Factur-X PDF + embedded XML) by combining the kaltblut
 * carrier-side validation with the dd_and_validate XML business-rule validation. Both layers are
 * returned in a single phive {@link ValidationResultList}, with the PDF carrier layer first.
 *
 * @author Philip Helger
 */
public class ApiPostHybridValidate extends AbstractAPIInvoker
{
  public static final String QUERY_PARAM_COUNTRY = "country";

  private static final Logger LOGGER = LoggerFactory.getLogger (ApiPostHybridValidate.class);
  private static final AtomicInteger COUNTER = new AtomicInteger (0);

  @Nullable
  private static EZugferdCountry _parseCountry (@Nullable final String sCountry,
                                                @NonNull @Nonempty final String sLogPrefix)
  {
    if (StringHelper.isEmpty (sCountry))
      return null;

    final EZugferdCountry eCountry = EZugferdCountry.getFromIDOrNull (sCountry.toUpperCase (Locale.ROOT));
    if (eCountry == null)
      LOGGER.warn (sLogPrefix +
                   "Unknown country code '" +
                   sCountry +
                   "' - falling back to default. Valid values are: DE, FR, OTHER");
    return eCountry;
  }

  @Override
  public void invokeAPI (@NonNull final IAPIDescriptor aAPIDescriptor,
                         @NonNull @Nonempty final String sPath,
                         @NonNull final Map <String, String> aPathVariables,
                         @NonNull final IRequestWebScopeWithoutResponse aRequestScope,
                         @NonNull final PhotonUnifiedResponse aUnifiedResponse) throws IOException
  {
    aUnifiedResponse.disableCaching ();
    final String sLogPrefix = "[HYBRID-VALIDATE-" +
                              AppVersion.getVersionNumber () +
                              "-" +
                              COUNTER.incrementAndGet () +
                              "] ";

    // Security check
    if (LOGGER.isDebugEnabled ())
      LOGGER.debug (sLogPrefix + "Verifying specific HTTP header with token");

    final String sToken = aRequestScope.headers ().getFirstHeaderValue (HEADER_X_TOKEN);
    if (StringHelper.isEmpty (sToken))
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

    // Read the payload as PDF bytes
    LOGGER.info (sLogPrefix + "Reading payload as PDF");
    final byte [] aPayloadBytes = StreamHelper.getAllBytes (aRequestScope.getRequest ().getInputStream ());
    if (aPayloadBytes == null || aPayloadBytes.length == 0)
    {
      final String sErrorMsg = "The request body is empty";
      LOGGER.error (sLogPrefix + sErrorMsg);
      aUnifiedResponse.text (sErrorMsg).setStatus (CHttp.HTTP_BAD_REQUEST);
      return;
    }

    // Optional country parameter
    final EZugferdCountry eCountry = _parseCountry (aRequestScope.params ().getAsString (QUERY_PARAM_COUNTRY),
                                                    sLogPrefix);

    final IHybridSource aHybridSource = HybridSource.fromBytes (aPayloadBytes);

    final Locale aDisplayLocale = CApp.DEFAULT_LOCALE;
    final Wrapper <ValidationResultList> aWrappedVRL = Wrapper.empty ();
    final Wrapper <IValidationExecutorSet <IValidationSourceXML>> aWrappedVES = Wrapper.empty ();
    final Wrapper <DocumentDetails> aWrappedDD = Wrapper.empty ();

    final Runnable aRunnable = () -> {
      final HybridLimits aHybridLimits = HybridLimits.DEFAULTS;

      // 1) Run hybrid PDF carrier validation
      LOGGER.info (sLogPrefix +
                   "Running hybrid PDF carrier validation" +
                   (eCountry != null ? " (country=" + eCountry + ")" : "") +
                   " with limits: " +
                   aHybridLimits);

      final HybridValidator aHybridValidator = new HybridValidator ();
      aHybridValidator.getSettings ().setLimits (aHybridLimits);
      if (eCountry != null)
        aHybridValidator.getSettings ().setCountry (eCountry);

      // Perform PDF validation
      final HybridValidationResult aHybridResult;
      try
      {
        aHybridResult = aHybridValidator.validate (aHybridSource);
      }
      catch (final IOException ex)
      {
        // Fatal: cannot proceed without parsing the PDF
        LOGGER.error (sLogPrefix + "Failed to parse PDF for hybrid validation", ex);
        throw new IllegalStateException ("Failed to parse PDF for hybrid validation: " + ex.getMessage (), ex);
      }

      // Convert to our VRL
      final ValidationResultList aVRL = new ValidationResultList (ValidationSourceBinary.create (null, aPayloadBytes));
      for (final HybridValidationLayer aLayer : aHybridResult.getAllLayers ())
        aVRL.add (HybridFindingConverter.toValidationResult (aLayer));

      // 2) Try to extract and validate the embedded XML
      byte [] aXmlBytes = null;
      try
      {
        aXmlBytes = HybridExtractor.extractInvoiceXml (aHybridSource);
      }
      catch (final IOException ex)
      {
        LOGGER.warn (sLogPrefix + "Could not extract invoice XML: " + ex.getMessage ());
      }

      if (aXmlBytes == null || aXmlBytes.length == 0)
      {
        LOGGER.info (sLogPrefix + "No embedded invoice XML was extracted - returning PDF carrier results only");
      }
      else
      {
        LOGGER.info (sLogPrefix + "Successfully extracted " + aXmlBytes.length + " XML bytes from PDF");

        final Document aDoc = DOMReader.readXMLDOM (aXmlBytes);
        if (aDoc == null || aDoc.getDocumentElement () == null)
        {
          LOGGER.warn (sLogPrefix + "The embedded XML could not be parsed - skipping XML validation");
        }
        else
        {
          // Run DDD
          final Wrapper <Element> aInnerElement = Wrapper.empty ();
          final DocumentDetails aDD = PhormDDD.findDocumentDetails (aDoc.getDocumentElement (), aInnerElement::set);
          if (aDD == null || !aDD.hasVESID ())
          {
            LOGGER.warn (sLogPrefix + "Failed to determine document details for the embedded XML");
          }
          else
          {
            aWrappedDD.set (aDD);

            final DVRCoordinate aVESID = DVRCoordinate.parseOrNull (aDD.getVESID ());
            final IValidationExecutorSet <IValidationSourceXML> aVES = aVESID == null ? null : AppValidator
                                                                                                           .getVESOrNull (aVESID);
            if (aVES == null)
            {
              LOGGER.warn (sLogPrefix + "VESID '" + aDD.getVESID () + "' could not be resolved");
            }
            else
            {
              aWrappedVES.set (aVES);

              final IValidationSourceXML aValSrc = aInnerElement.isSet () ? ValidationSourceXML.createPartial (null,
                                                                                                               aInnerElement.get ())
                                                                          : ValidationSourceXML.create (null, aDoc);

              LOGGER.info (sLogPrefix + "Performing XML validation using VESID '" + aVESID.getAsSingleID () + "'");

              // Run XML validation
              final ValidationResultList aXmlVRL = AppValidator.validate (aVES, aValSrc, aDisplayLocale);
              for (final ValidationResult aXmlLayer : aXmlVRL)
                aVRL.add (aXmlLayer);
            }
          }
        }
      }

      aWrappedVRL.set (aVRL);

      // Aggregate duration: kaltblut layer + any XML layers
      long nTotalMs = 0;
      for (final ValidationResult aVR : aVRL)
        nTotalMs += aVR.getDurationMS ();
      aVRL.setValidationDuration (Duration.ofMillis (nTotalMs));

      if (aVRL.getOverallValidity ().isValid ())
      {
        LOGGER.info (sLogPrefix +
                     "Hybrid validation completed and the document is considered valid (" +
                     aVRL.getValidationDuration () +
                     ")");
      }
      else
      {
        LOGGER.error (sLogPrefix +
                      "Hybrid validation completed and the document is considered invalid (" +
                      aVRL.getValidationDuration () +
                      ")");

        if (AppConfig.isUseHttp400OnValidationFailure ())
        {
          aUnifiedResponse.setStatus (CHttp.HTTP_BAD_REQUEST);
        }
      }
    };

    // Don't emit validation source content for PDFs (would be binary)
    final boolean bEmitValidationSourceContent = false;

    final AcceptMimeTypeList aAcceptMimeTypes = RequestHelper.getAcceptMimeTypes (aRequestScope.getRequest ());
    if (aAcceptMimeTypes.explicitlySupportsMimeType (CMimeType.APPLICATION_XML))
    {
      // Provide response as XML
      final IMicroDocument aResultXML = new MicroDocument ();
      final IMicroElement aResultXMLRoot = aResultXML.addElement ("validationResults");
      if (eCountry != null)
        aResultXMLRoot.addElement ("country").addText (eCountry.name ());

      CommonAPIInvoker.invoke (aResultXMLRoot, aRunnable::run);

      if (aWrappedDD.isSet ())
        aWrappedDD.get ().appendToMicroElement (aResultXMLRoot);

      // Perform conversion
      new XMLValidationResultListHelper ().ves (aWrappedVES.get ())
                                          .sourceToXMLDefault (bEmitValidationSourceContent)
                                          .applyTo (aResultXMLRoot, aWrappedVRL.get (), aDisplayLocale);

      if (AppConfig.isLogResponsePayload ())
      {
        LOGGER.info (sLogPrefix +
                     "Response XML is:\n" +
                     MicroWriter.getNodeAsString (aResultXML,
                                                  new XMLWriterSettings ().setIndent (EXMLSerializeIndent.INDENT_AND_ALIGN)));
      }
      aUnifiedResponse.xml (aResultXML);
    }
    else
      if (aAcceptMimeTypes.explicitlySupportsMimeType (CMimeType.TEXT_HTML))
      {
        // Provide response as HTML
        aRunnable.run ();

        // Perform conversion
        final String sResultHtml = new PhiveHtmlHelper (aDisplayLocale).useDefaultCSS ()
                                                                       .ves (aWrappedVES.get ())
                                                                       .errorTestExtractor ( (error,
                                                                                              locale) -> error instanceof final SVRLResourceError aSvrlError ? aSvrlError.getTest ()
                                                                                                                                                             : null)
                                                                       .sourceData (null)
                                                                       .createHtml (aWrappedVRL.get (),
                                                                                    new XMLWriterSettings ().setIndent (EXMLSerializeIndent.INDENT_AND_ALIGN));

        if (AppConfig.isLogResponsePayload ())
        {
          LOGGER.info (sLogPrefix + "Response HTML is:\n" + sResultHtml);
        }
        aUnifiedResponse.setContentAndCharset (sResultHtml, StandardCharsets.UTF_8).setMimeType (CMimeType.TEXT_HTML);
      }
      else
      {
        // Provide response as JSON
        final IJsonObject aResultJson = new JsonObject ();
        if (eCountry != null)
          aResultJson.add ("country", eCountry.name ());

        CommonAPIInvoker.invoke (aResultJson, aRunnable::run);

        if (aWrappedDD.isSet ())
          aResultJson.add ("documentDetails", aWrappedDD.get ().getAsJson ());

        // Perform conversion
        new JsonValidationResultListHelper ().ves (aWrappedVES.get ())
                                             .sourceToJsonDefault (bEmitValidationSourceContent)
                                             .applyTo (aResultJson, aWrappedVRL.get (), aDisplayLocale);

        if (AppConfig.isLogResponsePayload ())
        {
          LOGGER.info (sLogPrefix +
                       "Response JSON is:\n" +
                       new JsonWriter (JsonWriterSettings.DEFAULT_SETTINGS_FORMATTED).writeAsString (aResultJson));
        }
        aUnifiedResponse.json (aResultJson);
      }
  }
}
