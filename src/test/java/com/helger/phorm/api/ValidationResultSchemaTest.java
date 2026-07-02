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

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.diagnostics.error.list.IErrorList;
import com.helger.io.file.FileSystemIterator;
import com.helger.io.file.IFileFilter;
import com.helger.io.resource.ClassPathResource;
import com.helger.io.resource.FileSystemResource;
import com.helger.xml.schema.XMLSchemaValidationHelper;

/**
 * Test class that ensures the XML responses of the validation APIs match the published XML Schema
 * {@code schemas/phorm-validation-result.xsd}.
 *
 * @author Philip Helger
 */
public final class ValidationResultSchemaTest
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ValidationResultSchemaTest.class);
  private static final ClassPathResource SCHEMA = new ClassPathResource ("schemas/phorm-validation-result.xsd");

  @Test
  public void testExampleResponses ()
  {
    // Test real results
    final File fBaseDir = new File ("src/test/resources/example-responses/");
    assertTrue (fBaseDir.isDirectory ());

    for (final File f : new FileSystemIterator (fBaseDir).withFilter (IFileFilter.filenameEndsWith (".xml")
                                                                                 .and (IFileFilter.fileOnly ())))
    {
      LOGGER.info (f.getName ());
      final IErrorList aEL = XMLSchemaValidationHelper.validate (SCHEMA, new FileSystemResource (f));
      assertTrue ("Schema validation failed[" + f.getName () + "]: " + aEL.toString (), aEL.containsNoError ());
    }
  }
}
