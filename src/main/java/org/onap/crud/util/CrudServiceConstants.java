/**
 * ﻿============LICENSE_START=======================================================
 * Gizmo
 * ================================================================================
 * Copyright © 2017 AT&T Intellectual Property.
 * Copyright © 2017 Amdocs
 * All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 *
 * ECOMP is a trademark and service mark of AT&T Intellectual Property.
 */
package org.onap.crud.util;

public class CrudServiceConstants {
  public static final String CRD_SERVICE_NAME = "Crud-Service";

  public static final String CRD_FILESEP = (System.getProperty("file.separator") == null) ? "/"
      : System.getProperty("file.separator");

  public static final String CRD_SPECIFIC_CONFIG = System.getProperty("CONFIG_HOME") + CRD_FILESEP;
  public static final String CRD_CONFIG_FILE = CRD_SPECIFIC_CONFIG + "crud-api.properties";
  public static final String CRD_HOME_MODEL = CRD_SPECIFIC_CONFIG + "model" + CRD_FILESEP;
  public static final String CRD_HOME_AUTH = CRD_SPECIFIC_CONFIG + "auth" + CRD_FILESEP;
  public static final String CRD_AUTH_FILE = CRD_HOME_AUTH + "crud_policy.json";
  public static final String CRD_CHAMP_AUTH_FILE = CRD_HOME_AUTH + "champ-cert.p12";
  public static final String CRD_AUTH_POLICY_NAME = "crud";
  public static final String CRD_ASYNC_REQUEST_TIMEOUT = "crud.async.request.timeout";
  public static final String CRD_ASYNC_RESPONSE_PROCESS_POLL_INTERVAL  = "crud.async.response.process.poll.interval";  
}
