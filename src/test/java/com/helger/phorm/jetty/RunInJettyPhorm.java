/*
 * Copyright (C) 2022-2026 Philip Helger
 *
 * All rights reserved.
 */
package com.helger.phorm.jetty;

import com.helger.annotation.concurrent.Immutable;
import com.helger.photon.jetty.JettyStarter;

/**
 * Run as a standalone web application in Jetty on port 8080.<br>
 * http://localhost:8080/
 *
 * @author Philip Helger
 */
@Immutable
public final class RunInJettyPhorm
{
  public static void main (final String [] args) throws Exception
  {
    new JettyStarter (RunInJettyPhorm.class).run ();
  }
}
