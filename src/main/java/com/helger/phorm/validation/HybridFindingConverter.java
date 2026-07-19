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
package com.helger.phorm.validation;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.Immutable;
import com.helger.diagnostics.error.SingleError;
import com.helger.diagnostics.error.level.EErrorLevel;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.io.resource.ClassPathResource;
import com.helger.kaltblut.core.validate.EHybridValidationLayerKind;
import com.helger.kaltblut.core.validate.HybridFinding;
import com.helger.kaltblut.core.validate.HybridValidationLayer;
import com.helger.phive.api.artefact.IValidationArtefact;
import com.helger.phive.api.artefact.ValidationArtefact;
import com.helger.phive.api.result.ValidationResult;
import com.helger.phive.api.validity.EExtendedValidity;

/**
 * Bridge between a kaltblut {@link HybridValidationLayer} and the phive validation model. Each
 * layer maps to one phive {@link ValidationResult} carrying its own findings and duration.
 *
 * @author Philip Helger
 */
@Immutable
public final class HybridFindingConverter
{
  public static final IValidationArtefact ARTEFACT_BR_HYBRID = new ValidationArtefact (EPhormValidationType.PDF_HYBRID_CARRIER,
                                                                                       new ClassPathResource ("kaltblut/HybridValidator/BR-HYBRID"));
  public static final IValidationArtefact ARTEFACT_PDF_A3 = new ValidationArtefact (EPhormValidationType.PDF_HYBRID_CARRIER,
                                                                                    new ClassPathResource ("kaltblut/HybridValidator/PDF-A3"));

  private HybridFindingConverter ()
  {}

  @NonNull
  public static SingleError toError (@NonNull final HybridFinding aFinding)
  {
    return SingleError.builder ()
                      .errorLevel (aFinding.getSeverity ().getErrorLevel ())
                      .errorID (aFinding.getRuleID ())
                      .errorFieldName (aFinding.getLocation ())
                      .errorText (aFinding.getMessage ())
                      .build ();
  }

  @NonNull
  public static IValidationArtefact getValidationArtefact (@NonNull final EHybridValidationLayerKind eKind)
  {
    return switch (eKind)
    {
      case BR_HYBRID -> ARTEFACT_BR_HYBRID;
      case PDF_A3 -> ARTEFACT_PDF_A3;
      default -> throw new IllegalStateException ("Unsupported validation layer kind: " + eKind);
    };
  }

  /**
   * Convert a single kaltblut validation layer into a phive validation result.
   *
   * @param aLayer
   *        The kaltblut layer. May not be <code>null</code>.
   * @return The phive validation result. Never <code>null</code>.
   */
  @NonNull
  public static ValidationResult toValidationResult (@NonNull final HybridValidationLayer aLayer)
  {
    final ErrorList aErrorList = new ErrorList ();
    for (final HybridFinding aFinding : aLayer.getAllFindings ())
      if (aFinding.getSeverity ().getErrorLevel ().isGE (EErrorLevel.WARN))
        aErrorList.add (toError (aFinding));

    final EExtendedValidity eValidity = aLayer.isValid () ? EExtendedValidity.VALID : EExtendedValidity.INVALID;
    return new ValidationResult (getValidationArtefact (aLayer.getKind ()),
                                 aErrorList,
                                 eValidity,
                                 aLayer.getDuration ());
  }
}
